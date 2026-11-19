# Carry-over to 002-ticket-assistant

Purpose: items deferred from 001 or discovered early that the 002 spec/plan MUST address.
Consume when running /speckit-specify for 002; mark each item with where it landed.

## A. Moved out of 001 (MUST be specified in 002)
Source: 001 clarification session 2026-11-19, Q1; constitution Principle V

- [ ] A1. Process refresh intents: build the ticket's knowledge document, embed it, store it, mark the intent done.
- [ ] A2. Retry failed refreshes with backoff and a maximum attempt count; after that, mark them failed (no infinite retry).
- [ ] A3. Failure visibility: define WHO can see outstanding/failed refreshes and HOW (e.g., a count/list an operator can query).
- [ ] A4. Full rebuild of all embeddings from stored tickets and comments.
- [ ] A5. Verification that stored knowledge matches the current ticket (the removed "knowledge matches" acceptance scenarios).
- [ ] A6. Until 002 ships, the system does not meet Principle V. 002 is what closes that gap.

## B. Constraints already decided
- B1. The processor reads the ticket's CURRENT state when it runs, not a snapshot from when the intent was created; otherwise an older refresh can overwrite newer knowledge. Multiple pending intents for one ticket collapse into one refresh. (Source: 001 spec review, concurrency discussion)
- B2. Refresh triggers already recorded by 001: create, field edit, comment (including on CLOSED/CANCELLED tickets), every status move including cancel. (Source: 001 FR-016)
- B3. Knowledge-document source fields available from 001: identifier, title, description, priority, category (Billing/Technical/Account/Shipping/Other), status, assignee, resolution notes, cancellation reason, comments. (Source: 001 spec)
- B4. Vector table is created by a versioned migration; Spring AI's PGVector auto schema initialization MUST be disabled. (Source: constitution, Technology Constraints)
- B5. No relevant result above the threshold → return the "no relevant tickets" response WITHOUT calling the LLM. (Source: constitution Principle IV)
- B6. Top-K and similarity threshold are configuration, not code. (Source: constitution Principle VI)
- B7. Single retrieve → generate flow; no tool calling, no chained model calls. (Source: constitution Principle VII)

## C. Open questions 002 must answer
- [ ] C1. A cited ticket ID is not in the retrieved set: strip the citation, regenerate, or return the fallback answer? (Constitution IV says only "not returned as-is")
- [ ] C2. Document shape and chunking: one document per ticket, or separate chunks for description / comments / resolution? Paragraph vs fixed-size vs semantic splitting, justified for ticket data. (Assessment requires this documented in architecture.md)
- [ ] C3. Embedding model: local (Ollama) vs cloud; cost/latency/quality tradeoff. Note that the vector dimension is fixed in the migration, so changing models means a migration plus a full rebuild. Reviewers must be able to run it without your private credentials.
- [ ] C4. Questions like "Which high-priority tickets are related to payment?" need metadata filtering. Principle VII forbids a second LLM call to extract filters from the question, so how? Semantic search only, rule-based filter detection, or explicit filter parameters on /api/ai/ask?
- [ ] C5. Should CANCELLED tickets be retrievable? Should "similar resolved tickets" filter on status?
- [ ] C6. Does Spring AI's PgVectorStore join the Spring-managed transaction or use its own connection? Verify against the pinned version; don't assume. (Source: instructor's unverified claim, see ai-mistakes M-006)
