# Feature Specification: Ticket Assistant (Grounded Q&A)

**Feature Branch**: `002-ticket-assistant`

**Created**: 2026-11-20

**Status**: Draft

**Input**: User description: "Ticket assistant: grounded question answering over ticket history. Support agents ask natural-language questions about past and current tickets and get one answer per question, based strictly on stored ticket information. Feature 001 already records a refresh intent atomically with every ticket change; this feature acts on those intents to keep the assistant's knowledge current."

## Clarifications

### Session 2026-11-20

- **C1 (citation subset violation)**: If cited ticket identifiers are not a subset of the retrieved set, the system MUST return the citation-verification-failure outcome directly, with no unsupported factual claims.
- **C5 (status in retrieval)**: Tickets in any status, including CANCELLED, MAY appear in retrieval when relevant. Questions that imply "resolved" (for example similar resolved tickets) MUST be interpreted as tickets in status RESOLVED or CLOSED unless the question explicitly names another status.

### Amendment 2026-11-20 (post-session)

- **C1 amended**: Removed one regeneration on citation subset failure; the system returns citation-verification-failure immediately. Rationale: Principle VII (bounded AI scope) forbids chained model calls.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ask a grounded question (Priority: P1)

A support agent asks a natural-language question about tickets (for example whether payment failures
have appeared before, what resolved a specific ticket, common causes of tracking issues, similar
resolved tickets, or high-priority payment-related tickets) and receives a single answer whose factual
claims are supported only by relevant stored tickets, with those tickets' identifiers cited.

**Why this priority**: This is the core value—agents get trustworthy answers tied to real ticket records
instead of guessing or searching manually.

**Independent Test**: With tickets and comments seeded in known states, submit representative questions.
Each successful response includes an answer text and a non-empty list of cited ticket identifiers; every
cited identifier corresponds to a ticket whose stored fields were among those considered for that question.

**Acceptance Scenarios**:

1. **Given** stored tickets that mention payment failures and unrelated tickets, **When** an agent asks
   whether payment failures have been seen before, **Then** the response answers from relevant tickets
   only and cites at least one ticket identifier used in the answer.
2. **Given** ticket TKT-1001 exists with resolution notes and status RESOLVED or CLOSED, **When** an
   agent asks what the resolution was for TKT-1001, **Then** the response cites TKT-1001 and the answer
   reflects that ticket's stored resolution notes and related fields, not other tickets.
3. **Given** several resolved tickets about shipment tracking, **When** an agent asks for common causes
   of shipment tracking issues, **Then** the response summarizes patterns supported by cited tickets and
   does not introduce causes with no cited ticket.
4. **Given** a ticket and other tickets with overlapping category or description, **When** an agent asks
   for similar resolved tickets, **Then** cited tickets are among those retrieved for the question, are in
   status RESOLVED or CLOSED, and match the intent of similarity as stored in ticket data.
5. **Given** high-priority tickets in category BILLING and lower-priority or non-billing tickets, **When**
   an agent asks which high-priority tickets relate to payment, **Then** cited tickets satisfy the
   question using stored priority and category, with no fabricated ticket identifiers.
6. **Given** ticket TKT-1001 exists and no other ticket would score above the relevance cutoff for the
   question, **When** an agent asks about "tkt-1001" (any case), **Then** TKT-1001 is included in the
   retrieved set regardless of the cutoff, by deterministic rule, not by a separate interpretive step.

---

### User Story 2 - No relevant tickets (Priority: P1)

When nothing in the knowledge base meets the configured relevance cutoff for a question, the agent
receives an explicit no-relevant-tickets outcome and is not given an invented answer.

**Why this priority**: An honest empty result is safer than fabrication; it matches how agents must
treat uncertain knowledge.

**Independent Test**: Ask questions that cannot match any seeded ticket above the relevance cutoff.
Verify the no-relevant-tickets response shape, that no answer body implying facts is returned, and that
answer generation was not attempted.

**Acceptance Scenarios**:

1. **Given** tickets that do not relate to the topic asked, **When** an agent asks about that unrelated
   topic, **Then** the response is the no-relevant-tickets outcome and does not contain unsupported
   factual claims.
2. **Given** no tickets have been indexed yet, **When** an agent asks any question, **Then** the response
   is the no-relevant-tickets outcome.
3. **Given** retrieval is configured with a strict relevance cutoff, **When** the best match sits below
   that cutoff, **Then** the no-relevant-tickets outcome is returned rather than a low-confidence answer.

---

### User Story 3 - Knowledge stays current from refresh intents (Priority: P1)

Every durable refresh intent recorded by ticket lifecycle changes is processed so the assistant's view
of each ticket reflects that ticket's **current** stored state. Multiple pending intents for the same
ticket collapse into a single refresh. Failed refreshes are retried up to a configured limit, then marked
failed and surfaced—not silently dropped.

**Why this priority**: Grounded answers are wrong if indexed content lags behind ticket reality; feature
001 only records intent—this feature must consume it reliably.

**Independent Test**: Create or change tickets so refresh intents appear; process the queue; ask a question
that depends on the latest field values. Repeat with two rapid changes to one ticket and confirm one
effective refresh. Simulate refresh failure and confirm retries and final failed status.

**Acceptance Scenarios**:

1. **Given** a new ticket is saved and a refresh intent is recorded, **When** pending intents are
   processed, **Then** that ticket's knowledge includes title, description, category, priority, status,
   assignee, resolution notes, cancellation reason (when present), and comments as currently stored.
2. **Given** a ticket's description is edited after it was first indexed, **When** intents are processed,
   **Then** a subsequent question about that content reflects the latest description, not an older value.
3. **Given** two refresh intents for the same ticket are pending from consecutive saves, **When**
   processing runs, **Then** one successful refresh brings knowledge current for that ticket and does not
   leave stale data from processing only the first intent's timing.
4. **Given** indexing fails for a ticket, **When** retries up to the configured maximum are exhausted,
   **Then** the intent is marked failed, remains observable, and a later successful retry or full rebuild
   can clear the failure.
5. **Given** a ticket change was rolled back and no refresh intent was recorded (per feature 001),
   **When** intents are processed, **Then** no refresh runs for that rolled-back change.

---

### User Story 4 - Operator visibility and full rebuild (Priority: P2)

An operator can see how many ticket knowledge refreshes are pending or failed and can trigger a rebuild
that refreshes assistant knowledge for all tickets from stored ticket data.

**Why this priority**: Operations need to detect backlog and recover from widespread failure without
guessing whether the assistant is up to date.

**Independent Test**: Leave intents pending and failed; read operational status; trigger full rebuild;
confirm counts move toward zero and questions reflect all tickets after rebuild completes.

**Acceptance Scenarios**:

1. **Given** pending and failed refresh intents exist, **When** an operator requests refresh status,
   **Then** the response includes counts of pending and failed refreshes.
2. **Given** tickets exist in the database, **When** an operator requests a full knowledge rebuild,
   **Then** all tickets are reprocessed from stored data and knowledge afterward reflects current stored
   fields and comments.
3. **Given** a full rebuild completes successfully, **When** an agent asks a question about any ticket's
   current fields, **Then** answers align with stored ticket data.

---

### Edge Cases

- Question text is empty, only whitespace, or exceeds 1,000 characters → refused as invalid input with a
  clear error (distinct from no-relevant-tickets).
- Question references a ticket identifier that does not exist → may still retrieve other tickets if
  relevant; must not cite or fabricate the missing identifier as evidence.
- Ticket has no comments and empty resolution notes → answer may use other fields; must not invent
  comments or resolution text.
- Ticket is CANCELLED with a cancellation reason → knowledge and answers may use the reason when
  relevant to the question.
- Concurrent ticket changes and refresh processing → after processing settles, knowledge matches current
  stored state for each ticket.
- Cited ticket identifiers include one not in the retrieved set → citation-verification-failure outcome per
  clarification C1; no regeneration.
- Refresh processing is temporarily unavailable → ticket saves in feature 001 still succeed; intents
  accumulate and are processed when the consumer runs again.
- Full rebuild runs while new intents arrive → system eventually converges without losing intents
  recorded in feature 001.
- Questions that combine topic wording with attributes (priority, category, status) → answered using
  stored attributes of retrieved tickets, without requiring structured filter parameters from the agent.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST accept exactly one natural-language question per request and return
  exactly one response (grounded answer, no-relevant-tickets, invalid-question error, or citation
  verification failure as defined).
- **FR-002**: The system MUST retrieve candidate tickets from assistant knowledge using configurable
  retrieval breadth and a configurable relevance cutoff.
- **FR-003**: When no retrieved ticket meets the relevance cutoff, the system MUST return the
  no-relevant-tickets outcome and MUST NOT attempt answer generation at all.
- **FR-004**: When returning a grounded answer, the system MUST include the answer text and a list of
  cited ticket identifiers.
- **FR-005**: Every cited ticket identifier MUST appear in the set retrieved for that question; the
  system MUST verify this after the answer is produced; if verification fails, it MUST return the
  citation-verification-failure outcome (see clarification C1).
- **FR-006**: Answer generation MUST use only information derived from retrieved tickets for factual
  claims about tickets (title, description, category, priority, status, assignee, resolution notes,
  cancellation reason, comments).
- **FR-007**: The assistant MUST NOT create, modify, or cancel tickets, add comments, send
  notifications, or invoke follow-on automated steps.
- **FR-008**: The system MUST process pending refresh intents from feature 001, updating per-ticket
  knowledge from the ticket's current persisted state at processing time, not from the state when the
  intent was first created if that state is older.
- **FR-009**: When multiple pending refresh intents exist for the same ticket, processing MUST collapse
  them so one successful refresh brings that ticket's knowledge current.
- **FR-010**: Failed refreshes MUST be retried up to a configurable maximum attempt count; after that,
  intents MUST be marked failed and MUST remain visible for operators.
- **FR-011**: Operators MUST be able to obtain counts of pending and failed refresh intents.
- **FR-012**: Operators MUST be able to request a full rebuild of assistant knowledge from all stored
  tickets and their comments.
- **FR-013**: Retrieval breadth, relevance cutoff, refresh retry limits, and related tuning parameters
  MUST be adjustable without changing application source code.
- **FR-014**: Invalid questions MUST be rejected with errors distinguishable from the no-relevant-tickets
  outcome and from success.
- **FR-015**: Questions that imply filters on stored attributes (for example high priority, billing or
  payment category, resolved status) MUST be answerable from natural language alone using stored ticket
  fields among retrieved tickets, without a separate agent-facing filter API in this feature.
- **FR-016**: If the question text contains a ticket identifier matching the form TKT-<n>
  (case-insensitive) and that ticket exists, that ticket MUST be included in the retrieved set for that
  question regardless of the relevance cutoff; this inclusion MUST be applied by deterministic rule, not
  by an AI or interpretive step.

### Key Entities *(include if feature involves data)*

- **Assistant knowledge (per ticket)**: Searchable representation derived from a ticket's current stored
  fields and comments, used for retrieval—not a duplicate source of truth for ticket lifecycle.
- **Refresh intent (consumed)**: Durable record from feature 001; this feature processes, retries, and
  completes or fails them.
- **Question request**: A single natural-language question submitted by an agent.
- **Grounded answer**: Response with answer text and cited ticket identifiers passing subset verification.
- **No-relevant-tickets outcome**: Standard response when retrieval does not meet the relevance cutoff.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In acceptance testing, 100% of reviewed grounded answers cite only ticket identifiers
  retrieved for that question.
- **SC-002**: In acceptance testing, 100% of reviewed questions with no ticket above the relevance
  cutoff receive the no-relevant-tickets outcome with no fact-like answer body.
- **SC-003**: After each reviewed ticket save that records a refresh intent in feature 001, after the
  refresh processor runs, a question about the changed field reflects the new value.
- **SC-004**: Operators can determine pending and failed refresh counts in one request.
- **SC-005**: A full rebuild from stored tickets succeeds for the entire corpus in acceptance testing,
  after which sample questions across categories match stored ticket data.

## Assumptions

- Feature 001 is available: tickets, comments, and refresh intents persist and survive restart.
- Ticket identifiers use the form from feature 001 (for example TKT-1001).
- Categories are the fixed set from feature 001: BILLING, TECHNICAL, ACCOUNT, SHIPPING, OTHER.
- User interface, authentication, and multi-turn chat are out of scope; clients call backend
  capabilities defined during planning.
- Carry-over obligations from `docs/carry-over-002-ticket-assistant.md` are satisfied by this spec's
  freshness, grounding, configuration, and operator stories unless explicitly deferred to planning.
- Questions that imply filters on stored attributes (for example priority, category, status) are answered
  using only the top retrieved tickets up to the configured retrieval breadth; lower-ranked tickets that
  would match those attributes may be missed.

## Dependencies

- **Feature 001**: Refresh intent on create, field edit, comment, and every status move including
  cancellation; ticket and comment persistence.

## Out of Scope

- User interface for asking questions or viewing answers.
- Multi-turn conversation memory.
- Authentication, roles, and per-agent personalization.
- The assistant modifying tickets or triggering workflows.
- Attachments, notifications, and knowledge outside stored tickets.