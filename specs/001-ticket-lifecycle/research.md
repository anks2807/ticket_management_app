# Research: Ticket Lifecycle Management

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-11-19

The plan's Technical Context had no `NEEDS CLARIFICATION` markers: the brief fixed versions, architecture, API shape, and tests. This document records the remaining design decisions, plus facts checked against the pinned versions.

## Verified facts (checked 2026-11-19)

| Fact | How checked |
|------|-------------|
| `spring-boot-dependencies:4.1.2`, `spring-ai-bom:2.0.2`, and `apache-maven:3.9.12` exist on Maven Central | HTTP 200 on the POM/ZIP URLs |
| Boot 4.1.2 BOM manages Hibernate 7.4.13.Final, Flyway 12.4.0, Jackson 3.1.7, Testcontainers 2.0.5, JUnit 6.0.3, PostgreSQL JDBC 42.7.14 | Read from `spring-boot-dependencies-4.1.2.pom` |
| Boot 4 starter names: `spring-boot-starter-webmvc`, `-data-jpa`, `-flyway`, `-validation`, `-webmvc-test`, `-test`, and `spring-boot-testcontainers` exist | Same BOM. Note that Boot 4 splits Flyway out of the core, so `flyway-core` alone no longer auto-configures; the Flyway starter is needed. |
| Testcontainers 2.x module artifacts are `testcontainers-postgresql` and `testcontainers-junit-jupiter` (1.x used `postgresql` and `junit-jupiter`) | POMs exist under 2.0.5 |
| `pgvector/pgvector:0.8.7-pg17` exists | Docker Hub tags API |
| Local JDK is 25 only; no JDK 21 found | `java -version`, `/usr/libexec/java_home -V` |

**Not verified** (to be confirmed by the first implementation task, with the fallback noted): the exact Jackson 3 property name for failing on unknown properties (R-09), and the Boot 4 MockMvc test class names (R-12).

---

## R-01 Persistence technology

- **Decision**: Spring Data JPA (Hibernate) with entities that are the domain aggregate. No separate persistence model.
- **Rationale**: The aggregate (`Ticket` + `Comment`s) maps directly to two tables. `PESSIMISTIC_WRITE` locking, paging, and the search `Specification` are built in. A second model with a mapper would add a layer no requirement needs (IX).
- **Alternatives**: Spring JDBC/jOOQ (fewer annotations in the domain, but hand-written paging, search, and aggregate loading); separate pure-domain classes plus JPA entities (clean, but double the classes and a mapper).
- **Guardrails**: `spring.jpa.open-in-view=false`; `spring.jpa.hibernate.ddl-auto=validate` (Flyway owns the schema); entities have no public setters, so mutation happens only through aggregate methods.

## R-02 Identifier generation

- **Decision**: `ticket.id` is a `bigint` fed by `CREATE SEQUENCE ticket_id_seq START 1001 INCREMENT 1`. Hibernate uses `@SequenceGenerator(allocationSize = 1)`. The external form `TKT-<id>` is computed, not stored.
- **Rationale**: Satisfies FR-002 (unique, immutable, starts at 1001, never reused, gaps allowed). Numeric ordering gives a deterministic tie-break for paging (R-08). A stored text key would duplicate the id.
- **Alternatives**: Store `TKT-1001` text with a unique constraint (redundant, harder to sort); `IDENTITY` column (cannot start at 1001 as cleanly, and the explicit sequence makes the seed script's `nextval` use obvious).
- **Path handling**: `/api/tickets/{ticketId}` accepts `TKT-<n>`. Anything that is not canonical `TKT-<n>` is treated as an unknown ticket (404 `TICKET_NOT_FOUND`), because such a ticket does not exist.

## R-03 Schema, migrations, and seed data

- **Decision**: Flyway SQL migrations in `classpath:db/migration`: `V1__create_ticket.sql` (sequence + table + checks + indexes), `V2__create_comment.sql`, `V3__create_ticket_refresh_intent.sql`. Seed data is a **repeatable** migration `R__seed_dev_tickets.sql` in `classpath:db/seed`, loaded only when `application-dev.yml` adds that location.
- **Rationale**: A repeatable seed never collides with future versioned migrations. A versioned seed (e.g. `V900`) would make a later `V4` an out-of-order migration on any dev database that already applied it. The seed is guarded with `WHERE NOT EXISTS (SELECT 1 FROM ticket)` so it only populates an empty database. Tests and production never load `db/seed`.
- **Alternatives**: Versioned seed in the main location (leaks into production, violates "seed data dev-only"); Spring `data.sql` (not a versioned migration, violates the constitution's migration rule); Testcontainers-only fixtures (does not give developers sample data).
- **Constraints as backstop** (data shape only, never transitions): `CHECK` on enum value sets; `btrim(title) <> ''` and similar for required text; `resolution_notes` present only when status is `RESOLVED` or `CLOSED`; `cancellation_reason` present if and only if status is `CANCELLED`; `updated_at >= created_at`. Column lengths match the spec's limits (200 / 5000 / 100).
- **No `CREATE EXTENSION vector`** here. That belongs to feature 002 (YAGNI), although the image is ready for it.

## R-04 Concurrency and locking

- **Decision**: Edit, transition, and comment each open a transaction and call `TicketRepository.findByIdForUpdate(id)` (`@Lock(PESSIMISTIC_WRITE)`, which emits `SELECT … FOR UPDATE`) before evaluating anything. The domain method then runs against the loaded state, and the clock value for `updatedAt`/intent timestamps is read **after** the lock is held, so `updatedAt` is monotonic per ticket. Create takes no row lock because the row does not exist yet.
- **Rationale**: Under PostgreSQL's default READ COMMITTED isolation, a `FOR UPDATE` that waited re-reads the row's latest committed version once the lock frees. The loser therefore sees the winner's change and the domain refuses it (a disallowed move, or `TICKET_FROZEN`). This is exactly spec Story 2 scenario 10 and the "last-write-wins only between edits of an editable ticket" rule. Reads (`GET`) take no lock and see committed state.
- **D5 (comments also lock)**: the brief named moves and edits. A comment bumps the ticket's `updatedAt` (FR-012). Without the lock, a comment could commit an older `updatedAt` after a concurrent move. The cost is one short lock per comment, and there is a single lock per transaction, so no deadlock is possible.
- **D11 (bounded wait)**: the JDBC URL sets `lock_timeout` (5 s default, overridable by configuration). If a ticket is held by another in-progress change longer than that wait, the handler returns 503 `TICKET_BUSY` with a `Retry-After` header (guidance to retry later). The ticket is unchanged and no refresh intent is recorded. This is the spec edge case: a fourth outcome, distinct from badly formed input, a disallowed move, and a missing ticket. Hibernate's own lock-timeout hint is not relied on, because PostgreSQL only supports "wait forever", `NOWAIT`, or `SKIP LOCKED` through that hint.
- **Alternatives**: Optimistic `@Version` (loser gets an exception and must retry; the brief chose pessimistic, and retry logic is extra code); `SERIALIZABLE` isolation (retry storms, same retry logic); advisory locks (non-standard, and invisible to a plain `SELECT FOR UPDATE` in tests).
- **Rule for contributors**: no code path may edit, transition, or comment on a ticket without having loaded it via `findByIdForUpdate`. Create does not take a row lock. The repository exposes `findByIdForUpdate` for those three writes and a plain `findById` for read use cases in the query service.

## R-05 Error model

- **Decision**: One handler (`@RestControllerAdvice`, extending `ResponseEntityExceptionHandler` so framework errors are covered too) produces `application/problem+json` (RFC 9457). `type` stays `about:blank`. The stable discriminator is the `errorCode` extension member.

| HTTP | `errorCode` | When | Extra members |
|------|-------------|------|---------------|
| 400 | `MALFORMED_REQUEST` | Body not parseable, wrong JSON type, unknown field, bad query parameter type (`page=abc`) | `errors[]` where a field is identifiable |
| 400 | `VALIDATION_FAILED` | Field validation: blank, too long, not an allowed value, missing required notes/reason, empty edit | `errors[]`: `{field, code, message, allowedValues?}`, **all** failing fields |
| 404 | `TICKET_NOT_FOUND` | No such ticket (including non-canonical identifiers) | none |
| 404 | `RESOURCE_NOT_FOUND` | Unknown route | none |
| 409 | `INVALID_TRANSITION` | Recognized status the ticket cannot move to, including its current status | `currentStatus`, `allowedTransitions` (empty for `CLOSED`/`CANCELLED`) |
| 409 | `TICKET_FROZEN` | Field edit on `CLOSED`/`CANCELLED` (D1) | `currentStatus` |
| 409 | `RESOLUTION_NOTES_NOT_EDITABLE` | Edit supplies resolution notes while the ticket has none (D1) | `currentStatus` |
| 503 | `TICKET_BUSY` | Ticket held by another in-progress change longer than the configured wait (spec edge case; D11). Ticket unchanged; no refresh intent. Fourth outcome, distinct from 400, 409, and 404. | `Retry-After` header |
| 405 / 415 | `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE` | Framework-level | none |
| 500 | `INTERNAL_ERROR` | Anything unexpected; generic text, details only in the server log | none |

- **Rationale**: Principle II needs a stable code and consistent shape on every error and no stack traces. 400 vs 409 differ by status *and* code (II; FR-006). 404 is a third outcome (FR-007; SC-004). 503 `TICKET_BUSY` is a fourth outcome (spec edge case): the response includes `Retry-After`, the ticket is unchanged, and no refresh intent is recorded. The spec's edge case "a status move names an unknown status" maps to 400 `VALIDATION_FAILED` on field `status`, never to 409.
- **D2 precedence (400 → 404 → 409)**: validation happens at the controller before any lookup, matching Principle II ("before reaching domain or persistence logic"). A malformed request about a missing ticket therefore returns 400. A well-formed request about a missing ticket returns 404. State checks run last, inside the locked transaction.
- **D1 rationale**: the brief fixed 409 for transitions, 400 for malformed/validation, 404 for unknown. Frozen edits and "notes not yet editable" are valid requests that the ticket's *state* forbids, which is the definition of 409. They carry their own codes so they cannot be confused with `INVALID_TRANSITION`.
- **Alternative**: reuse `INVALID_TRANSITION` for frozen edits (rejected: it is not a transition, and it would hide the "frozen" meaning); use 422 (rejected: the brief names only 400/404/409).

## R-06 Edit semantics (D3)

- **Decision**: `PATCH /api/tickets/{ticketId}` uses JSON Merge Patch semantics (RFC 7396) on `title`, `description`, `priority`, `assignee`, `category`, and `resolutionNotes`. An absent field is unchanged. An explicit null clears, and only assignee may be cleared. Null on title, description, priority, or category is 400 `VALIDATION_FAILED`. A blank assignee string does not clear the assignee. All other fields must be non-blank when present. A body containing no editable field is a 400 (`VALIDATION_FAILED`). Unknown fields (`status`, `id`, `createdAt`, `comments`, …) are rejected by R-09.
- **Rationale**: One endpoint (brief) with partial updates lets an agent correct one field without resending the rest. RFC 7396 is the confirmed meaning of absent versus null: null is the clear, and only assignee accepts it. A blank string is not a clear. No extra library is added; Jackson already in the Boot stack distinguishes absent from null.
- **Resolution notes**: allowed only if the ticket already has notes (then they replace them, must be non-blank, so they can never be cleared) and the ticket is not frozen. Order of checks inside the domain method: frozen (`TICKET_FROZEN`) first, then notes-not-editable.
- **Accepted edits always bump `updatedAt` and write an intent**, even if a value equals the stored one; "no-op detection" is not required by the spec and would add a rule that has no test in the spec.
- **Alternatives**: `PUT` with full replacement (forces clients to resend resolution notes and cancellation state); treating null as unchanged and using a blank assignee string to clear (rejected; D3).

## R-07 Enumerations on the wire (D6) and all-fields validation

- **Decision**: `Priority` and `Category` use the same text on the wire and in the database, as `TicketStatus` does: `LOW`, `MEDIUM`, `HIGH`, `URGENT` and `BILLING`, `TECHNICAL`, `ACCOUNT`, `SHIPPING`, `OTHER`. Stored as `varchar` with a `CHECK`. There is no wire/database casing mapping. Matching is exact and case-sensitive.
- **Request DTOs carry these as `String`** and validate with a small `@AllowedValues` constraint so Bean Validation reports every bad field in one response (FR-005, SC-003). The error entry lists `allowedValues` (spec edge case for category). The controller converts to enums only after validation succeeds.
- **Rationale**: one spelling for the wire and the database removes the translation. Storing the enum name avoids a JPA `AttributeConverter` per enum.
- **Alternatives**: bind to enums (Jackson stops at the first bad value, hiding others, so it fails SC-003 for multi-field mistakes); title-case labels on the wire (`Low`, `Billing`) with a mapping to stored names (rejected; D6).

## R-08 Search, pagination, and ordering (D7, D8)

- **Decision**: `GET /api/tickets?q=<keyword>&status=<STATUS>&page=<n>`. Implemented with a Spring Data JPA `Specification`:
  - `q` is trimmed; blank means no keyword filter.
  - A text match is `lower(column) LIKE lower('%' || escaped(q) || '%') ESCAPE '\'` on `title`, `description`, `category` (the stored name, e.g. `BILLING`), `resolution_notes`, `assignee`, and, through `EXISTS (SELECT … FROM comment …)`, comment `body`. `%`, `_`, and `\` in `q` are escaped so they match literally.
  - **Category note**: category is matched on its enum name (for example `BILLING`), which is the same text on the wire and in the database. There is no separate label.
  - **Identifier**: if the trimmed `q` matches `^TKT-([1-9][0-9]{0,17})$` (case-insensitive), the ticket with that numeric id also matches (OR-ed with the text match). A partial form like `1001` is not an identifier. `TKT-01001` does not match, because the spec says exact match.
  - `cancellation_reason` is **not** searched (FR-014 lists the fields and omits it).
  - `status` filter applies with AND; unrecognized value is 400 `VALIDATION_FAILED` on field `status` (FR-015).
  - Fixed page size 20, `page` is zero-based (default 0, negative is 400), ordered by `updated_at DESC, id DESC`. The `id` tie-break makes paging stable when timestamps are equal. A page past the end returns an empty `items` list, not an error.
  - Response is `{items, page, pageSize, totalItems, totalPages}`. Items are summaries (no comments, no description).
- **Rationale**: Constitution IX rules out a full-text engine; the brief says the same. A sequential scan with `LIKE` is correct and fast enough for SC-007's 1,000 tickets. `lower()` follows the database's locale: the pgvector image creates the database with a UTF-8 locale, which handles non-ASCII text, and the migration should not assume otherwise.
- **Index guidance**: add indexes `(updated_at DESC, id DESC)` and `(status, updated_at DESC, id DESC)` now (they serve ordering and the status filter). Do **not** add a trigram (`pg_trgm`) index until a measurement shows the scan is too slow; that would be a new migration then.
- **Alternatives**: PostgreSQL `tsvector` full-text (stems words, breaks substring semantics the spec requires); `ILIKE` (equivalent but harder to guarantee escaping portably in JPA Criteria); client-controlled `size` (the spec fixes 20).

## R-09 Rejecting unknown fields

- **Decision**: Configure Jackson to fail on unknown properties for all request bodies, and cover this with a test on every body-accepting endpoint (including an edit that sends `status`, which is required by Story 3 scenario 4). An unknown field becomes 400 `MALFORMED_REQUEST` naming the field.
- **Rationale**: Boot defaults to ignoring unknown fields. Silent ignore would let `PATCH {"status": "CLOSED"}` look successful, which would contradict FR-008 and Story 3 scenario 4.
- **To confirm in the first implementation task**: Boot 4 uses Jackson 3 (`tools.jackson`), and its property for this may differ from Boot 3's `spring.jackson.deserialization.fail-on-unknown-properties`. If the property name does not work, the fallback is a class-level `@JsonIgnoreProperties(ignoreUnknown = false)` on each request record (annotations stay in `com.fasterxml.jackson.annotation` in Jackson 3). The test decides which one ships.

## R-10 Refresh intent (outbox) and seed

- **Decision**: Table `ticket_refresh_intent(id identity, ticket_id FK, change_type, created_at)`. `change_type` is one of `TICKET_CREATED`, `FIELDS_UPDATED`, `COMMENT_ADDED`, `STATUS_CHANGED` (the last also covers cancellation). Every write use case in `TicketCommandService` ends with one `RefreshIntentRepository.save(...)` inside the same `@Transactional` method, after the domain method succeeds. The intent carries only the ticket id and kind; the later consumer reads current state.
- **Why not more columns**: no `processed_at`, `attempts`, or `payload`. Their semantics belong to feature 002 and can be added by a migration then (IX).
- **Why an explicit call, not events**: one call per use case in a single class is easy to read and test. A `@TransactionalEventListener` would hide the write. The "no change without an intent" risk is covered by `RefreshIntentIT`, which exercises every use case.
- **Atomicity tests**: refused requests assert the intent count is unchanged. Rollback is proven with a **test-only database trigger** installed by the test: (a) one that raises on `INSERT` into `ticket_refresh_intent` (the ticket change must also be absent), and (b) a `DEFERRABLE INITIALLY DEFERRED` constraint trigger on `ticket` that raises at commit (the already-inserted intent must also be absent). This stays on the real database and does not mock persistence (Principle III).
- **D9 seed**: `R__seed_dev_tickets.sql` inserts several tickets (every status, with comments and notes), takes ids from `ticket_id_seq`, and inserts a `TICKET_CREATED` intent for each. Without it, tickets present in a dev database would be invisible to the assistant, which is the stale-knowledge defect Principle V forbids.
- **Alternatives**: Domain events; database triggers writing the outbox (hides logic in SQL and would also fire for seed/maintenance changes); storing intents in a message broker (infrastructure no requirement needs).

## R-11 Time

- **Decision**: Inject a `java.time.Clock` bean. Times are `Instant`, stored as `timestamptz`, truncated to microseconds before use, and serialized as ISO-8601 UTC. Create sets `createdAt == updatedAt`.
- **Rationale**: PostgreSQL stores microseconds, whereas `Instant.now()` can carry nanoseconds on Linux. Without truncation, the value returned from create would differ from the value read back, violating the spec assumption "shown as that same moment wherever the ticket is read". An injectable clock keeps timing assertions deterministic.

## R-12 Test infrastructure

- **Decision**:
  - One shared `PostgresIntegrationTest` base class starts a singleton Testcontainers `PostgreSQLContainer` from `DockerImageName.parse("pgvector/pgvector:0.8.7-pg17").asCompatibleSubstituteFor("postgres")` and wires it with `@ServiceConnection`. Local compose and tests use the same image tag; the duplication is documented in `quickstart.md`.
  - Tests are **not** `@Transactional` (that would hide commit and rollback behavior). A `@BeforeEach` truncates `ticket_refresh_intent`, `comment`, and `ticket`, and does not reset the sequence (no test depends on specific identifier numbers).
  - HTTP-level tests use `MockMvc` (real context, real database). Concurrency tests call the same `MockMvc` from two threads released by a `CyclicBarrier`/`CountDownLatch` and repeat the race a fixed number of times (for example 25) to prove the invariant rather than hope for one interleaving.
  - **Deterministic lock test**: the test begins its own transaction, takes `SELECT … FOR UPDATE` on the ticket, starts the request on another thread, waits until `pg_stat_activity` shows a backend waiting on a lock (polling with a timeout, never a fixed sleep), applies a competing change and commits, then asserts that the request saw the new state (for example a 409 `INVALID_TRANSITION`). This proves "re-evaluated against committed state" without depending on scheduling.
  - `RestartPersistenceIT` creates data, closes the Spring context, starts a second context against the same container, and re-reads the data and intents.
  - `MigrationAndSeedIT` runs Flyway on an empty schema (with and without the seed location) and asserts the schema validates against the entities and that the seed creates one intent per seeded ticket.
- **To confirm**: the Boot 4 MockMvc test API names (`MockMvcTester` / `@AutoConfigureMockMvc` location) against `spring-boot-starter-webmvc-test` 4.1.2. The choice of MockMvc over a real HTTP port is for speed; the RANDOM_PORT alternative is equivalent for these tests.
- **Alternatives**: H2 and persistence mocks (forbidden by Principle III); Testcontainers per class (slow); `@Transactional` tests with rollback (cannot test commit/rollback or locks).

## R-13 Local run and secrets

- **Decision**: `docker-compose.yml` at the repository root runs `pgvector/pgvector:0.8.7-pg17` with `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?set it in .env}` (no default). `.env.example` holds placeholders only; `.env` is added to `.gitignore`. The app reads `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` from the environment; `application.yml` has no password. The `dev` profile adds only the seed location.
- **Rationale**: Principle VIII (no secrets in the repo) and VI (environment-specific configuration externalized).
- **Alternatives**: A committed dev password in compose (even throwaway values train people to commit credentials, and the constitution forbids secrets "ever"); Spring Boot's `spring-boot-docker-compose` module (convenient, but an extra dependency with no requirement behind it).

## R-14 Spring AI

- **Decision**: Spring AI 2.0.2 is recorded as a pinned version in the plan and nothing else: no BOM import and no dependency in `backend/pom.xml`.
- **Rationale**: The constitution requires pinning, not using. Importing the BOM now adds dependency surface and a version that could drift without purpose. Feature 002 adds it, at the pinned version.
