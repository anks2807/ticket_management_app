# Data Model: Ticket Lifecycle Management

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

Three tables: `ticket`, `comment`, `ticket_refresh_intent`. One aggregate: `Ticket` (owns its `Comment`s). The refresh intent is an outbox row written by the application layer alongside a ticket change, not part of the aggregate's state.

## Entities

### Ticket (aggregate root)

| Field | Type | Rules | Spec |
|-------|------|-------|------|
| `id` | bigint, from `ticket_id_seq` (start 1001, increment 1) | Immutable. External form `TKT-<id>`. Never reused; gaps allowed. | FR-002 |
| `title` | text ≤ 200 | Required, non-blank | FR-001, Assumptions |
| `description` | text ≤ 5,000 | Required, non-blank | FR-001 |
| `priority` | `Priority` | One of `LOW`, `MEDIUM`, `HIGH`, `URGENT` | Assumptions |
| `category` | `Category` | One of `BILLING`, `TECHNICAL`, `ACCOUNT`, `SHIPPING`, `OTHER`; required | FR-001, FR-011 |
| `assignee` | text ≤ 100, nullable | Optional. On edit, an explicit null clears it and a blank string does not. | FR-001 |
| `status` | `TicketStatus` | Starts `OPEN`; changes only through a lifecycle move | FR-002, FR-009 |
| `resolutionNotes` | text ≤ 5,000, nullable | Introduced only by the move to `RESOLVED` (required, non-blank). Afterwards editable but never cleared. Frozen when `CLOSED`/`CANCELLED`. | FR-010, FR-011 |
| `cancellationReason` | text ≤ 5,000, nullable | Introduced only by the move to `CANCELLED` (required, non-blank). Never changed afterwards. | FR-010 |
| `createdAt` | instant (µs) | Set at creation; immutable | FR-002 |
| `updatedAt` | instant (µs) | Equal to `createdAt` at creation. Bumped by every accepted edit, comment, and move. | FR-002, FR-011, FR-012 |
| `comments` | list of `Comment` | Append-only, ordered by `id` ascending | FR-012 |

**Aggregate behaviors** (the only mutators; each takes `now`. `edit`, `moveTo`, and `addComment` are called after the row lock is held. `Ticket.create` does not take a row lock, because no row exists yet):

| Method | Preconditions (else exception) | Effect |
|--------|-------------------------------|--------|
| `Ticket.create(title, description, priority, category, assignee, now)` | none | status `OPEN`, `createdAt = updatedAt = now` |
| `edit(changes, now)` | not frozen → `TicketFrozenException`; if `resolutionNotes` supplied the ticket must already have notes → `ResolutionNotesNotEditableException` | applies supplied fields, `updatedAt = now` |
| `moveTo(target, notesOrReason, now)` | `status.canMoveTo(target)` → else `InvalidTransitionException(current, allowed)`; `RESOLVED` needs notes, `CANCELLED` needs a reason | sets status (and notes/reason), `updatedAt = now` |
| `addComment(author, text, now)` | none (any status) | appends a `Comment`, `updatedAt = now` |

Domain methods assume the boundary already validated field shape (non-blank, length). They still guard their own invariants (for example notes on a move to `RESOLVED`) and treat a violation as a programming error (`IllegalArgumentException`), which would surface as a 500, never as a client error.

### Comment

| Field | Type | Rules |
|-------|------|-------|
| `id` | bigint identity | Ordering key within a ticket |
| `ticketId` | FK → `ticket.id` | Belongs to exactly one ticket |
| `author` | text ≤ 100 | Required, non-blank |
| `text` | text ≤ 5,000 (column `body`) | Required, non-blank |
| `createdAt` | instant (µs) | Equal to the ticket's `updatedAt` produced by the same change |

Comments are never updated or deleted. There is no method that does either.

### Refresh intent (outbox row)

| Field | Type | Rules |
|-------|------|-------|
| `id` | bigint identity | Ordered consumption key for feature 002 |
| `ticketId` | FK → `ticket.id` | The ticket whose knowledge must be refreshed |
| `changeType` | `ChangeType` | `TICKET_CREATED`, `FIELDS_UPDATED`, `COMMENT_ADDED`, `STATUS_CHANGED` |
| `createdAt` | instant (µs) | Same moment as the change |

Written in the same transaction as the change. Never updated or deleted in this feature. Nothing in this feature reads it other than tests. Processing state, attempts, and payload columns are intentionally absent and are added by feature 002's migration.

## State machine

Defined once, as data on the `TicketStatus` enum. `Ticket.moveTo` consults only this definition.

| From \ To | OPEN | IN_PROGRESS | RESOLVED | CLOSED | CANCELLED |
|-----------|:----:|:-----------:|:--------:|:------:|:---------:|
| **OPEN** | refused (same status) | allowed | refused | refused | allowed |
| **IN_PROGRESS** | refused | refused (same status) | allowed | refused | allowed |
| **RESOLVED** | refused | refused | refused (same status) | allowed | refused |
| **CLOSED** | refused | refused | refused | refused (same status) | refused |
| **CANCELLED** | refused | refused | refused | refused | refused (same status) |

`allowedTransitions` returned on a refusal:

| Current status | `allowedTransitions` |
|----------------|----------------------|
| `OPEN` | `IN_PROGRESS`, `CANCELLED` |
| `IN_PROGRESS` | `RESOLVED`, `CANCELLED` |
| `RESOLVED` | `CLOSED` |
| `CLOSED` | (empty) |
| `CANCELLED` | (empty) |

Five allowed pairs, twenty refused pairs. `TransitionMatrixIT` enumerates all 25.

Field-edit and comment rules by status:

| Status | Field edit | Comment | Notes on edit |
|--------|-----------|---------|---------------|
| `OPEN`, `IN_PROGRESS` | allowed | allowed | refused with `RESOLUTION_NOTES_NOT_EDITABLE` (ticket has no notes) |
| `RESOLVED` | allowed | allowed | allowed (replace, non-blank) |
| `CLOSED`, `CANCELLED` | refused `TICKET_FROZEN` | allowed | refused `TICKET_FROZEN` |

## Schema (reference for migrations; exact SQL belongs to the implementation)

```text
V1__create_ticket.sql
  SEQUENCE ticket_id_seq START 1001 INCREMENT 1
  TABLE ticket (
    id                  bigint        PRIMARY KEY,           -- value from ticket_id_seq (Hibernate, allocationSize 1)
    title               varchar(200)  NOT NULL,
    description         varchar(5000) NOT NULL,
    priority            varchar(10)   NOT NULL,
    category            varchar(10)   NOT NULL,
    assignee            varchar(100),
    status              varchar(12)   NOT NULL,
    resolution_notes    varchar(5000),
    cancellation_reason varchar(5000),
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL
  )
  CHECKs (shape only, never transitions):
    priority IN ('LOW','MEDIUM','HIGH','URGENT')
    category IN ('BILLING','TECHNICAL','ACCOUNT','SHIPPING','OTHER')
    status   IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED','CANCELLED')
    btrim(title) <> '' AND btrim(description) <> ''
    assignee IS NULL OR btrim(assignee) <> ''
    resolution_notes IS NULL  OR (status IN ('RESOLVED','CLOSED') AND btrim(resolution_notes) <> '')
    (status = 'CANCELLED') = (cancellation_reason IS NOT NULL)
    cancellation_reason IS NULL OR btrim(cancellation_reason) <> ''
    updated_at >= created_at
  INDEX ticket_updated_idx        ON ticket (updated_at DESC, id DESC)
  INDEX ticket_status_updated_idx ON ticket (status, updated_at DESC, id DESC)

V2__create_comment.sql
  TABLE comment (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id  bigint        NOT NULL REFERENCES ticket (id),
    author     varchar(100)  NOT NULL CHECK (btrim(author) <> ''),
    body       varchar(5000) NOT NULL CHECK (btrim(body) <> ''),
    created_at timestamptz   NOT NULL
  )
  INDEX comment_ticket_idx ON comment (ticket_id, id)

V3__create_ticket_refresh_intent.sql
  TABLE ticket_refresh_intent (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id   bigint      NOT NULL REFERENCES ticket (id),
    change_type varchar(20) NOT NULL
                CHECK (change_type IN ('TICKET_CREATED','FIELDS_UPDATED','COMMENT_ADDED','STATUS_CHANGED')),
    created_at  timestamptz NOT NULL
  )

db/seed/R__seed_dev_tickets.sql   (dev profile only; repeatable)
  Guarded by WHERE NOT EXISTS (SELECT 1 FROM ticket).
  Inserts tickets in every status (ids from ticket_id_seq), their comments,
  and one TICKET_CREATED intent per seeded ticket.
```

Notes:

- A `RESOLVED`/`CLOSED` ticket always has notes (the move to `RESOLVED` requires them), so the constraint `resolution_notes IS NULL OR status IN (RESOLVED, CLOSED)` is consistent with the lifecycle. The constraint does not force notes to exist for `RESOLVED`, so the domain remains the sole authority on transitions.
- A `CANCELLED` ticket has a reason and no resolution notes (notes can only be set after `RESOLVED`).
- The checks are a backstop for data shape. Removing one never changes lifecycle behavior; it only lowers protection against a bug.
- The unique `id` is the sequence value. Hibernate must use `allocationSize = 1` so each insert calls `nextval` and the sequence's increment of 1 matches.
- Foreign keys are safe because no delete exists in this feature.

## Read models (application layer)

Returned from inside the transaction so lazy collections are never touched after it closes (`open-in-view` is off).

| Model | Fields |
|-------|--------|
| `TicketDetails` | `id` (`TKT-…`), `title`, `description`, `priority`, `category`, `assignee`, `status`, `resolutionNotes`, `cancellationReason`, `createdAt`, `updatedAt`, `comments[]` (`author`, `text`, `createdAt`) |
| `TicketSummary` | `id`, `title`, `priority`, `category`, `assignee`, `status`, `createdAt`, `updatedAt` |
| `TicketPage` | `items[]` (`TicketSummary`), `page`, `pageSize` (20), `totalItems`, `totalPages` |
| `CommentView` | `author`, `text`, `createdAt` |

## Traceability

| Spec requirement | Where it is realized |
|------------------|----------------------|
| FR-001, FR-002, SC-008 | `Ticket.create`, `ticket_id_seq`, `created_at`/`updated_at` |
| FR-003 | `TicketDetails` read model |
| FR-004, SC-006 | PostgreSQL tables, Flyway; restart test |
| FR-005, SC-003 | Boundary validation (web); DB checks as backstop |
| FR-006, FR-007, SC-004 | Distinct `errorCode`/status (research R-05) |
| FR-008 | Separate endpoints; unknown-field rejection (R-09) |
| FR-009, SC-002 | `TicketStatus` transition table; matrix test |
| FR-010 | `moveTo` (notes/reason), `edit` (no clearing), checks |
| FR-011 | `Ticket.edit`, frozen rule |
| FR-012 | `Ticket.addComment` |
| FR-013, FR-014, FR-015, SC-007 | Search specification (R-08) |
| FR-016, SC-005 | `ticket_refresh_intent`; same-transaction write (R-10) |
