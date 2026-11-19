# Review code

Review the code changed in the current task/phase (use `git diff` against the last commit if no files are given).

Check, and report findings as a table (file:line, severity HIGH/MED/LOW, issue, fix):

1. **Spec traceability** — every behaviour maps to an FR/scenario in the current `specs/<feature>/spec.md`. Flag behaviour with no spec source, and spec requirements in scope with no code.
2. **Constitution** — state machine only in domain (I); validation + problem details (II); refresh intent written in the same transaction for every change (V); no hardcoded tunables (VI); no secrets (VIII); no unjustified abstractions (IX).
3. **Pinned platform traps** — any `javax.*`, `com.fasterxml.jackson.databind`, Testcontainers 1.x, Spring AI 1.x API, `RestTemplate`, `ddl-auto` other than `validate`.
4. **Transactions & locking** — `@Transactional` on application services only; edit/transition/comment use the pessimistic lock; no remote calls inside a DB transaction.
5. **Error handling** — correct status + errorCode per `api-standards.mdc`; no stack traces leaked.
6. **Tests** — each changed behaviour has a test that would fail if the behaviour broke.

Do NOT modify code in this command. End with: "Blocking issues: N" and the list.
