# Backend API contract references (003 UI)

The UI does **not** define REST shapes. It consumes:

| Feature | OpenAPI | Base path |
|---------|---------|-----------|
| 001 Ticket lifecycle | [../../001-ticket-lifecycle/contracts/openapi.yaml](../../001-ticket-lifecycle/contracts/openapi.yaml) | `/api/tickets` |
| 002 Assistant | [../../002-ticket-assistant/contracts/openapi.yaml](../../002-ticket-assistant/contracts/openapi.yaml) | `/api/ai` |

## Client module

- `frontend/src/api/client.ts` — all `fetch` calls
- Types from `openapi-typescript` output (`schema-001.ts`, `schema-002.ts`)

## Ticket response (001)

Success bodies use schema `Ticket`. Required field **`allowedTransitions`** drives move controls (FR-007).

Errors: `application/problem+json` with `errorCode`.
