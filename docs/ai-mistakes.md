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