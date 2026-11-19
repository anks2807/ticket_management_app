# Tasks: Ticket Lifecycle Management

**Input**: Design documents from `/specs/001-ticket-lifecycle/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included. plan.md requires test-first for the transition rules (Constitution III) and integration tests on real PostgreSQL for every story.

**Organization**: Phase 1 setup, Phase 2 foundational domain with red-first tests, then one phase per user story in priority order (US1+US2 are both P1 and share one phase), then a final cross-cutting phase.

**Package and paths**: Java code lives under `backend/src/{main,test}/java/com/ttn/tickets/`. Three files sit at the repository root (`docker-compose.yml`, `.env.example`, `.gitignore`), as plan.md's Project Structure places them. See the Notes section.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 to US5 for user story phases only
- `data-model.md` field citations appear as `data-model.md` `Entity.field`

---

## Phase 1: Setup (Project Initialization)

**Purpose**: Build, run, and test infrastructure. Nothing here implements business logic.

- [X] T001 Create `backend/pom.xml` with coordinates `com.ttn:ticket-management`, parent `spring-boot-starter-parent` 4.1.2, `java.version` 21, the starters and test dependencies listed in plan.md Technical Context, and `maven-enforcer-plugin` requiring Java `[21,22)` and Maven `[3.9.12,3.10)`
- [X] T002 Generate the Maven Wrapper pinned to Maven 3.9.12: `backend/.mvn/wrapper/maven-wrapper.properties`, `backend/mvnw`, `backend/mvnw.cmd` (depends on T001)
- [X] T003 [P] Create the Spring Boot entry point `backend/src/main/java/com/ttn/tickets/TicketManagementApplication.java`
- [X] T004 [P] Create `backend/src/main/resources/application.yml`: datasource from `SPRING_DATASOURCE_*` environment variables with no password default, `spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=validate`, Flyway location `classpath:db/migration` only (research R-03, R-13)
- [X] T005 [P] Create `docker-compose.yml` at the repository root: image `pgvector/pgvector:0.8.7-pg17`, `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?set it in .env}`, port 5432 (research R-13)
- [X] T006 [P] Create `.env.example` at the repository root with placeholder values only (research R-13)
- [X] T007 [P] Add `.env` to `.gitignore` at the repository root (research R-13)
- [X] T008 [P] Create `backend/src/main/resources/db/migration/V1__create_ticket.sql`: `ticket_id_seq` starting at 1001, `ticket` table with CHECK constraints for shape only, and the two indexes from data-model.md. Cite `data-model.md` `Ticket.id`, `Ticket.title`, `Ticket.description`, `Ticket.priority`, `Ticket.category`, `Ticket.assignee`, `Ticket.status`, `Ticket.resolutionNotes`, `Ticket.cancellationReason`, `Ticket.createdAt`, `Ticket.updatedAt`
- [X] T009 [P] Create `backend/src/main/resources/db/migration/V2__create_comment.sql`: `comment` table and `comment_ticket_idx`. Cite `data-model.md` `Comment.author` and `Comment.text` (column `body`)
- [X] T010 [P] Create `backend/src/main/resources/db/migration/V3__create_ticket_refresh_intent.sql`: `ticket_refresh_intent` table with the four `change_type` values. Cite `data-model.md` `Refresh intent.changeType`
- [X] T011 [P] Create the shared Testcontainers base class `backend/src/test/java/com/ttn/tickets/integration/PostgresIntegrationTest.java`: singleton `PostgreSQLContainer` from `pgvector/pgvector:0.8.7-pg17` (`asCompatibleSubstituteFor("postgres")`), wired with `@ServiceConnection`, and a `@BeforeEach` that truncates `ticket_refresh_intent`, `comment`, `ticket` without resetting the sequence (research R-12)

**Checkpoint**: `./mvnw -q -DskipTests compile` succeeds on JDK 21, and the Flyway migrations apply to an empty container.

---

## Phase 2: Foundational (Domain Stubs and Red-First Tests)

**Purpose**: Compiling stubs and the tests that define the transition rules. The tests must fail before any real logic exists (Constitution III). No user story work starts until the red run in T019 is recorded.

- [X] T012 [P] Create `backend/src/main/java/com/ttn/tickets/domain/Priority.java` with constants `LOW`, `MEDIUM`, `HIGH`, `URGENT`. Cite `data-model.md` `Ticket.priority`
- [X] T013 [P] Create `backend/src/main/java/com/ttn/tickets/domain/Category.java` with constants `BILLING`, `TECHNICAL`, `ACCOUNT`, `SHIPPING`, `OTHER`. Cite `data-model.md` `Ticket.category`
- [X] T014 Create the stub `backend/src/main/java/com/ttn/tickets/domain/TicketStatus.java`: constants `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`. `canMoveTo` and `allowedTransitions` throw `UnsupportedOperationException`. Cite `data-model.md` `Ticket.status`
- [X] T015 [P] Create `backend/src/main/java/com/ttn/tickets/domain/InvalidTransitionException.java` carrying `currentStatus` and `allowedTransitions` (fields are read by `TransitionMatrixIT`)
- [X] T016 Create the stub `backend/src/main/java/com/ttn/tickets/domain/Ticket.java`: `moveTo` throws `UnsupportedOperationException`. The stub has no JPA annotations; mapping starts in T024 and T025 (depends on T014, T015)
- [X] T017 [P] Create `backend/src/test/java/com/ttn/tickets/domain/TicketStatusTransitionTest.java`: pure unit test with no Spring context. Covers the 5 allowed pairs, the 20 refused pairs, and `allowedTransitions` for each status (data-model.md State machine table). Traces to FR-009, Constitution I
- [X] T018 [P] Create `backend/src/test/java/com/ttn/tickets/integration/TransitionMatrixIT.java`: all 25 from/to pairs against real PostgreSQL through `MockMvc`, with fixtures inserted by `JdbcTemplate`. Allowed pairs expect 200. Refused pairs expect 409 `INVALID_TRANSITION` with `currentStatus` and `allowedTransitions`, the ticket and `updatedAt` unchanged, and zero new refresh intents (depends on T011)
- [X] T019 Confirm the red run: run `backend/src/test/java/com/ttn/tickets/domain/TicketStatusTransitionTest.java` and `backend/src/test/java/com/ttn/tickets/integration/TransitionMatrixIT.java` through `./mvnw test` in `backend/`. Both must fail. The unit test fails with `UnsupportedOperationException`, and the matrix fails with 404 because the transitions endpoint does not exist yet. Record the failing test names in the commit message

**Checkpoint**: Both red tests fail for the stated reasons. Real implementation begins.

---

## Phase 3: User Stories 1 and 2 - Create, view, and move tickets (Priority: P1) 🎯 MVP

**Goal**: An agent can create a ticket, open it with its full details, and move it through the lifecycle. Disallowed moves are refused without changes.

**Independent Test**: Create a ticket, read it back (CreateAndViewIT). Move it through each allowed status and attempt disallowed moves (TransitionMatrixIT, TransitionValidationIT). The refresh intent is written with each change (verified in the final phase).

### Tests for User Stories 1 and 2 (write first; must fail before implementation)

- [X] T020 [P] [US1] Create `backend/src/test/java/com/ttn/tickets/integration/CreateAndViewIT.java`: create with all fields and without assignee, check identifier `TKT-` form, status OPEN, `createdAt == updatedAt`, empty comments and notes, and 400 for each invalid field naming that field (spec US1 scenarios 1–3; FR-001–FR-005)
- [X] T021 [P] [US2] Create `backend/src/test/java/com/ttn/tickets/integration/TransitionValidationIT.java`: 400 `VALIDATION_FAILED` for missing or blank `resolutionNotes` on RESOLVED and missing or blank `cancellationReason` on CANCELLED. 400 for stray fields that do not belong to the target status (D4). 400 `MALFORMED_REQUEST` for an unknown status, with the ticket unchanged. 404 `TICKET_NOT_FOUND` for an unknown ticket (FR-006, FR-007, SC-003, SC-004)

### Implementation for User Stories 1 and 2

- [X] T022 [P] [US1] Create `backend/src/main/java/com/ttn/tickets/domain/ChangeType.java` with `TICKET_CREATED`, `FIELDS_UPDATED`, `COMMENT_ADDED`, `STATUS_CHANGED`. Cite `data-model.md` `Refresh intent.changeType`
- [X] T023 [P] [US1] Create `backend/src/main/java/com/ttn/tickets/domain/RefreshIntent.java` as an immutable JPA entity mapped to `ticket_refresh_intent`, with no setters and no update or delete methods. Cite `data-model.md` `Refresh intent.id`, `Refresh intent.ticketId`, `Refresh intent.changeType`, `Refresh intent.createdAt`
- [X] T024 [P] [US1] Create `backend/src/main/java/com/ttn/tickets/domain/Comment.java` with `author`, `text`, `createdAt`, and JPA mapping to the `comment` table. Cite `data-model.md` `Comment.id`, `Comment.ticketId`, `Comment.author`, `Comment.text`, `Comment.createdAt`
- [X] T025 [US1] Implement `Ticket.create(title, description, priority, category, assignee, now)` in `backend/src/main/java/com/ttn/tickets/domain/Ticket.java`: status OPEN, `createdAt = updatedAt = now`. Add the JPA mapping for the `comments` collection (ordered by `id`). Map `Ticket.id` with `@SequenceGenerator(sequenceName = "ticket_id_seq", allocationSize = 1)` so Hibernate's allocation matches the sequence's increment of 1. Add static helpers that format `TKT-<id>` and parse only the canonical form. Cite `data-model.md` `Ticket.id`, `Ticket.title`, `Ticket.description`, `Ticket.priority`, `Ticket.category`, `Ticket.assignee`, `Ticket.status`, `Ticket.createdAt`, `Ticket.updatedAt`, `Ticket.comments`
- [X] T026 [US2] Replace the stub in `backend/src/main/java/com/ttn/tickets/domain/TicketStatus.java` with the real transition table: `canMoveTo`, `allowedTransitions`, and `InvalidTransitionException` on refusal. This turns T017 green. Cite `data-model.md` `Ticket.status` (State machine table)
- [X] T027 [US2] Implement `Ticket.moveTo(target, notesOrReason, now)` in `backend/src/main/java/com/ttn/tickets/domain/Ticket.java`: consult only `TicketStatus.canMoveTo`; require notes for RESOLVED and a reason for CANCELLED; set `resolutionNotes` or `cancellationReason` as part of the move; set `updatedAt = now`. Violated invariants throw `IllegalArgumentException` (data-model.md). Cite `data-model.md` `Ticket.resolutionNotes`, `Ticket.cancellationReason`, `Ticket.updatedAt`
- [X] T028 [US1] Create `backend/src/main/java/com/ttn/tickets/persistence/TicketRepository.java` (Spring Data JPA) with `findById` and `findByIdForUpdate` annotated `@Lock(PESSIMISTIC_WRITE)` (research R-04)
- [X] T029 [P] [US1] Create `backend/src/main/java/com/ttn/tickets/persistence/RefreshIntentRepository.java`
- [X] T030 [P] [US1] Create `backend/src/main/java/com/ttn/tickets/application/TicketNotFoundException.java`
- [X] T031 [P] [US1] Create `backend/src/main/java/com/ttn/tickets/application/view/TicketDetails.java` and `backend/src/main/java/com/ttn/tickets/application/view/CommentView.java` as immutable read models (data-model.md Read models)
- [X] T032 [US1] Add a `java.time.Clock` bean (`Clock.systemUTC()`) to `backend/src/main/java/com/ttn/tickets/TicketManagementApplication.java`. Truncate each instant to microseconds before it is stored or returned, so the value read back matches the value written (research R-11)
- [X] T033 [US1] Create `backend/src/main/java/com/ttn/tickets/application/TicketQueryService.java` (read-only) with `getById` returning `TicketDetails` inside the transaction
- [X] T034 [US1] Create `backend/src/main/java/com/ttn/tickets/application/TicketCommandService.java` (`@Transactional`) with `create` (no row lock, one `TICKET_CREATED` intent in the same save) and `transition` (`findByIdForUpdate`, read the T032 clock after the lock and truncate to microseconds, `STATUS_CHANGED` intent). Use that clock, not `Instant.now()`. Trace to research R-04, R-11, FR-016
- [X] T035 [P] [US1] Create `backend/src/main/java/com/ttn/tickets/web/validation/AllowedValues.java` (constraint annotation with `allowedValues` attribute)
- [X] T036 [P] [US1] Create `backend/src/main/java/com/ttn/tickets/web/validation/AllowedValuesValidator.java` so every invalid field is reported in one response (FR-005, SC-003)
- [X] T037 [P] [US1] Create `backend/src/main/java/com/ttn/tickets/web/dto/CreateTicketRequest.java` with `String` fields validated by Bean Validation and `@AllowedValues`. Cite `data-model.md` `Ticket.title`, `Ticket.description`, `Ticket.priority`, `Ticket.category`, `Ticket.assignee`
- [X] T038 [P] [US2] Create `backend/src/main/java/com/ttn/tickets/web/dto/TransitionRequest.java` with `status` as `String`, optional `resolutionNotes` and `cancellationReason`. Cite `data-model.md` `Ticket.resolutionNotes`, `Ticket.cancellationReason`
- [X] T039 [P] [US1] Create `backend/src/main/java/com/ttn/tickets/web/dto/TicketResponse.java`
- [X] T040 [P] [US1] Create `backend/src/main/java/com/ttn/tickets/web/error/ErrorCode.java` with the codes from research R-05 for 400, 404, 409 `INVALID_TRANSITION`, 405, 415, and 500
- [X] T041 [US1] Create `backend/src/main/java/com/ttn/tickets/web/error/ApiExceptionHandler.java` (`@RestControllerAdvice` extending `ResponseEntityExceptionHandler`): RFC 9457 `application/problem+json`, `errorCode`, `errors[]` with all failing fields, no stack traces (Constitution II; research R-05)
- [X] T042 [US1] Make unknown JSON fields fail with 400 `MALFORMED_REQUEST` in `backend/src/main/resources/application.yml`. Confirm the Jackson 3 property name; if it does not work, add `@JsonIgnoreProperties(ignoreUnknown = false)` to each request record (research R-09)
- [X] T043 [US1] Create `backend/src/main/java/com/ttn/tickets/web/TicketController.java` with `POST /api/tickets` (create), `GET /api/tickets/{ticketId}` (view), and `POST /api/tickets/{ticketId}/transitions` (move). Paths and response codes follow `specs/001-ticket-lifecycle/contracts/openapi.yaml`. Validation happens here before any lookup (D2)

**Checkpoint**: CreateAndViewIT, TransitionValidationIT, TransitionMatrixIT, and TicketStatusTransitionTest all pass. P1 is independently testable as the MVP.

---

## Phase 4: User Story 3 - Edit ticket details (Priority: P2)

**Goal**: An agent edits title, description, priority, assignee, category, and resolution notes without touching status. Closed and cancelled tickets are frozen.

**Independent Test**: Edit each editable field and confirm status, identifier, and comments are unchanged. A status change through PATCH is refused (EditTicketIT).

### Tests for User Story 3 (write first; must fail before implementation)

- [X] T044 [P] [US3] Create `backend/src/test/java/com/ttn/tickets/integration/EditTicketIT.java`: JSON Merge Patch semantics per D3 (absent is unchanged; explicit null clears only assignee; null on title, description, priority, or category is 400; blank assignee does not clear; empty body is 400). Frozen tickets return 409 `TICKET_FROZEN`. Notes-on-edit with no notes return 409 `RESOLUTION_NOTES_NOT_EDITABLE`. Notes cannot be cleared. Refused edits add no intent (spec US3; FR-008, FR-011)

### Implementation for User Story 3

- [X] T045 [P] [US3] Create `backend/src/main/java/com/ttn/tickets/domain/TicketFrozenException.java` (carries `currentStatus`)
- [X] T046 [P] [US3] Create `backend/src/main/java/com/ttn/tickets/domain/ResolutionNotesNotEditableException.java` (carries `currentStatus`)
- [X] T047 [US3] Implement `Ticket.edit(changes, now)` in `backend/src/main/java/com/ttn/tickets/domain/Ticket.java`: refuse when CLOSED or CANCELLED; refuse notes when the ticket has none; apply supplied fields; set `updatedAt = now`. Cite `data-model.md` `Ticket.title`, `Ticket.description`, `Ticket.priority`, `Ticket.assignee`, `Ticket.category`, `Ticket.resolutionNotes`, `Ticket.status`
- [X] T048 [P] [US3] Create `backend/src/main/java/com/ttn/tickets/web/dto/EditTicketRequest.java` that distinguishes absent from explicit null (use Jackson's tree model; no new dependency). Only `assignee` may be null. Cite `data-model.md` `Ticket.assignee` (task narrows it to the explicit-null clear rule from D3)
- [X] T049 [US3] Add `edit` to `backend/src/main/java/com/ttn/tickets/application/TicketCommandService.java`: `findByIdForUpdate`, read the clock after the lock, `FIELDS_UPDATED` intent in the same save
- [X] T050 [US3] Add `PATCH /api/tickets/{ticketId}` to `backend/src/main/java/com/ttn/tickets/web/TicketController.java` (Content-Type `application/merge-patch+json` per contracts/openapi.yaml)
- [X] T051 [US3] Add `TICKET_FROZEN` and `RESOLUTION_NOTES_NOT_EDITABLE` to `backend/src/main/java/com/ttn/tickets/web/error/ErrorCode.java` and map both to 409 in `backend/src/main/java/com/ttn/tickets/web/error/ApiExceptionHandler.java`

**Checkpoint**: EditTicketIT passes and the earlier phase tests still pass.

---

## Phase 5: User Story 4 - Comment on a ticket (Priority: P2)

**Goal**: An agent adds an append-only comment in any status, including CLOSED and CANCELLED.

**Independent Test**: Add a comment, open the ticket, and confirm the comment with author, text, and time is shown, and earlier comments are unchanged (CommentIT).

### Tests for User Story 4 (write first; must fail before implementation)

- [X] T052 [P] [US4] Create `backend/src/test/java/com/ttn/tickets/integration/CommentIT.java`: comment on OPEN, IN_PROGRESS, RESOLVED, CLOSED, and CANCELLED tickets; `updatedAt` changes; comments are ordered and never edited or deleted; `COMMENT_ADDED` intent per saved comment (spec US4; FR-012)

### Implementation for User Story 4

- [X] T053 [US4] Implement `Ticket.addComment(author, text, now)` in `backend/src/main/java/com/ttn/tickets/domain/Ticket.java`: append a `Comment` with `createdAt = now` and set `updatedAt = now`. Cite `data-model.md` `Comment.createdAt`, `Ticket.updatedAt`
- [X] T054 [P] [US4] Create `backend/src/main/java/com/ttn/tickets/web/dto/AddCommentRequest.java` with `author` and `text`. Cite `data-model.md` `Comment.author`, `Comment.text`
- [X] T055 [US4] Add `addComment` to `backend/src/main/java/com/ttn/tickets/application/TicketCommandService.java`: `findByIdForUpdate` (D5), read the clock after the lock, `COMMENT_ADDED` intent in the same save
- [X] T056 [US4] Add `POST /api/tickets/{ticketId}/comments` to `backend/src/main/java/com/ttn/tickets/web/TicketController.java`

**Checkpoint**: CommentIT passes, and comments appear in the US1 detail view.

---

## Phase 6: User Story 5 - Find tickets (Priority: P3)

**Goal**: An agent lists tickets newest-updated first in pages of 20, searches by keyword, and filters by status. Keyword and status apply together.

**Independent Test**: With tickets in several statuses, search for a word that appears on one ticket and filter by status. Every result matches (SearchAndListIT).

### Tests for User Story 5 (write first; must fail before implementation)

- [X] T057 [P] [US5] Create `backend/src/test/java/com/ttn/tickets/integration/SearchAndListIT.java`: each searchable field (FR-014), case-insensitivity, exact canonical identifier match with case and whitespace ignored, partial identifier not matching, `%`, `_`, and `\` matched literally, status filter, keyword plus status together, empty result, unknown status 400 naming `status`, and at least 1,000 tickets (SC-007)

### Implementation for User Story 5

- [X] T058 [P] [US5] Create `backend/src/main/java/com/ttn/tickets/persistence/TicketSearchSpecifications.java`: case-insensitive `LIKE` over title, description, category, resolution notes, assignee, and comment body (via `EXISTS`); exact canonical identifier; `%`, `_`, `\` escaped; cancellation reason excluded (D7, D8). Cite `data-model.md` `Ticket.title`, `Ticket.description`, `Ticket.category`, `Ticket.resolutionNotes`, `Ticket.assignee`, `Ticket.status`, `Comment.text`
- [X] T059 [P] [US5] Create `backend/src/main/java/com/ttn/tickets/application/view/TicketSummary.java` and `backend/src/main/java/com/ttn/tickets/application/view/TicketPage.java` (`items`, `page`, `pageSize` 20, `totalItems`, `totalPages`)
- [X] T060 [US5] Add `list` and `search` to `backend/src/main/java/com/ttn/tickets/application/TicketQueryService.java`: order by `updatedAt DESC, id DESC`, page size 20, and the keyword and status filters combined with AND
- [X] T061 [US5] Add `GET /api/tickets` with optional `q`, `status`, and `page` query parameters to `backend/src/main/java/com/ttn/tickets/web/TicketController.java`. Reuse `@AllowedValues` for status and return 400 `VALIDATION_FAILED` naming `status` for an unknown value (FR-015)

**Checkpoint**: SearchAndListIT passes at 1,000 tickets.

---

## Final Phase: Cross-Cutting Concerns (Concurrency, Refresh Intents, Restart, Errors, Seed Data)

**Purpose**: Tests and behavior that span every story. Adds the 503 `TICKET_BUSY` path and dev seed data.

### Tests (write first)

- [X] T062 [P] Create `backend/src/test/java/com/ttn/tickets/integration/ConcurrencyIT.java`: Race 1 (two OPEN→IN_PROGRESS moves: one 200, one 409, one intent, repeated 25 times with a `CyclicBarrier`); Race 2 (edit vs. move to CLOSED/CANCELLED: only serial outcomes, no frozen edit, intent count equals accepted changes); deterministic lock test that holds `SELECT ... FOR UPDATE`, waits on `pg_stat_activity` with a timeout, commits a competing change, and checks the queued request saw it (research R-12)
- [X] T063 [P] Create `backend/src/test/java/com/ttn/tickets/integration/RefreshIntentIT.java`: an intent on commit for create, edit, comment, and every move including cancel; none for refused requests; none on rollback, using test-only DB triggers (created in the test, not in migrations) that force a failure after the ticket write and after the intent write (FR-016, SC-005)
- [X] T064 [P] Create `backend/src/test/java/com/ttn/tickets/integration/RestartPersistenceIT.java`: create data, close the Spring context, start a second context against the same container, and re-read tickets, comments, and intents (FR-004, SC-006)
- [X] T065 [P] Create `backend/src/test/java/com/ttn/tickets/integration/ErrorFormatIT.java`: every error uses the RFC 9457 shape with `errorCode`; 404 `RESOURCE_NOT_FOUND` for an unknown route; 405 and 415 framework errors; 500 returns generic text with no stack trace; 503 `TICKET_BUSY` includes `Retry-After` (research R-05, Constitution II)
- [X] T066 [P] Create `backend/src/test/java/com/ttn/tickets/integration/MigrationAndSeedIT.java`: Flyway on an empty schema with and without the seed location; schema validates against the entities; seed creates one `TICKET_CREATED` intent per seeded ticket (research R-12, D9)

### Implementation

- [X] T067 [P] Create `backend/src/main/resources/db/seed/R__seed_dev_tickets.sql` (repeatable): guarded by `WHERE NOT EXISTS (SELECT 1 FROM ticket)`; tickets in every status with their comments, and one `TICKET_CREATED` intent per seeded ticket. Cite `data-model.md` `Ticket.status`, `Refresh intent.changeType` (D9)
- [X] T068 [P] Create `backend/src/main/resources/application-dev.yml` that adds `classpath:db/seed` to the Flyway locations (plan.md Project Structure)
- [X] T069 Map lock-wait timeouts to 503 `TICKET_BUSY` with a `Retry-After` header in `backend/src/main/java/com/ttn/tickets/web/error/ApiExceptionHandler.java`, and add the code to `backend/src/main/java/com/ttn/tickets/web/error/ErrorCode.java` (D11; spec edge case)
- [X] T070 Set the lock wait bound in `backend/src/main/resources/application.yml` as `lock_timeout` on the JDBC URL, default 5 s and overridable by configuration (D11, research R-04)
- [X] T071 Final verification: run `./mvnw verify` in `backend/` on JDK 21 and walk through `specs/001-ticket-lifecycle/quickstart.md` to confirm each outcome it describes

---

## Dependencies and Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies
- **Phase 2 (Foundational)**: Depends on Phase 1. Blocks all user stories until T019 records the red run
- **Phase 3 (US1+US2)**: Depends on Phase 2. This is the MVP
- **Phase 4 (US3)**: Depends on Phase 3 (uses `Ticket`, `TicketCommandService`, and `TicketController`)
- **Phase 5 (US4)**: Depends on Phase 3. Independent of US3 at the file level, but shares `Ticket.java`, `TicketCommandService.java`, and `TicketController.java`, so run it after US3 when working alone
- **Phase 6 (US5)**: Depends on Phase 3. Independent of US3 and US4 in behavior
- **Final Phase**: Depends on all user stories. T069 and T070 change code that T062 and T065 exercise

### Within Each Story

- Tests are written and observed failing before the implementation they cover
- Domain before application, application before web
- Tasks that touch the same file (`Ticket.java`, `TicketCommandService.java`, `TicketController.java`, `ApiExceptionHandler.java`, `ErrorCode.java`, `application.yml`) run in ID order and are not marked [P]

### Parallel Opportunities

- Phase 1: T003, T004, T005, T006, T007, T008, T009, T010, T011 (different files)
- Phase 2: T012 and T013; T015 with T014; T017 and T018 once T016 is done
- Phase 3: T020 and T021 together; T022, T023, T024 together; T029, T030, T031 together; T035, T036, T037, T038, T039, T040
- Phase 4: T044 with T045, T046, T048
- Phase 5: T052 with T054
- Phase 6: T057 with T058 and T059
- Final: T062 through T068 (all different files)

### Parallel Example: Phase 3

```text
# Tests together, after Phase 2:
T020 CreateAndViewIT.java
T021 TransitionValidationIT.java

# Domain value types together:
T022 ChangeType.java
T023 RefreshIntent.java
T024 Comment.java
```

---

## Implementation Strategy

### MVP First

1. Phase 1 (Setup), then Phase 2 (red run recorded at T019)
2. Phase 3 (US1+US2): create, view, and move. Validate against CreateAndViewIT, TransitionValidationIT, and TransitionMatrixIT
3. Stop and confirm the MVP before adding edits, comments, and search

### Incremental Delivery

1. Setup and foundation
2. US1+US2 (MVP)
3. US3 (edit and frozen rules)
4. US4 (comments)
5. US5 (search and list)
6. Final phase (concurrency, outbox rollback, restart, error format, seed data)

---

## Notes

- Three artifacts sit at the repository root (`docker-compose.yml`, `.env.example`, `.gitignore`), as plan.md's Project Structure places them. They are not under `backend/`; this is the one exception to the `backend/` path rule
- Feature 002 work (outbox consumer, retries, reindex) is out of scope; see `docs/carry-over-002-ticket-assistant.md`
- Constitution Principle V (v3.0.0) is fully met by this feature's scope: refresh intent recording and rollback tests. Retry, failure surfacing, and reindex belong to feature 002 (plan.md Constitution Check)
