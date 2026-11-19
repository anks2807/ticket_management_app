---
description: Spec-driven workflow rules for this repository. Always applies.
alwaysApply: true
---

# Project workflow (spec-driven)

- Source of truth order: `.specify/memory/constitution.md` > `specs/<feature>/spec.md` > `plan.md` > `tasks.md` > code.
- Never implement behaviour that is not in the current feature's spec. If something is missing, stop and say which spec section needs an amendment.
- Never build more than the current phase of `tasks.md`. Do not "build the complete application".
- Pinned versions (do not change without a plan update): Java 21, Spring Boot 4.1.2, Spring AI 2.0.2, Maven 3.9.12 (wrapper).
- When you are not certain an API, class, property or version exists in the pinned versions, say so explicitly instead of guessing.
- When you claim to have verified something (a version, a file, a test result), state how: command run, file read, or URL fetched. Never claim verification you did not perform.
- Never put secrets in code, config, tests, docs or examples. Use environment variables and `.env.example` placeholders.
- Any AI mistake found during work is recorded in `docs/ai-mistakes.md` (what was produced, what was correct, how it was caught, fix).
