---
description: Testing guidelines for deterministic logic and AI output
globs: backend/src/test/**
alwaysApply: false
---

# Testing guidelines

## Deterministic logic (state machine, validation, persistence)
- Integration tests run against real PostgreSQL via Testcontainers 2.x. No H2, no mocked repositories.
- Write state-machine tests first; run them and record the failing run before implementing.
- The transition matrix covers all 25 from/to pairs: 5 allowed (assert new status, `updatedAt` changed, one new refresh intent) and 20 refused (assert 409 `INVALID_TRANSITION`, `currentStatus`, `allowedTransitions`, ticket unchanged, zero new intents).
- Every error test asserts status code AND `errorCode` AND (for 400) the field names.
- Concurrency tests must be deterministic (hold the row lock in the test, queue the competing request, release); no `Thread.sleep`-based races.
- Refresh intent: assert one intent per accepted change and none for refused or rolled-back changes.
- Test names describe behaviour: `should_refuse_move_from_CLOSED_to_OPEN`.
- Each test sets up its own data; never depend on test order or seed data.

## AI output (feature 002+)
- Never assert exact LLM wording. Assert structure and grounding properties:
  - cited ticket IDs ⊆ retrieved ticket IDs;
  - no-match question returns the no-relevant-tickets response AND the chat model is never invoked (verify with a test double around the model, not the retriever);
  - in-scope question cites at least one expected ticket ID.
- Retrieval quality is measured with a fixed evaluation set (question → expected ticket IDs) reporting hit rate @K; keep the set in `src/test/resources/eval/`.
- Tests that call a real model are tagged `@Tag("llm")` and excluded from the default build.
