CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE assistant_ticket_chunk (
    id        uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    content   text,
    metadata  jsonb,
    embedding vector(1024)
);

CREATE INDEX assistant_ticket_chunk_embedding_hnsw_idx
    ON assistant_ticket_chunk USING HNSW (embedding vector_cosine_ops);

ALTER TABLE ticket_refresh_intent
    ADD COLUMN processing_status varchar(20),
    ADD COLUMN attempt_count int,
    ADD COLUMN next_attempt_at timestamptz,
    ADD COLUMN processed_at timestamptz,
    ADD COLUMN last_error text;

UPDATE ticket_refresh_intent
SET processing_status = 'PENDING',
    attempt_count     = 0
WHERE processing_status IS NULL;

ALTER TABLE ticket_refresh_intent
    ALTER COLUMN processing_status SET NOT NULL,
    ALTER COLUMN processing_status SET DEFAULT 'PENDING',
    ALTER COLUMN attempt_count SET NOT NULL,
    ALTER COLUMN attempt_count SET DEFAULT 0;
