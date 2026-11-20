# Research: Ticket Assistant (002)

**Feature**: [spec.md](./spec.md) | **Date**: 2026-11-20

Phase 0 resolves carry-over open questions (C2, C3, C6) and records verification evidence for Spring AI 2.0.2.

## R-01 — AWS Bedrock for chat and embeddings

| | |
|---|---|
| **Decision** | Chat via **Bedrock Converse** (`BedrockProxyChatModel`); embeddings via **Amazon Titan Text Embeddings V2** (`amazon.titan-embed-text-v2:0`). |
| **Rationale** | User-mandated stack; Titan V2 default output is 1024 dimensions, normalized embeddings suit cosine distance in PGVector. |
| **Alternatives** | Local Ollama (rejected: user pinned Bedrock); Titan V1 1536-dim (rejected: user pinned V2). |

Configuration: `assistant.bedrock.chat-model-id`, `assistant.bedrock.embedding-model-id`, `assistant.bedrock.embedding-dimensions` (default **1024**, must match Flyway `vector(N)`).

Credentials: AWS SDK default chain; `.env.example` documents `AWS_REGION` and `AWS_PROFILE` only.

## R-02 — Chunking and document shape (carry-over C2)

| | |
|---|---|
| **Decision** | One knowledge document per ticket; section-aware chunks per [.cursor/rules/rag-vector-store.md](../../.cursor/rules/rag-vector-store.md). |
| **Rationale** | Short structured tickets; avoids extra model calls (Principle VII). |
| **Alternatives** | Fixed-size-only or semantic chunking (rejected: see rag rule rationale). |

## R-03 — Bedrock prompt caching (verified)

| | |
|---|---|
| **Decision** | **Supported** in Spring AI 2.0.2 via `BedrockChatOptions.cacheOptions` + `BedrockCacheOptions` / `BedrockCacheStrategy` (reference: Spring AI Bedrock Converse docs). Use `SYSTEM_ONLY` with `multiBlockSystemCaching(true)` when static system text is split from dynamic content. |
| **Verification** | API: `org.springframework.ai.bedrock.converse.api.BedrockCacheOptions` (Spring AI 2.0.2 Javadoc). Implementation: `BedrockProxyChatModel` on tag `v2.0.2` inserts Bedrock `CachePoint` from `cacheOptions`. |
| **Fallback** | `BedrockCacheStrategy.NONE` — single chat call unchanged; higher cost/latency only. |
| **Bedrock minimum** | Per [AWS prompt caching](https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-caching.html), each cache checkpoint requires a model-specific minimum token count on the prompt prefix before the checkpoint (512–4,096 tokens depending on model). Static system prompt must exceed the minimum for `assistant.bedrock.chat-model-id` or Bedrock does not cache (no error). Verify via cache-read tokens on a second identical-prefix call ([quickstart.md](./quickstart.md)). |

Ticket context is sent in the **user** message; static grounding rules in the cacheable system prefix.

## R-04 — PgVectorStore transactions (carry-over C6)

| | |
|---|---|
| **Decision** | `PgVectorStore` v2.0.2 uses **`JdbcTemplate` only** — no separate pool or `@Transactional` on the class. |
| **Verification** | `PgVectorStore.java` at tag `v2.0.2` (`spring-projects/spring-ai`): `jdbcTemplate.batchUpdate` / `query` for all persistence. With the app's JPA `DataSource`, calls inside `@Transactional` join the same JDBC transaction via `DataSourceUtils`. |
| **Design** | `PgVectorStore.add()` calls `embeddingModel.embed` inside `doAdd` (v2.0.2 source) — embedding runs in the **refresh processor** `@Transactional` boundary (chunk delete, add, intent update). This is **not** the 001 ticket-change transaction (Constitution V). Never call `vectorStore.add()` inside ticket command transactions. |

## R-05 — Refresh processor

Scheduled job; claim `PENDING` rows `FOR UPDATE SKIP LOCKED`; collapse by `ticket_id`; current-state read; transactional delete+`vectorStore.add()`; retry/backoff; max attempts default 5.

**Rebuild**: `POST /api/ai/refresh/rebuild` enqueues **one `PENDING` refresh intent per ticket**; scheduled processor does all embedding and chunk writes.

## R-06 — Ask path

Validate → similarity search → **FR-016**: metadata filter `ticketId` for each existing id in question, merge candidates **before** dedupe by `ticketId`; forced tickets **exempt from similarity threshold** → empty ⇒ no `ChatModel` → one chat call → cite subset check.

## R-04b — Vector table schema (Spring AI 2.0.2)

Columns required by `PgVectorStore` (UUID id): `id uuid`, `content text`, `metadata` (json/jsonb), `embedding vector(N)`. Table name via **`spring.ai.vectorstore.pgvector.table-name=assistant_ticket_chunk`**. Source: `PgVectorStore.java` v2.0.2 `CREATE TABLE` in `afterPropertiesSet`; property list in [PGVector reference](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html). Details: [data-model.md](./data-model.md).

## R-07 — Test doubles

Integration tests: deterministic fake `EmbeddingModel`, recording `ChatModel` stub, Testcontainers pgvector. `@Tag("llm")` evaluation excluded by default in Maven.

## Carry-over closure

| Item | Resolution |
|------|------------|
| C2 | R-02 + [data-model.md](./data-model.md) + rag rule |
| C3 | R-01 Bedrock Titan V2; CI uses fakes |
| C6 | R-04 JdbcTemplate + transaction design |