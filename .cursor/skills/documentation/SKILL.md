---
name: documentation
description: Write or update project documentation (spec/ artefacts, architecture.md, README, ADR-style decisions, ai-mistakes and prompt-history entries). Use when asked to document a decision, a feature, the architecture, or a mistake.
---

# Documentation skill

## Principles
- Document decisions, not just facts: every non-obvious choice states Context → Decision → Alternatives considered → Consequences.
- Trace everything: link to the spec requirement (FR-xxx), constitution principle, or task that drove it.
- Never invent facts about the code. Read the code or spec first; if something is not implemented, say "not implemented" instead of describing intended behaviour as real.
- Prefer short sections and tables over long prose. No marketing language.

## Where things go
- `spec/` — assessment artefacts (requirements, architecture, data-model, api-contract, state-machine, rag-ingestion, rag-api-contract, evaluation-strategy, ui-flow, test-strategy). Each summarises and LINKS to the spec-kit sources in `specs/<feature>/` rather than duplicating them.
- `spec/architecture.md` MUST include: component diagram (Mermaid), chunking strategy with justification, embedding model choice with cost/latency/quality trade-off, retrieval parameters and how they were tuned, outbox/refresh design.
- `docs/ai-mistakes.md` — entry format: ID, date/phase, tool, category (WRONG-CODE, UNGROUNDED-ANSWER, OMISSION, INVENTED-REQUIREMENT, OVERSIMPLIFICATION), what the AI produced, what was correct, how caught, impact, fix, prevention.
- `docs/prompt-history.md` — per meaningful prompt: phase, prompt summary, raw log link (`.specstory/history/...`), AI output, my decision, accepted/rejected, linked mistake.
- `README.md` — what it is, prerequisites, setup (`.env`, docker compose, models), run, test, project layout, links to spec/ and docs/.

## Checklist before finishing
- [ ] Every claim verifiable from code, spec, or a command output.
- [ ] Links resolve.
- [ ] No secrets, tokens, or personal data.
