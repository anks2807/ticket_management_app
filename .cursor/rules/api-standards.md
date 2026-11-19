---
description: REST API standards (contract, errors, naming)
globs: backend/src/main/java/**/web/**, specs/**/contracts/**
alwaysApply: false
---

# API standards

## Resources
- Base path `/api`. Plural nouns: `/api/tickets`, `/api/tickets/{id}/comments`.
- `{id}` is the human identifier `TKT-<n>`.
- Status changes ONLY via `POST /api/tickets/{id}/transitions`. `PATCH /api/tickets/{id}` never changes status.
- PATCH uses JSON Merge Patch semantics (RFC 7396): absent = unchanged, `null` = clear. Only `assignee` is clearable; `null` on a required field is 400.
- Enum values on the wire are enum names in upper case: `OPEN`, `IN_PROGRESS`, `LOW`, `BILLING`, ...
- Lists: `GET /api/tickets?q=&status=&page=` — page size fixed at 20, sorted by `updatedAt` desc; response includes `page`, `size`, `totalElements`, `totalPages`.
- Unknown JSON fields in request bodies are rejected (400).
- Timestamps are ISO-8601 UTC (`2026-11-19T10:15:30Z`).

## Errors (RFC 9457 problem details, `application/problem+json`)
Every error body: `type`, `title`, `status`, `detail`, `errorCode`, and when relevant `errors[]` (`field`, `message`).

| Status | When | errorCode |
|---|---|---|
| 400 | Malformed JSON, unknown field, field validation, unrecognised enum value | `VALIDATION_FAILED` / `MALFORMED_REQUEST` |
| 404 | Ticket does not exist | `TICKET_NOT_FOUND` |
| 409 | Disallowed status transition (body adds `currentStatus`, `allowedTransitions`) | `INVALID_TRANSITION` |
| 409 | Edit on CLOSED/CANCELLED ticket | `TICKET_FROZEN` |
| 409 | Edit sets resolution notes on a ticket that has none | `RESOLUTION_NOTES_NOT_EDITABLE` |
| 503 | Row lock wait exceeded (`Retry-After` header) | `TICKET_BUSY` |
| 500 | Anything unexpected — generic message, no stack trace | `INTERNAL_ERROR` |

- Check order: 400 → 404 → 409.
- Malformed input and disallowed transitions never share a status code or errorCode (constitution II).

## AI endpoint (feature 002)
- `POST /api/ai/ask` with `{ "question": "..." }`.
- Response always includes `answer`, `sources[]` (ticket IDs with title/status), and `grounded` (boolean). No-match returns `grounded: false`, empty `sources`, and the fixed no-relevant-tickets message.
