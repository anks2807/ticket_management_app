# Data Model: Ticket Assistant (002)

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

## `ticket_refresh_intent` (extended)

001 columns: `id`, `ticket_id`, `change_type`, `created_at`.

Added by `V4`:

| Column | Type | Rules |
|--------|------|-------|
| `processing_status` | varchar(20) NOT NULL DEFAULT `PENDING` | `PENDING`, `DONE`, `FAILED` |
| `attempt_count` | int NOT NULL DEFAULT 0 | |
| `next_attempt_at` | timestamptz | null ⇒ eligible now |
| `processed_at` | timestamptz | Set when `DONE` |
| `last_error` | text | Operator visibility |

## `assistant_ticket_chunk` (PgVectorStore 2.0.2)

Flyway creates a table matching **`PgVectorStore`** default column layout (UUID id type, cosine HNSW). **Source checked**: `PgVectorStore.afterPropertiesSet()` SQL at tag **v2.0.2** (`spring-projects/spring-ai`); configuration property **`spring.ai.vectorstore.pgvector.table-name`** = `assistant_ticket_chunk` (default `vector_store` in [Spring AI PGVector reference](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)).

| Column | PostgreSQL type | Notes |
|--------|-----------------|-------|
| `id` | `uuid` PRIMARY KEY DEFAULT `uuid_generate_v4()` | Document id; `PgVectorStore` `PgIdType.UUID` |
| `content` | `text` | Chunk text (`content-field-name`, default `content`) |
| `metadata` | `jsonb` | Chunk metadata; store uses `?::jsonb` on insert (compatible with `json`) |
| `embedding` | `vector(1024)` | Dimension must match `spring.ai.vectorstore.pgvector.dimensions` / `assistant.bedrock.embedding-dimensions` |

Required extensions (migration): `vector`, `uuid-ossp` (per `PgVectorStore` schema init). Index: `HNSW` on `embedding` with `vector_cosine_ops`.

## Configuration (`assistant.*`)

| Property | Default |
|----------|---------|
| `assistant.ask.max-question-length` | 1000 |
| `assistant.retrieval.top-k` | 5 |
| `assistant.retrieval.similarity-threshold` | 0.70 |
| `assistant.chunking.max-tokens` | 800 |
| `assistant.refresh.poll-interval` | 5s |
| `assistant.refresh.batch-size` | 20 |
| `assistant.refresh.max-attempts` | 5 |
| `assistant.refresh.backoff-base` | 30s |
| `assistant.bedrock.chat-model-id` | (required in prod; env-backed) |
| `assistant.bedrock.embedding-model-id` | `amazon.titan-embed-text-v2:0` |
| `assistant.bedrock.embedding-dimensions` | 1024 |
| `assistant.bedrock.prompt-caching.enabled` | `true` |