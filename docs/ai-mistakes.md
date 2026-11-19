# AI Mistakes Log

Every meaningful incorrect, ungrounded, or invented AI output caught during development.
Categories: WRONG-CODE · UNGROUNDED-ANSWER · OMISSION · INVENTED-REQUIREMENT · OVERSIMPLIFICATION

---
## M-001 · Re-embedding triggers missed comments and status changes
- **Date / Phase:** 2026-11-19 · Constitution
- **Tool:** Cursor /speckit-constitution (generated)
- **Category:** OMISSION
- **AI said:** Principle V: refresh embeddings on ticket "create, update, or close".
- **Actually true:** Requirements say knowledge documents include comments and metadata includes status, so adding a comment and every status transition (including CANCELLED) must also trigger a refresh.
- **How caught:** Cross-checked Principle V against the Ingestion section of the requirements document.
- **Impact:** The knowledge base would go stale after comments or cancellations, failing an acceptance criterion.
- **Fix:** Amended Principle V (v1.1.0).
- **Prevention:** Check AI-generated principles against the source requirements, not just the prompt. The generator copied the omission without flagging it.

## M-002 · Invented, unverifiable quality gate
- **Date / Phase:** 2026-11-19 · Constitution
- **Tool:** Cursor /speckit-constitution
- **Category:** INVENTED-REQUIREMENT
- **AI said:** "Lint, type checks, and the full test suite MUST pass before merge."
- **Actually true:** No linter was chosen; "type checks" is meaningless for Java beyond compiling. The gate could not be checked.
- **How caught:** Review question: "Which tool enforces this?" No answer existed.
- **Impact:** A rule nobody can check, which weakens trust in the rest of the document.
- **Fix:** Replaced with "successful compilation and full test suite pass" (v1.1.0).
- **Prevention:** Every MUST in a spec or constitution needs a named way to verify it.

## M-003 · Untestable absolute in grounding principle
- **Date / Phase:** 2026-11-19 · Constitution
- **Tool:** Cursor /speckit-constitution
- **Category:** OVERSIMPLIFICATION
- **AI said:** "The assistant MUST NOT use general model knowledge."
- **Actually true:** An LLM always uses its general knowledge to produce language; the rule cannot be tested or enforced as written.
- **How caught:** Asked how a test would check this principle.
- **Impact:** A non-negotiable principle with no way to check it.
- **Fix:** Replaced with testable rules: claims traceable to retrieved tickets; cited IDs ⊆ retrieved IDs checked in code; LLM skipped when nothing passes the threshold (v1.1.0).
- **Prevention:** Rewrite AI-quality rules as checks that can run in code.

## M-004 · "Single transaction" claim oversimplified
- **Date / Phase:** 2026-11-19 · Architecture discussion
- **Tool:** Claude (chat instructor)
- **Category:** OVERSIMPLIFICATION
- **AI said:** With Postgres + PGVector, the ticket and embedding writes "could share one transaction."
- **Actually true:** Embedding generation is a remote model call; running it inside the DB transaction ties ticket saves to the AI provider being up. The correct pattern is a transactional outbox: record the reindex intent in the same transaction and embed asynchronously with retries.
- **How caught:** Follow-up review of the claim before writing it into the constitution.
- **Impact:** Would have made ticket CRUD fail whenever the embedding provider was down.
- **Fix:** Principle V now requires atomic intent recording and forbids embedding inside the transaction.
- **Prevention:** For any "just put it in one transaction" suggestion, ask what external calls happen inside it.

## M-007 · Instructor predicted the wrong constitution version bump
- **Date / Phase:** 2026-11-19 · Constitution amendment
- **Tool:** Claude (chat instructor)
- **Category:** UNGROUNDED-ANSWER
- **AI said:** The amendment should produce v1.1.0 (MINOR).
- **Actually true:** The amendment redefined Principle IV and swapped the mandated stack; under the constitution's own Governance rules that is MAJOR → v2.0.0. The spec-kit command applied the rules correctly and explained why.
- **How caught:** Compared the generated Sync Impact Report against the Governance versioning rules.
- **Impact:** Recording MINOR would have labeled a breaking governance change (redefined principle and a new mandated stack) as backward-compatible.
- **Fix:** Accepted 2.0.0.
- **Prevention:** Re-apply the rules after the scope of a change changes; don't reuse an earlier prediction.

## M-008 · Category list did not cover the requirements' own examples
- **Date / Phase:** 2026-11-19 · /speckit-specify, /speckit-clarify (001)
- **Tool:** Cursor (spec-kit)
- **Category:** INVENTED-REQUIREMENT
- **AI said:** First, category as free text ("words the assistant can use"); then, in clarify, a fixed list of Billing, Technical, Account, Other.
- **Actually true:** Category is a RAG metadata filter, so free text fragments it ("Payment" vs "payments"); and the requirements example "common causes of shipment tracking issues" has no matching category, so it would fall into Other.
- **How caught:** Checked the category values against the requirements document's example questions.
- **Impact:** Shipment-tracking tickets would be stored as Other, so a category filter could not retrieve them; free-text values would also split equivalent categories.
- **Fix:** Fixed list Billing, Technical, Account, Shipping, Other (post-clarify amendment).
- **Prevention:** Check every enumerated value against the source requirements and example queries.

## M-009 · Untestable success criterion for a backend-only feature
- **Date / Phase:** 2026-11-19 · /speckit-specify (001)
- **Tool:** Cursor (spec-kit)
- **Category:** INVENTED-REQUIREMENT
- **AI said:** SC-001 "An agent can create a ticket and open its full details in under one minute."
- **Actually true:** The feature has no UI; a human timing target is not measurable by an automated test and measures nothing about the system.
- **How caught:** Manual spec review against the "every criterion must be testable" rule.
- **Impact:** A success criterion that no automated test can check, and that says nothing about the backend feature.
- **Fix:** SC-001 deleted.
- **Prevention:** Success criteria must be checkable for the feature's actual scope.

## M-010 · Last-write-wins would break the non-negotiable state machine
- **Date / Phase:** 2026-11-19 · /speckit-specify, /speckit-clarify (001)
- **Tool:** Cursor (spec-kit)
- **Category:** WRONG-CODE (design defect)
- **AI said:** "Two agents change the same ticket … the last accepted change is stored; no conflict detection."
- **Actually true:** Two concurrent moves from OPEN (→IN_PROGRESS and →CANCELLED) would both validate against OPEN; the later write could turn a CANCELLED ticket into IN_PROGRESS, a forbidden transition (Constitution I, NON-NEGOTIABLE). The same race allows edits on frozen tickets.
- **How caught:** Manual review combining the concurrency assumption with the state machine; neither specify nor clarify flagged it.
- **Impact:** A later write could store a forbidden transition or an edit on a frozen ticket.
- **Fix:** Spec requires every move/edit to be evaluated against the state at save time; plan uses a pessimistic row lock; concurrency tests for both races.
- **Prevention:** For any concurrency rule, check it against every invariant in the constitution.

## M-011 · Plan claimed create takes a row lock
- **Date / Phase:** 2026-11-19 · /speckit-plan (001)
- **Tool:** Cursor (spec-kit)
- **Category:** WRONG-CODE
- **AI said:** "Every create, edit, comment, and status move … first takes a pessimistic row lock on the ticket."
- **Actually true:** A create has no existing row to lock; only edit, transition, and comment lock.
- **How caught:** Manual plan review.
- **Impact:** Implementers would try to lock a row that does not exist yet on create.
- **Fix:** Plan corrected (D-decision prompt).
- **Prevention:** Read summary statements literally; check them against what each operation can actually do.