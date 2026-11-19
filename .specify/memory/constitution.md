<!--
Sync Impact Report (temporary; remove before committing)
- Version change: 1.0.0 -> 2.0.0 (MAJOR: Principle IV redefined; mandated stack changed
  H2 -> PostgreSQL and Redis -> PGVector, per Governance versioning policy)
- Modified principles:
  - II. Validation at the Boundary: invalid transitions distinguishable from malformed
    input by status code and error code
  - III. Test-First for Deterministic Logic: real containerized PostgreSQL, not H2/mocks
  - IV. Grounded AI Only: "MUST NOT use general model knowledge" replaced by claim
    traceability, cited IDs subset of retrieved IDs verified in code, no-LLM short circuit
  - V. Knowledge Freshness: expanded triggers; atomic refresh intent in the ticket
    transaction; embedding generation outside it; retries; full reindex capability
- Added sections: none
- Modified sections: Technology Constraints (PostgreSQL, PGVector, versioned migrations,
  pinned versions); Development Workflow & Quality Gates (lint/type checks removed;
  compilation and full test suite required)
- Removed sections: none
- Templates reviewed (not modified by this command): .specify/templates/plan-template.md,
  spec-template.md, tasks-template.md (plan MUST pin Spring Boot, Spring AI, and build
  tool versions)
- Follow-up TODOs: none
-->

# AI-Powered Support Ticket Management System Constitution

## Core Principles

### I. Domain Integrity (NON-NEGOTIABLE)

The ticket lifecycle is enforced exclusively by the backend domain layer. The only valid
transitions are:

- `OPEN` → `IN_PROGRESS` → `RESOLVED` → `CLOSED`
- `OPEN` → `CANCELLED`
- `IN_PROGRESS` → `CANCELLED`

Every other transition MUST be rejected with a client error and MUST NOT change persisted
state. Clients (including the React frontend) are never trusted: UI controls may hide
invalid actions for usability, but the backend MUST re-validate every request. Transition
rules MUST live in one place in the domain layer, not in controllers, repositories, or the
frontend.

Rationale: a single authoritative state machine prevents inconsistent ticket data and
makes the rules testable.

### II. Validation at the Boundary

All input MUST be validated server-side at the REST boundary before reaching domain or
persistence logic. All errors MUST use one consistent, machine-readable error format that
includes a stable error code, a human-readable message, and field-level details where a
field is at fault. No endpoint may return ad-hoc error shapes or leak stack traces.

An invalid state transition MUST be distinguishable from malformed input by both HTTP
status code and error code: the two cases MUST use different status codes and different
error codes, and neither may be reused for the other.

Rationale: clients need predictable errors to render feedback, and a client must be able to
tell "your request is badly formed" from "this action is not allowed in the current state".

### III. Test-First for Deterministic Logic

Every ticket state transition, valid and invalid, MUST have an integration test run against
a real, containerized PostgreSQL database before the feature is considered done. Integration
tests MUST NOT use H2 or mocks of the persistence layer. Tests for deterministic logic MUST
be written before the implementation and observed to fail first. A feature touching
transitions is not complete while any transition lacks a passing test.

Rationale: deterministic rules have a complete, enumerable test matrix, and tests must
exercise the same database engine used in production.

### IV. Grounded AI Only (NON-NEGOTIABLE)

The assistant MUST answer solely from retrieved ticket context. Every factual claim in an
answer MUST be traceable to one or more retrieved tickets, and every answer MUST cite the
ticket IDs it used. The set of cited ticket IDs MUST be a subset of the retrieved ticket
IDs, and this MUST be verified in code after generation; an answer citing any ID outside
the retrieved set MUST NOT be returned as-is. When no retrieval result meets the configured
similarity threshold, the system MUST return the no-relevant-tickets response without
invoking the LLM. Prompts MUST instruct the model accordingly, and tests MUST cover the
grounded, cited, citation-subset violation, and no-relevant-tickets (LLM not invoked)
cases. Fabrication (unsupported claims or invalid citations) is a defect, not a quality
issue, and MUST be triaged and fixed as a bug.

Rationale: users act on the answers; an ungrounded answer is worse than no answer, and
prompt instructions alone cannot guarantee grounding, so it is verified in code.

### V. Knowledge Freshness

The embeddings of a ticket MUST be refreshed after each of the following: ticket creation,
field updates, comment additions, and every status transition, including cancellation. A
stale knowledge base is a defect.

- The intent to refresh MUST be recorded atomically with the ticket change, in the same
  database transaction.
- Embedding generation MUST NOT run inside that transaction.
- Failed refreshes MUST be retried, and failures MUST be surfaced (logged and observable),
  never silently swallowed.
- A full reindex capability MUST exist to rebuild all embeddings from ticket data.
- Tests MUST verify that each trigger records a refresh intent, that rolled-back ticket
  changes record none, and that failed refreshes are retried.

Rationale: grounded answers are only as good as the freshness of what they are grounded in,
and recording intent transactionally ensures no ticket change is ever lost, while keeping
slow model calls out of the database transaction.

### VI. Configuration over Hardcoding

Retrieval parameters (top-K, similarity threshold), model names, and service endpoints MUST
be externalized configuration (e.g., Spring configuration properties) with documented
defaults. They MUST NOT be hardcoded in source. Changing them MUST NOT require code
changes.

Rationale: AI tuning is iterative and environments differ.

### VII. Bounded AI Scope

The assistant is a single retrieve → generate flow. It MUST NOT take actions: no ticket
creation or modification, no notifications, no tool calling, and no chaining of model calls
or agents. AI components MUST have read-only access to ticket data.

Rationale: a bounded, read-only assistant is predictable, testable, and safe.

### VIII. Security

No secrets (API keys, tokens, passwords, certificates) may exist in the repository, ever,
including history, tests, docs, and sample config. Secrets MUST be supplied via environment
or an external secret store, and the repository MUST provide placeholder-only examples. A
leaked secret MUST be removed and rotated immediately.

Rationale: leaked credentials are costly and irreversible once public.

### IX. Simplicity

No abstraction, layer, library, or configuration option may be added without a current
requirement that needs it. Speculative generality MUST be rejected in review. Prefer the
smallest change that satisfies the requirement.

Rationale: unused abstractions are cost without benefit.

### X. AI Accountability

AI-generated code and answers are reviewed, not trusted. All AI-generated changes MUST be
reviewed by a human and pass the same gates as hand-written code. Mistakes made by AI
tooling MUST be recorded in `docs/ai-mistakes.md` with what happened, impact, and how it was
caught or corrected.

Rationale: learning from AI errors reduces recurrence and keeps humans accountable.

## Technology Constraints

The following stack is mandated; deviations require a constitution amendment:

- Language/runtime: Java 21
- Backend framework: Spring Boot
- AI integration: Spring AI
- Database: PostgreSQL, for both relational data and vector storage
- Vector store: PGVector, in the same PostgreSQL instance as the relational data
- API style: REST
- Frontend: React

Database schema and seed data MUST be managed exclusively through versioned migrations kept
in the repository, so that anyone can reproduce the database from the repository alone.
Manual or out-of-band schema changes are prohibited.

The exact versions of Spring Boot, Spring AI, and the build tool MUST be pinned in the
plan and MUST NOT drift; changing a pinned version requires an explicit plan update.

## Development Workflow & Quality Gates

- Every change MUST be traceable to a spec and verified against Principles I-X in the plan's
  Constitution Check.
- A feature is done only when: validation and error-format behavior is covered by tests,
  every affected state transition has a passing integration test (Principle III), refresh
  intent recording and retry behavior is tested where tickets change (Principle V), and
  grounded-answer behavior is tested where the assistant changes (Principle IV).
- Code review MUST verify no secrets, no hardcoded AI parameters, and no unjustified
  abstractions.
- The project MUST compile successfully and the full test suite MUST pass before merge.

## Governance

This constitution supersedes all other practices. Amendments require a documented proposal,
review approval, an updated version, and a migration plan for any affected code, templates,
or specs. Versioning follows semantic versioning: MAJOR for backward-incompatible principle
removals or redefinitions, MINOR for new principles/sections or materially expanded
guidance, PATCH for clarifications and wording fixes. All PRs and reviews MUST verify
compliance with this constitution; violations of NON-NEGOTIABLE principles (I, IV) block
merge, and any other complexity or deviation MUST be justified in writing in the PR or plan.

**Version**: 2.0.0 | **Ratified**: 2026-11-19 | **Last Amended**: 2026-11-19
