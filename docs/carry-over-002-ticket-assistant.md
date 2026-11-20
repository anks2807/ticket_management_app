# Carry-over to 002-ticket-assistant

Purpose: items deferred from 001 or discovered early that the 002 spec/plan MUST address.
Consume when running /speckit-specify for 002; mark each item with where it landed.

## A. Moved out of 001 (MUST be specified in 002)
Source: 001 clarification session 2026-11-19, Q1; constitution Principle V

- [x] A1. Process refresh intents → User Story 3, FR-008–FR-009 (`specs/002-ticket-assistant/spec.md`)
- [x] A2. Retry failed refreshes → User Story 3, FR-010
- [x] A3. Failure visibility → User Story 4, FR-011
- [x] A4. Full rebuild → User Story 4, FR-012
- [x] A5. Knowledge matches current ticket → User Story 3 scenarios 1–2, SC-003, SC-005
- [x] A6. Principle V consumption → Stories 3–4; closure when 002 ships

## B. Constraints already decided
- B1. The processor reads the ticket's CURRENT state when it runs, not a snapshot from when the intent was created; otherwise an older refresh can overwrite newer knowledge. Multiple pending intents for one ticket collapse into one refresh. (Source: 001 spec review, concurrency discussion)
- B2. Refresh triggers already recorded by 001: create, field edit, comment (including on CLOSED/CANCELLED tickets), every status move including cancel. (Source: 001 FR-016)
- B3. Knowledge-document source fields available from 001: identifier, title, description, priority, category (Billing/Technical/Account/Shipping/Other), status, assignee, resolution notes, cancellation reason, comments. (Source: 001 spec)
- B4. Vector table is created by a versioned migration; Spring AI's PGVector auto schema initialization MUST be disabled. (Source: constitution, Technology Constraints)
- B5. No relevant result above the threshold → return the "no relevant tickets" response WITHOUT calling the LLM. (Source: constitution Principle IV)
- B6. Top-K and similarity threshold are configuration, not code. (Source: constitution Principle VI)
- B7. Single retrieve → generate flow; no tool calling, no chained model calls. (Source: constitution Principle VII)

## C. Open questions 002 must answer
- [x] C1. Citation subset violation → Clarifications Session 2026-11-20 / FR-005
- [x] C2. Chunking → R-02 in `specs/002-ticket-assistant/research.md`, `data-model.md`, rag rule
- [x] C3. Embedding → R-01 Titan V2 1024-dim on Bedrock; fakes for `verify`
- [x] C4. Metadata-style questions → FR-015 (natural language, stored attributes among retrieved tickets); plan chooses mechanism
- [x] C5. Status in retrieval → Clarifications Session 2026-11-20; User Story 1 scenario 4
- [x] C6. PgVectorStore txn → R-04 in `specs/002-ticket-assistant/research.md` (v2.0.2 source)
