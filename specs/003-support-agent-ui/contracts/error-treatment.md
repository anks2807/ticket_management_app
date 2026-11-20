# Problem details → UI treatment (003)

Maps backend `errorCode` (RFC 9457 problem+json) to UI behavior. Implemented by `parseProblem` in the frontend.

| errorCode | HTTP | UI treatment |
|-----------|------|--------------|
| `VALIDATION_FAILED` | 400 | Field errors: `errors[]` → message under matching input |
| `MALFORMED_REQUEST` | 400 | Same as validation when `errors[]` present; else generic banner |
| `TICKET_NOT_FOUND` | 404 | Not-found page / inline message |
| `INVALID_TRANSITION` | 409 | Banner: message + `currentStatus` + list `allowedTransitions` |
| `TICKET_FROZEN` | 409 | Explain ticket frozen; no successful field edit |
| `RESOLUTION_NOTES_NOT_EDITABLE` | 409 | Explain notes cannot be set via edit |
| `TICKET_BUSY` | 503 | Retry message; show `Retry-After` when header present |
| `INVALID_QUESTION` | 400 | Assistant invalid-input (distinct from no-relevant-tickets) |
| (other) | * | Generic fallback: `title` + `detail` |

## Non-problem success paths

| Context | Shape | UI |
|---------|-------|-----|
| Ask | `AskResponse.outcome` = `GROUNDED` | Answer + source links |
| Ask | `NO_RELEVANT_TICKETS` | Dedicated empty message |
| Ask | `CITATION_VERIFICATION_FAILED` | Trust failure message |

## Assistant vs ticket errors

- No relevant tickets: HTTP **200**, not problem+json
- Invalid question: HTTP **400**, problem+json `INVALID_QUESTION`
