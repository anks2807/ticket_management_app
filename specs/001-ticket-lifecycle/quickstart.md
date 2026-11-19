# Quickstart: Ticket Lifecycle Management

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Contract**: [contracts/openapi.yaml](./contracts/openapi.yaml) | **Data model**: [data-model.md](./data-model.md)

A run-and-validate guide for the finished feature. It describes what to run and what outcome proves each requirement. Implementation code is not included here; it lives in the tasks and the source tree.

> Status: the code does not exist yet. These steps become runnable once `/speckit-tasks` and `/speckit-implement` have been completed.

## Prerequisites

| Need | Detail |
|------|--------|
| JDK 21 | The build enforces `[21,22)`. A JDK 25 alone fails the enforcer rule by design. |
| Docker | Running daemon, for `docker compose` and Testcontainers |
| Maven | Use the wrapper (`backend/mvnw`), which pins Maven 3.9.12. No global Maven is needed. |
| Free ports | `5432` (PostgreSQL) and `8080` (service) |

## One-time setup

1. Copy the environment template and set a local password of your choice (never commit `.env`):

   ```bash
   cp .env.example .env
   # edit .env: set POSTGRES_PASSWORD (placeholder values only in .env.example)
   ```

2. Start the database. This is the same image tag the tests use (`pgvector/pgvector:0.8.7-pg17`):

   ```bash
   docker compose up -d db
   ```

   The tag appears in `docker-compose.yml` and in the Testcontainers base class. If you change one, change both and update the pin in [plan.md](./plan.md).

## Run the service (dev profile with sample data)

```bash
cd backend
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/tickets
export SPRING_DATASOURCE_USERNAME=<your db user>
export SPRING_DATASOURCE_PASSWORD=<the password from .env>
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Expected: Flyway applies `V1`–`V3`, then the repeatable seed (dev profile only) inserts sample tickets in every status with comments and one refresh intent each. The service listens on `http://localhost:8080`.

Without `-Dspring-boot.run.profiles=dev`, schema migrations run but no sample data is inserted.

## Run the automated checks

```bash
cd backend
./mvnw verify
```

Expected: compilation succeeds and every test passes (unit tests, then integration tests against a Testcontainers PostgreSQL). Test groups and what each proves are mapped in the plan's Test Strategy.

Test-first evidence: the transition unit test and `TransitionMatrixIT` were committed and recorded as failing before the domain logic existed (see the task log).

## Manual validation scenarios

Use any HTTP client. Examples use `curl`. Replace `TKT-<n>` with the identifier returned by the create call.

### V1. Create and view (Story 1)

| Step | Request | Expected |
|------|---------|----------|
| Create | `POST /api/tickets` with `title`, `description`, `priority` (`LOW`/`MEDIUM`/`HIGH`/`URGENT`), `category` (`BILLING`/`TECHNICAL`/`ACCOUNT`/`SHIPPING`/`OTHER`), optional `assignee` | `201`, `id` like `TKT-1001` (the first ever), `status` `OPEN`, `createdAt` equal to `updatedAt`, empty `comments`, `Location` header |
| View | `GET /api/tickets/TKT-<n>` | `200`, same details, `resolutionNotes` and `cancellationReason` null |
| No assignee | Create without `assignee` | `201`, `assignee` null |
| Restart | Stop and restart the service (database keeps running), then view again | Same ticket, same details |
| Persistence of intent | See V8 | One `TICKET_CREATED` row for the ticket, still present after restart |

### V2. Lifecycle moves (Story 2)

All moves use `POST /api/tickets/TKT-<n>/transitions`.

| From | Body | Expected |
|------|------|----------|
| OPEN | `{"status":"IN_PROGRESS"}` | `200`, status `IN_PROGRESS`, `updatedAt` changed, other fields unchanged |
| IN_PROGRESS | `{"status":"RESOLVED","resolutionNotes":"..."}` | `200`, status `RESOLVED`, notes saved |
| RESOLVED | `{"status":"CLOSED"}` | `200`, status `CLOSED` |
| OPEN or IN_PROGRESS | `{"status":"CANCELLED","cancellationReason":"..."}` | `200`, status `CANCELLED`, reason saved |
| any | a status the ticket cannot move to, including its current status | `409`, `errorCode` `INVALID_TRANSITION`, body has `currentStatus` and `allowedTransitions`; ticket unchanged |
| CLOSED / CANCELLED | any status | `409`, `allowedTransitions` is `[]` |
| IN_PROGRESS | `{"status":"RESOLVED"}` (no notes) | `400`, `errorCode` `VALIDATION_FAILED`, `errors[]` names `resolutionNotes`; status stays `IN_PROGRESS` |
| OPEN | `{"status":"CANCELLED"}` or blank reason | `400`, `errors[]` names `cancellationReason` |
| any | `{"status":"DONE"}` | `400` (not `409`), `errors[]` names `status` and lists the allowed values |
| unknown ticket | any valid body | `404`, `errorCode` `TICKET_NOT_FOUND` |

The three outcomes (400, 409, 404) have different status codes and different `errorCode` values (SC-004).

### V3. Edit (Story 3)

`PATCH /api/tickets/TKT-<n>`:

| Case | Expected |
|------|----------|
| Change `title`, `description`, `priority`, `assignee`, or `category` | `200`, only those fields and `updatedAt` change; status and comments unchanged |
| `{"assignee": null}` | `200`, assignee cleared. A blank assignee string does not clear it |
| Null `title`, `description`, `priority`, or `category` | `400` `VALIDATION_FAILED`; ticket unchanged |
| Body includes `status`, `id`, or `createdAt` | `400` `MALFORMED_REQUEST` (unknown field); ticket unchanged |
| Empty body `{}` | `400` `VALIDATION_FAILED` |
| Category `Misc` | `400` `VALIDATION_FAILED`, `errors[]` names `category` and lists `BILLING`, `TECHNICAL`, `ACCOUNT`, `SHIPPING`, `OTHER` |
| Two bad fields at once (blank `title`, bad `priority`) | `400`, `errors[]` contains **both** fields |
| `resolutionNotes` while the ticket has none | `409` `RESOLUTION_NOTES_NOT_EDITABLE` |
| `resolutionNotes` on a RESOLVED ticket | `200`, replaced; blank notes give `400` |
| Any field on a CLOSED or CANCELLED ticket | `409` `TICKET_FROZEN`; ticket unchanged |

### V4. Comments (Story 4)

`POST /api/tickets/TKT-<n>/comments` with `author` and `text`:

| Case | Expected |
|------|----------|
| Any ticket, including CLOSED and CANCELLED | `201`; the comment shows author, text, and time; ticket `updatedAt` changes |
| Add a second comment | Both appear on `GET`, oldest first, the first unchanged |
| Blank `author` or `text` | `400` `VALIDATION_FAILED` |

### V5. List and search (Story 5)

`GET /api/tickets?q=<keyword>&status=<STATUS>&page=<n>`:

| Case | Expected |
|------|----------|
| No parameters | First 20 tickets, newest `updatedAt` first; `pageSize` 20, `totalItems`, `totalPages` present |
| `page=1` | Next portion |
| `q=<word from one title>` (any case) | Only matching tickets |
| `q=TKT-1001`, `q=tkt-1001`, `q=%20TKT-1001%20` | That ticket |
| `q=1001` | The ticket does not match by identifier (it can still match through text) |
| `status=OPEN` | Only OPEN tickets |
| `q` and `status` together | Results match both |
| `q` with no matches | `200` with empty `items` |
| `status=DONE` | `400`, `errors[]` names `status` |
| `q=100%` | Matches the literal text `100%`, not "100 followed by anything" |

### V6. Concurrency (Story 2, scenario 10)

Fire two simultaneous `OPEN → IN_PROGRESS` requests at the same ticket (for example two terminals using a shared start signal). Expected: exactly one `200`, one `409` `INVALID_TRANSITION` whose `currentStatus` is `IN_PROGRESS`, and exactly one `STATUS_CHANGED` intent for that move. The automated version lives in `ConcurrencyIT` and repeats the race many times, plus a deterministic variant that holds the row lock.

If the ticket is held by another in-progress change longer than the configured wait, the waiting request returns 503 `TICKET_BUSY` with a `Retry-After` header (guidance to retry later). The ticket is unchanged and no refresh intent is recorded. This is a fourth outcome, distinct from a badly formed request, a disallowed move, and a missing ticket (spec edge case).

### V7. Frozen-ticket race

Fire a field edit and a move to `CLOSED` (from `RESOLVED`) simultaneously. Expected: either the edit lands first (both succeed, two intents) or the close lands first (edit gets `409` `TICKET_FROZEN`, one intent). A frozen ticket is never edited.

### V8. Refresh intents (FR-016)

Inspect the table directly:

```bash
docker compose exec db psql -U <your db user> -d tickets \
  -c "select id, ticket_id, change_type, created_at from ticket_refresh_intent order by id"
```

| Action | Expected rows for the ticket |
|--------|------------------------------|
| Create | `TICKET_CREATED` |
| Accepted edit | `FIELDS_UPDATED` |
| Accepted comment (any status) | `COMMENT_ADDED` |
| Accepted move, including to `CANCELLED` | `STATUS_CHANGED` |
| Refused request (400, 404, 409) | no new row |

Rollback behavior (the intent write failing rolls back the ticket change, and vice versa) is proven only by `RefreshIntentIT`, which installs temporary database triggers; it is not a manual step.

## Troubleshooting

| Symptom | Cause |
|---------|-------|
| Build fails with an enforcer message about Java | The active JDK is not 21 |
| `POSTGRES_PASSWORD` error from compose | `.env` missing; copy `.env.example` and set a value |
| Tests fail to start containers | Docker is not running |
| Flyway "validate failed" after pulling | A migration changed after being applied locally; recreate the local database (`docker compose down -v`) |
