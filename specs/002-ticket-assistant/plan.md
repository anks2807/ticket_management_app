# Implementation Plan: Ticket Assistant (Grounded Q&A)

**Branch**: `002-ticket-assistant` | **Date**: 2026-11-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-ticket-assistant/spec.md` plus user `/speckit-plan` technical constraints.

## Summary

Extends the existing **001** Spring Boot service (`backend/`, package root `com.ttn.tickets`) with a read-only **assistant** slice in `com.ttn.tickets.assistant`: consume `ticket_refresh_intent` rows to keep PGVector chunks current, and answer one natural-language question per request with grounded citations.

Approach:

- **Models**: AWS Bedrock via Spring AI **2.0.2** — Converse chat (Claude, model id from config) + Titan Text Embeddings V2 (dimension from config, default 1024).
- **Storage**: PGVector in the same PostgreSQL; Flyway `V4+` creates `vector` extension, `assistant_ticket_chunk` table, HNSW index; `spring.ai.vectorstore.pgvector.initialize-schema=false`.
- **Refresh**: `@Scheduled` processor claims intents with `FOR UPDATE SKIP LOCKED`, collapses per ticket, reads current ticket+comments, chunks; in one processor `@Transactional`: delete chunks, `PgVectorStore.add()` (embeds inside the store), mark intents `DONE` / retry / `FAILED` — **not** the 001 ticket-change transaction (Principle V).
- **Ask**: validate → similarity search (top-K, threshold) → FR-016 metadata fetch + merge (threshold-exempt) → dedupe by ticketId → if empty `NO_RELEVANT_TICKETS` without chat → **one** `ChatModel.call` → citation ⊆ retrieved → `GROUNDED` or `CITATION_VERIFICATION_FAILED`.
- **Tests**: `./mvnw verify` without AWS credentials (fake embedding + stub chat); optional `@Tag("llm")` Bedrock evaluation.

## Technical Context

**Language/Version**: Java 21 (`maven.compiler.release=21`)

**Pinned versions** (do not drift; plan amendment required to change):

| Component | Version | Notes |
|-----------|---------|-------|
| Java | 21 | |
| Spring Boot | 4.1.2 | Parent BOM |
| Spring AI | 2.0.2 | Import `spring-ai-bom` in `backend/pom.xml` |
| Maven | 3.9.12 | Wrapper |
| PostgreSQL image | `pgvector/pgvector:0.8.7-pg17` | Same as 001 |

**Primary Dependencies** (new for 002; versions from Spring AI BOM 2.0.2):

- `spring-ai-starter-model-bedrock-converse` — chat
- `spring-ai-starter-model-bedrock` — Titan embeddings
- `spring-ai-starter-vector-store-pgvector` — `PgVectorStore`

**Storage**: PostgreSQL 17 + pgvector; 001 tables plus `assistant_ticket_chunk` and extended `ticket_refresh_intent` ([data-model.md](./data-model.md)).

**Testing**: JUnit 6, AssertJ, MockMvc, Testcontainers PostgreSQL. Default `verify` excludes `@Tag("llm")`. Stub/fake AI beans in test configuration.

**Target Platform**: JVM service on port 8080.

**Constraints**: Single chat call; no citation regeneration; no chat when retrieval empty; embedding never inside 001 ticket-save transactions (may run inside the refresh processor transaction via `PgVectorStore.add()`); AWS via default credential chain.

## Constitution Check

| # | Principle | Post-design | Notes |
|---|-----------|-------------|-------|
| I | Domain Integrity | PASS | Read-only assistant |
| II | Validation at the Boundary | PASS | `INVALID_QUESTION` vs outcomes |
| III | Test-First | PASS | Postgres + pgvector ITs |
| IV | Grounded AI Only | PASS | Threshold bypass, citation check |
| V | Knowledge Freshness | PASS | Consumer + rebuild |
| VI | Configuration | PASS | `assistant.*` properties |
| VII | Bounded AI Scope | PASS | One chat call |
| VIII | Security | PASS | No keys in repo |
| IX | Simplicity | PASS | Scheduled JDBC + Spring AI store |
| X | AI Accountability | PASS | Human review |

## Decisions

| # | Decision | Ref |
|---|----------|-----|
| D1 | Package `com.ttn.tickets.assistant` in existing module | User |
| D2 | Embedding dimension default 1024 | R-01 |
| D3 | Chunking per rag-vector-store rule | R-02 |
| D4 | Bedrock prompt caching via `BedrockCacheOptions` when enabled | R-03 |
| D5 | Processor `@Transactional`: delete chunks, `vectorStore.add()` (embedding inside store), intent status — separate from ticket saves | R-04 |
| D6 | `FOR UPDATE SKIP LOCKED` claim + collapse by ticket | R-05 |
| D7 | FR-016 regex matches 001 search pattern | R-06 |
| D8 | Exclude `@Tag("llm")` from default Maven | R-07 |

See [research.md](./research.md).

## Project Structure

### Documentation

```text
specs/002-ticket-assistant/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/openapi.yaml
└── spec.md
```

### Source (additions)

```text
backend/src/main/java/com/ttn/tickets/assistant/
├── config/
├── chunking/
├── refresh/
├── retrieval/
├── generation/
└── web/
```

Flyway: `V4__assistant_pgvector_and_refresh_processing.sql`.

## Refresh processing

Scheduled claim → collapse by `ticket_id` → load current ticket → chunk → **one processor transaction**: delete chunks by `ticketId` metadata filter, `vectorStore.add()` (calls `EmbeddingModel` internally), mark intents `DONE` / retry / `FAILED`.

**Rebuild** (`POST /api/ai/refresh/rebuild`, 202): insert **one `PENDING` refresh intent per ticket** (durable rows); the scheduled processor performs indexing — no synchronous full embed in the HTTP thread.

## Ask flow

Validate (≤1000 chars) → similarity search (top-K, threshold) → **FR-016**: for each existing `TKT-<n>` in the question, fetch chunks via **metadata filter** `ticketId == …` and **merge into the candidate set before dedupe by ticketId**; forced tickets are **exempt from the similarity threshold** → if empty ⇒ no `ChatModel` → one call → cite verify.

## Prompt caching (Spring AI 2.0.2)

**Verified**: `BedrockCacheOptions`, `BedrockCacheStrategy`, on `BedrockChatOptions`; `BedrockProxyChatModel` (tag v2.0.2). Static system grounding text in cacheable prefix; ticket context in user message. **Fallback**: `NONE` — same behaviour, no cache.

**Bedrock minimum cacheable prefix**: depends on `assistant.bedrock.chat-model-id` (see [AWS Bedrock prompt caching](https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html) model table — minima **512–4,096 tokens** per checkpoint). The static system prompt must exceed the minimum for the configured model or caching is ineffective (request still succeeds, prefix not cached). Validate with repeated asks and non-zero **cache read** token usage in response metadata ([quickstart.md](./quickstart.md)).

## PgVectorStore transactions

**Verified**: `PgVectorStore` v2.0.2 uses `JdbcTemplate` only; joins active Spring transaction on same `DataSource` when called from `@Transactional` processor. `add()` invokes `EmbeddingModel.embed` inside the store ([`doAdd`](https://raw.githubusercontent.com/spring-projects/spring-ai/v2.0.2/vector-stores/spring-ai-pgvector-store/src/main/java/org/springframework/ai/vectorstore/pgvector/PgVectorStore.java)) — acceptable in the **refresh** transaction, not in 001 ticket saves.

Table schema and `spring.ai.vectorstore.pgvector.table-name`: [data-model.md](./data-model.md).

## Test strategy

No-match: assert chat stub not called. Citation violation, FR-016, refresh collapse, retry→failed, rebuild, rollback (no intent). LLM eval tagged separately.

## Complexity Tracking

| Item | Why |
|------|-----|
| Processing columns on `ticket_refresh_intent` | Retry, status, SKIP LOCKED |
| Section-aware chunk builder | Spec + rag rule |