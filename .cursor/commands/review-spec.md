# Review spec

Review the given `specs/<feature>/spec.md` (or the current feature's spec). Do not edit it.

Report as a numbered list with severity:

1. **Tech leaks** — frameworks, databases, HTTP codes, URLs, JSON in a what/why spec.
2. **Silent assumptions** — decisions made in Assumptions or requirements without a clarification; list each with its consequence.
3. **Testability** — any FR or success criterion that cannot be checked by an automated test (vague words, UI timings in a backend feature, unbounded "until").
4. **Contradictions** — between requirements, scenarios, edge cases, assumptions, and the Clarifications log.
5. **Constitution conflicts** — especially I (state machine), IV (grounding), V (freshness), IX (simplicity). Include concurrency effects on the state machine.
6. **Source requirement coverage** — compare against the assessment requirements document; list anything missing or only partially covered.
7. **Scope creep** — behaviour not requested.

End with the top 5 questions worth asking in `/speckit-clarify`, ranked by impact.
