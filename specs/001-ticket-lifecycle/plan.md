# Implementation Plan: Ticket Lifecycle Management

**Branch**: `001-ticket-lifecycle` | **Date**: 2026-11-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-ticket-lifecycle/spec.md`

## Summary

A single Spring Boot service that lets support agents create, view, edit, comment on, find, and move tickets through the lifecycle `OPEN → IN_PROGRESS → RESOLVED → CLOSED` (plus `OPEN`/`IN_PROGRESS → CANCELLED`).

Technical approach:

- **Layers**: `web` (controllers, DTOs, error mapping) → `application` (use cases, transaction boundary) → `domain` (`Ticket` aggregate and the only copy of the state machine) → `persistence` (Spring Data JPA repositories, search specification).
- **Correctness under concurrency**: edit, comment, and status move each run in one transaction that first takes a pessimistic row lock (`SELECT … FOR UPDATE`) on the ticket, then evaluates the request against the committed state. Create runs in a transaction and does not take a row lock, because no row exists yet. A losing request is therefore re-evaluated, not overwritten (spec Story 2 scenario 10; frozen-ticket edge case).
- **Refresh intent**: a row in `ticket_refresh_intent` is inserted in the same transaction as every ticket change. Nothing reads it in this feature (Principle V; processing belongs to feature 002).
- **Errors**: RFC 9457 problem details with a stable `errorCode`. 400 = malformed input or field validation, 409 = request refused by ticket state, 404 = unknown ticket.
- **Schema**: Flyway versioned migrations; dev-only seed data as a repeatable migration in a separate location that only the `dev` profile loads.
- **Verification**: Testcontainers PostgreSQL (the same pgvector image used locally), the full 5×5 transition matrix, both concurrency races, and outbox commit/rollback tests. Deterministic transition tests are written first and observed failing.

## Technical Context

**Language/Version**: Java 21 (`maven.compiler.release=21`)

**Pinned versions** (Constitution "Technology Constraints": do not drift; changing any requires a plan update):

| Component | Pinned version | Role in this feature |
|-----------|----------------|----------------------|
| Java | 21 | Language and runtime |
| Spring Boot | 4.1.2 | Parent/BOM for everything below |
| Spring AI | 2.0.2 | **Pinned for later features. NOT a dependency here** (no BOM import, no artifact) |
| Maven | 3.9.12 via Maven Wrapper | Build tool; version fixed in `.mvn/wrapper/maven-wrapper.properties` |
| PostgreSQL image | `pgvector/pgvector:0.8.7-pg17` | Local runs (docker-compose) and Testcontainers |

Versions below are **managed by the Spring Boot 4.1.2 BOM** (read from `spring-boot-dependencies-4.1.2.pom` on 2026-11-19); the plan does not override them: Spring Framework 7.0.10, Hibernate ORM 7.4.13.Final, Hibernate Validator 9.1.5.Final, Jackson 3.1.7 (`tools.jackson`), Flyway 12.4.0, PostgreSQL JDBC 42.7.14, Testcontainers 2.0.5, JUnit Jupiter 6.0.3, AssertJ 3.27.7, Mockito 5.23.0.

**Primary Dependencies** (all Boot-managed, no explicit versions):
`spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql`, `org.postgresql:postgresql` (runtime).
Test scope: `spring-boot-starter-test`, `spring-boot-starter-webmvc-test`, `spring-boot-testcontainers`, `org.testcontainers:testcontainers-junit-jupiter`, `org.testcontainers:testcontainers-postgresql`.
Build plugin: `maven-enforcer-plugin` (Java `[21,22)` and Maven `[3.9.12,3.10)`), justified by the "MUST NOT drift" pin rule.
Not added: Spring AI, Lombok, MapStruct, H2, any search engine, `spring-boot-starter-actuator`, any extra JSON/validation library.

**Storage**: PostgreSQL 17 with the pgvector extension available in the image. This feature does **not** run `CREATE EXTENSION vector`; the assistant feature (002) does.

**Testing**: JUnit 6 (Jupiter), AssertJ, `MockMvc` (real Spring context) over a Testcontainers PostgreSQL. No H2, no mocked persistence.

**Target Platform**: Linux/macOS server process on a JVM; local DB via `docker compose`.

**Project Type**: web-service (backend only; the React frontend is a later feature).

**Performance Goals**: The spec sets none beyond SC-007 (find a known ticket among at least 1,000). No latency number is invented here. A sequential-scan `LIKE` search is sufficient at that scale; see research R-08 for what to measure before adding an index.

**Constraints**: Server-side validation only (clients untrusted); state machine in the domain layer only; no ticket delete; no processing of refresh intents; pessimistic row lock on edit, transition, and comment (create takes none); page size fixed at 20.

**Scale/Scope**: One service, one aggregate (`Ticket` with `Comment`s), one outbox table, 6 endpoints, 3 versioned migrations plus 1 dev seed.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Pre-research | Post-design | Notes |
|---|-----------|--------------|-------------|-------|
| I | Domain Integrity | PASS | PASS | The transition table is a single method on `TicketStatus`; `Ticket` is the only caller. Controllers, repositories, and SQL contain no transition logic. DB `CHECK` constraints guard data *shape* only (e.g. cancellation reason present iff `CANCELLED`), never transitions. |
| II | Validation at the Boundary | PASS | PASS | Bean Validation at the controller; one problem-detail format for every error including framework errors; 400 vs 409 use different status *and* `errorCode`. No stack traces in responses (catch-all returns generic 500). |
| III | Test-First for Deterministic Logic | PASS | PASS | Transition matrix (25 pairs) as integration tests on real PostgreSQL, written first against stubs and observed failing. |
| IV | Grounded AI Only | N/A | N/A | No AI in this feature. |
| V | Knowledge Freshness | **PARTIAL** | **PARTIAL** | This feature delivers the atomic, durable intent (creation, field update, comment, every status move incl. cancellation) and tests commit/rollback behavior. Retry, failure visibility, and full reindex are deferred to feature 002 **by spec decision** (clarification 2026-11-19). Justified in Complexity Tracking. |
| VI | Configuration over Hardcoding | PASS | PASS | No AI parameters here. DB URL/user/password and lock timeout come from configuration/environment. The page size of 20 is a spec-fixed constant, not a tunable. |
| VII | Bounded AI Scope | N/A | N/A | No AI. |
| VIII | Security | PASS | PASS | No secrets in the repo: compose reads the DB password from an untracked `.env`; `.env.example` has placeholders only; `application.yml` has no default password. Seed data is fictional. |
| IX | Simplicity | PASS | PASS | No full-text engine, no MapStruct/Lombok, no generic repository layer, no event bus (explicit outbox call per use case). Two justified additions are listed in Complexity Tracking. |
| X | AI Accountability | PASS | PASS | All plan content is AI-drafted and needs human review. Any AI mistake found during implementation goes into `docs/ai-mistakes.md`. |

**Gate result**: no unjustified violations. Principle V is PARTIAL by documented spec decision, so **this feature alone does not satisfy the constitution's "done" definition** (retry behavior untested). See Complexity Tracking.

## Decisions (confirmed)

D1–D11 are confirmed. Details and alternatives are in [research.md](./research.md).

| # | Decision | Where it shows up |
|---|----------|-------------------|
| D1 | **Confirmed.** A refused edit or comment **because of ticket state** is 409, with distinct codes: `TICKET_FROZEN` (edit on `CLOSED`/`CANCELLED`) and `RESOLUTION_NOTES_NOT_EDITABLE` (edit sets notes while the ticket has none). The brief fixed 409 for transitions only; these are not malformed input. | contract, R-05 |
| D2 | **Confirmed.** Check order is **400 → 404 → 409**: boundary validation first, then existence, then state. | R-05 |
| D3 | **Confirmed.** `PATCH` uses JSON Merge Patch semantics (RFC 7396): an absent field is unchanged, and an explicit null clears. Only assignee may be cleared. Null on title, description, priority, or category is 400 `VALIDATION_FAILED`. A blank assignee string does not clear the assignee. An empty body (no editable field) is a 400. | contract, R-06 |
| D4 | **Confirmed.** A transition request carrying a field that does not belong to the target (for example `resolutionNotes` on a move to `IN_PROGRESS`) is a 400, not silently ignored. | contract |
| D5 | **Confirmed.** Comments also take the row lock (the brief named moves and edits). A comment bumps `updatedAt`, so unlocked comments could write a stale `updatedAt` over a concurrent change. | R-04 |
| D6 | **Confirmed.** Priority and category use enum names on the wire, the same as status: `LOW`, `MEDIUM`, `HIGH`, `URGENT` and `BILLING`, `TECHNICAL`, `ACCOUNT`, `SHIPPING`, `OTHER`. There is no wire/database casing mapping. | R-07 |
| D7 | **Confirmed.** Cancellation reason is **not** searchable (FR-014 lists the searchable fields and omits it). | R-08 |
| D8 | **Confirmed.** Keyword is trimmed; a blank keyword means no keyword filter. `%`, `_`, and `\` in a keyword are matched literally. An identifier match needs canonical form `TKT-<n>` (no leading zeros). | R-08 |
| D9 | **Confirmed.** Dev seed data also writes a refresh intent per seeded ticket, so the later assistant is not born stale. | R-10 |
| D10 | **Confirmed.** Base package `com.ttn.tickets`. Maven coordinates `com.ttn:ticket-management`. The code lives under `backend/` so a `frontend/` directory can sit beside it later. | Project Structure |
| D11 | **Confirmed.** A PostgreSQL `lock_timeout` (5 s) bounds waits on the row lock. If the ticket is held longer than that wait, the response is 503 `TICKET_BUSY` and includes a `Retry-After` header (spec edge case: temporarily unavailable, retry later; ticket unchanged; no refresh intent; a fourth outcome, distinct from badly formed input, a disallowed move, and a missing ticket). | R-04, contract |

## Project Structure

### Documentation (this feature)

```text
specs/001-ticket-lifecycle/
├── plan.md              # This file
├── research.md          # Phase 0: decisions, rationale, alternatives
├── data-model.md        # Phase 1: entities, schema, state machine
├── quickstart.md        # Phase 1: run and validation guide
├── contracts/
│   └── openapi.yaml     # Phase 1: REST contract (OpenAPI 3.1)
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks, not created here)
```

### Source Code (repository root)

```text
docker-compose.yml                         # pgvector/pgvector:0.8.7-pg17, password from .env
.env.example                               # placeholders only; .env is gitignored
backend/
├── pom.xml                                # com.ttn:ticket-management; spring-boot-starter-parent 4.1.2, release 21, enforcer
├── mvnw, mvnw.cmd
├── .mvn/wrapper/maven-wrapper.properties  # Maven 3.9.12
└── src/
    ├── main/
    │   ├── java/com/ttn/tickets/
    │   │   ├── TicketManagementApplication.java
    │   │   ├── web/
    │   │   │   ├── TicketController.java
    │   │   │   ├── dto/                   # request/response records
    │   │   │   ├── validation/AllowedValues.java (+ validator)
    │   │   │   └── error/                 # ApiExceptionHandler, ErrorCode
    │   │   ├── application/
    │   │   │   ├── TicketCommandService.java   # @Transactional: create, edit, transition, comment
    │   │   │   ├── TicketQueryService.java     # read-only: get, list/search
    │   │   │   ├── TicketNotFoundException.java
    │   │   │   └── view/                       # immutable read models returned inside the tx
    │   │   ├── domain/
    │   │   │   ├── Ticket.java                 # aggregate; create/edit/transition/addComment
    │   │   │   ├── Comment.java
    │   │   │   ├── TicketStatus.java           # the ONLY transition table
    │   │   │   ├── Priority.java, Category.java
    │   │   │   ├── RefreshIntent.java, ChangeType.java
    │   │   │   └── (InvalidTransitionException, TicketFrozenException,
    │   │   │        ResolutionNotesNotEditableException)
    │   │   └── persistence/
    │   │       ├── TicketRepository.java       # incl. findByIdForUpdate (PESSIMISTIC_WRITE)
    │   │       ├── TicketSearchSpecifications.java
    │   │       └── RefreshIntentRepository.java
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml             # adds classpath:db/seed to Flyway locations
    │       └── db/
    │           ├── migration/                  # V1 ticket+sequence, V2 comment, V3 refresh intent
    │           └── seed/R__seed_dev_tickets.sql
    └── test/java/com/ttn/tickets/
        ├── domain/TicketStatusTransitionTest.java     # pure unit, written first
        └── integration/
            ├── PostgresIntegrationTest.java           # shared Testcontainers base + cleanup
            ├── TransitionMatrixIT.java                # 25 from/to pairs, written first
            ├── TransitionValidationIT.java
            ├── CreateAndViewIT.java
            ├── EditTicketIT.java
            ├── CommentIT.java
            ├── SearchAndListIT.java
            ├── ErrorFormatIT.java
            ├── RefreshIntentIT.java
            ├── ConcurrencyIT.java
            ├── RestartPersistenceIT.java
            └── MigrationAndSeedIT.java
```

**Structure Decision**: single Maven module under `backend/`. Layers are Java packages, not Maven modules (Principle IX). Dependency direction is `web → application → domain` and `application → persistence → domain`; `domain` imports no Spring classes. The domain entities carry JPA annotations directly rather than being mirrored by a second persistence model, which avoids a mapping layer that no requirement needs.

## Test Strategy (summary)

| Concern | Test | Spec / constitution trace |
|---------|------|---------------------------|
| Transition rules, pure logic | `TicketStatusTransitionTest` (unit, first) | FR-009, Principle I |
| Every from/to pair (5×5 = 5 allowed, 20 refused) against real PostgreSQL; refused pairs assert 409 `INVALID_TRANSITION`, `currentStatus`, `allowedTransitions`, ticket and `updatedAt` unchanged, zero new intents | `TransitionMatrixIT` (first, red against stubs) | FR-009, SC-002, Principle III |
| 400 cases: bad status value, missing/blank notes on `RESOLVED`, missing/blank reason on `CANCELLED`, stray fields, unknown JSON fields; 404 unknown ticket | `TransitionValidationIT`, `ErrorFormatIT` | FR-005–007, FR-010, SC-003, SC-004 |
| Race 1: two `OPEN→IN_PROGRESS` moves; exactly one 200 and one 409; one intent. Deterministic variant: the test holds the row lock, queues the request, commits a competing change, and asserts the request was evaluated against it | `ConcurrencyIT` | Story 2 scenario 10 |
| Race 2: edit vs. move to `CLOSED`/`CANCELLED`; only the two serial outcomes are possible; a frozen ticket is never edited; intent count equals the number of accepted changes | `ConcurrencyIT` | Frozen-edit edge case, FR-011 |
| Intent on commit for create, edit, comment, every move incl. cancel; none for refused requests; none on rollback (a test-only DB trigger forces a failure after the ticket write, and another after the intent write) | `RefreshIntentIT` | FR-016, SC-005, Principle V |
| Persistence across restart (second Spring context on the same container) | `RestartPersistenceIT` | FR-004, SC-006 |
| Search: each searchable field, case-insensitivity, exact ID (case/whitespace), partial ID not matching, `%`/`_` literal, status filter, combined, empty result, ≥1,000 tickets | `SearchAndListIT` | FR-013–015, SC-007 |
| Migrations apply to an empty database; seed applies only with the dev location and creates intents | `MigrationAndSeedIT` | Constitution "Technology Constraints", D9 |

Red-first procedure: the first task commits compiling stubs (domain methods throw `UnsupportedOperationException`, endpoints missing) plus the matrix and unit tests; the failing run is recorded in the task log before any real implementation is written.

## Complexity Tracking

| Violation / addition | Why Needed | Simpler Alternative Rejected Because |
|----------------------|------------|--------------------------------------|
| **Principle V is only partly met** (intent recorded and tested; retry, failure visibility, and reindex absent) and the Quality Gate "retry behavior is tested where tickets change" cannot pass in this feature | Spec clarification 2026-11-19 moved retry, failure visibility, and rebuild to feature 002 because nothing consumes intents until the assistant exists. Tracked in `docs/carry-over-002-ticket-assistant.md`. | Building a consumer here would add an embedding model, a worker, and failure handling with no feature to use them (violates IX and the spec's scope). **Mitigation**: do not release 001 to users until 002 lands, or record explicit reviewer approval of this deviation in the PR. |
| Custom `@AllowedValues` constraint (priority, category, status as strings) | FR-005/SC-003 require naming *every* invalid field in one response. Binding straight to enums makes Jackson fail on the first bad value and hide the rest. | Jackson enum binding reports only one field per request. A handler that parses Jackson's exception reports one error and breaks SC-003 for multi-field mistakes. |
| `maven-enforcer-plugin` | Constitution requires the pinned Java/Maven versions to "NOT drift"; the enforcer makes drift a build failure. | Documentation alone does not stop drift. |

## Environment Notes (outside this plan's control)

- The only JDK found on this machine is **25** (Corretto 25.0.2). The plan pins **21**, and the enforcer rule will fail the build on any other JDK. Install a JDK 21 (for example via SDKMAN or Homebrew) before the first build. Building on 25 with `--release 21` would compile but would not prove the pinned runtime.
- Docker must be running for both `docker compose` and Testcontainers.
- Maven 3.9.12, Spring Boot 4.1.2, and Spring AI 2.0.2 artifacts were confirmed present on Maven Central on 2026-11-19; the `pgvector/pgvector:0.8.7-pg17` tag exists on Docker Hub.
