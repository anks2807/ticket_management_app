# Data Model: Support Agent Web UI (003)

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Client view models only. Authoritative data from backend JSON.

## API shapes (generated)

| Schema | UI use |
|--------|--------|
| `Ticket` | Detail; includes `allowedTransitions` |
| `TicketPage` | List paging |
| `CreateTicketRequest` / `EditTicketRequest` / `TransitionRequest` / `AddCommentRequest` | Forms |
| `AskRequest` / `AskResponse` | Assistant |

## Client state

| State | Owner | Notes |
|-------|-------|-------|
| `ListQuery` | List page | `q`, `status?`, `page` |
| `CreateFormDraft` | Create page | Retain on validation error |
| `DetailDraft` | Detail page | Reload on success |
| `TransitionDraft` | Move dialog | Notes/reason when required |
| `AssistantSession` | App shell | Last question + outcome across routes |

## `ParseProblemResult` (client)

Variants: `fieldErrors`, `invalidTransition`, `frozen`, `notFound`, `busy`, `generic`. See [error-treatment.md](./contracts/error-treatment.md).

## Assistant outcomes (200)

`GROUNDED` | `NO_RELEVANT_TICKETS` | `CITATION_VERIFICATION_FAILED` — each distinct UI per spec FR-012.
