---
description: RAG / vector store guidelines — chunking convention, embedding model choice, retrieval-tuning defaults
globs: backend/src/main/java/**/assistant/**, backend/src/main/resources/**, specs/002-*/**
alwaysApply: false
---

# RAG & vector store guidelines

## Storage
- Vector store: PGVector in the same PostgreSQL instance (constitution). Table created by a Flyway migration (`CREATE EXTENSION IF NOT EXISTS vector`, table, HNSW index). Spring AI `initialize-schema: false`.
- Vector dimension in the migration MUST equal the embedding model's output dimension. Changing the model = new migration + full reindex.

## Knowledge documents (chunking convention)
- Source of truth is the relational ticket; documents are derived and disposable.
- One knowledge document per ticket, built as labelled sections:
  `Ticket TKT-n | Title | Category | Priority | Status` / `Description` / `Resolution notes` / `Cancellation reason` / `Comments` (chronological, `author: text`).
- Chunking: section-aware. If the whole document fits the chunk budget (default 800 tokens), keep ONE chunk. Otherwise split on section boundaries (comments split in groups), never mid-sentence; repeat the header line (ID, title, category, status) at the top of every chunk so each chunk is self-describing.
- Rationale: tickets are short, structured records; fixed-size splitting cuts resolutions away from their problem, semantic splitting adds a model call for little gain on short text.
- Metadata on every chunk: `ticketId`, `status`, `priority`, `assignee`, `category`, `chunkType`.
- Refresh = delete all chunks for `ticketId`, then insert new ones, reading the ticket's CURRENT state (never a snapshot from the intent).

## Retrieval defaults (all configurable via `@ConfigurationProperties`, never hardcoded)
- `assistant.retrieval.top-k`: 5
- `assistant.retrieval.similarity-threshold`: 0.70 (tune with the evaluation set; document the chosen value and why)
- Results are de-duplicated by `ticketId` before building the prompt.

## Generation & grounding
- Single retrieve → generate call. No tool calling, no chained model calls, no agents.
- If no chunk meets the threshold: return the fixed no-relevant-tickets response WITHOUT calling the chat model.
- System prompt (static, cacheable) instructs: answer only from the provided tickets; cite ticket IDs in square brackets; if the tickets do not contain the answer, say so.
- After generation: extract cited IDs; any ID not in the retrieved set → do not return the answer as-is (see spec 002 for the exact behaviour).
- Temperature low (≤ 0.2). Model names and endpoints come from configuration.

## Embedding model choice
- Document the choice and the cost / latency / quality trade-off in `spec/architecture.md` (local via Ollama vs cloud).
- Whatever is chosen, reviewers must be able to run the project without your personal credentials (`.env.example` + documented setup).
