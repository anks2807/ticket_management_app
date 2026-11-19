## Phase: Plan — 001-ticket-lifecycle

### P-0xx · /speckit-plan
- **Prompt:** Technical plan with pinned versions (Java 21, Spring Boot 4.1.2, Spring AI 2.0.2 pinned-not-used, Maven 3.9.12 wrapper), layered architecture, outbox, pessimistic locking, RFC 9457 errors (400/409/404), Testcontainers.
- **Raw log:** .specstory/history/2026-11-18_18-49-53Z-ai-support-ticket-constitution.md
- **Version sources:** <spring.io Spring Boot URL>, <Spring AI getting-started URL> (Spring AI 2.0.x supports Boot 4.0/4.1; Boot 4.0 EOL 2026-12-31, so chose 4.1.x)
- **AI output:** plan.md, research.md, data-model.md, contracts/openapi.yaml, quickstart.md; 11 self-made decisions (D1–D11) flagged for confirmation.

### Decisions on plan D1–D11
Recommendations came from the chat instructor (Claude); final decisions and reasons are mine.

| # | Decision | Final | Reason |
|---|----------|-------|--------|
| D1 | 409 for edits refused by ticket state (TICKET_FROZEN, RESOLUTION_NOTES_NOT_EDITABLE) | Accepted | Same category as invalid transitions: request is fine, ticket state forbids it. |
| D2 | Check order 400 → 404 → 409 | Accepted | Validate input before touching the DB; state checks need the ticket to exist. |
| D3 | PATCH semantics | **Overruled** → JSON Merge Patch (RFC 7396): absent = unchanged, null = clear; only assignee is clearable; null on required field = 400 | AI's "blank string clears" was an invented convention; Merge Patch is a standard clients already understand. |
| D4 | Stray fields on a transition = 400 | Accepted | Silently ignoring input hides client bugs. |
| D5 | Comments also take the row lock | Accepted | Comment updates updatedAt; without the lock it could overwrite a concurrent change. |
| D6 | Wire casing for priority/category | **Overruled** → enum names everywhere (LOW, BILLING, IN_PROGRESS) | One casing convention; removes a needless mapping layer. |
| D7 | Cancellation reason not searchable | Accepted (spec unchanged) | Spec doesn't require it; adding it violates Principle IX without a need. |
| D8 | Trim keyword; %, _, \ literal; canonical identifier | Accepted | Prevents wildcard injection into LIKE queries. |
| D9 | Seed data writes refresh intents | Accepted | Otherwise 002 starts with seeded tickets it doesn't know about. |
| D10 | Package/coordinates | **Overruled** → com.ttn.tickets / com.ttn:ticket-management | Fix naming before code exists; renaming later is churn. |
| D11 | Lock timeout → 503 TICKET_BUSY | Accepted **with change**: add Retry-After header; add edge case to spec first | Contention is temporary, so retrying the same request can succeed (unlike 409). Spec must own the behaviour. |

### Review findings on the plan
- Plan states create takes a pessimistic row lock: impossible, no row exists yet → ai-mistakes M-0xx.
- Plan claims versions were "read from the BOM" and "confirmed on Maven Central": pending — checked via ./mvnw dependency:tree in Step 2.
- Environment: only JDK 25 installed; installed JDK 21 to satisfy enforcer.