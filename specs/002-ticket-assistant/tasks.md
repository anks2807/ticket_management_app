# Tasks: Ticket Assistant (Grounded Q&A)

**Input**: Design documents from `/specs/002-ticket-assistant/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/openapi.yaml, quickstart.md. Feature **001** lifecycle backend must be present.

**Tests**: Included (Constitution III/IV/V). Phase 2 is red-first grounding tests on stubs before ask/refresh implementation. `./mvnw verify` must pass **without** AWS credentials; `@Tag("llm")` excluded by default.

**Organization**: Phase 1 setup → Phase 2 red-first grounding tests → Phase 3 US3 refresh → Phase 4 US1+US2 ask → Phase 5 US4 operator → Phase 6 LLM evaluation.

**Package and paths**: `backend/src/{main,test}/java/com/ttn/tickets/assistant/` for 002 code; shared integration base stays `com.ttn.tickets.integration`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Parallel when different files and no dependency on incomplete work
- **[Story]**: US1–US4 per spec.md user stories
- Field citations: `data-model.md` `Entity.column` or `` `assistant_ticket_chunk`.column ``

---

## Phase 1: Setup (Spring AI, migration, configuration, test doubles)

**Purpose**: Dependencies, schema, configuration, and test AI doubles so grounding tests can run without AWS.

- [ ] T001 Update `backend/pom.xml`: import BOM `org.springframework.ai:spring-ai-bom:2.0.2`; add `spring-ai-starter-model-bedrock-converse`, `spring-ai-starter-model-bedrock`, `spring-ai-starter-vector-store-pgvector` (plan Technical Context)
- [ ] T002 [P] Create `backend/src/main/resources/db/migration/V4__assistant_pgvector_and_refresh_processing.sql`: `CREATE EXTENSION vector` and `uuid-ossp`; table `assistant_ticket_chunk` with `id uuid PRIMARY KEY DEFAULT uuid_generate_v4()`, `content text`, `metadata jsonb`, `embedding vector(1024)`; HNSW on `embedding vector_cosine_ops`; alter `ticket_refresh_intent` add nullable columns then `UPDATE ticket_refresh_intent SET processing_status = 'PENDING', attempt_count = 0` for all existing rows, then set `processing_status` and `attempt_count` NOT NULL; add `next_attempt_at`, `processed_at`, `last_error` per data-model.md `ticket_refresh_intent.*`
- [ ] T003 [P] Create `backend/src/main/java/com/ttn/tickets/assistant/config/AssistantProperties.java` (`@ConfigurationProperties` prefix `assistant`) for retrieval, chunking, refresh, and bedrock ids/dimensions per data-model.md Configuration table
- [ ] T004 Extend `backend/src/main/resources/application.yml`: `spring.ai.vectorstore.pgvector.table-name=assistant_ticket_chunk`, `initialize-schema=false`, `dimensions=1024`, distance/index HNSW cosine; bind `assistant.*` defaults; prod-oriented bedrock model ids as env-backed placeholders only (no secrets)
- [ ] T025 [P] Create `backend/src/main/java/com/ttn/tickets/assistant/config/AssistantVectorStoreConfiguration.java`: `PgVectorStore` bean, table `assistant_ticket_chunk`, `initializeSchema=false`, dimensions from `AssistantProperties` / data-model.md `assistant_ticket_chunk.embedding` (depends on T004)
- [ ] T005 [P] Create `backend/src/test/java/com/ttn/tickets/assistant/support/DeterministicFakeEmbeddingModel.java`: fixed 1024-dim vectors derived from input hash (research R-07)
- [ ] T006 [P] Create `backend/src/test/java/com/ttn/tickets/assistant/support/RecordingChatModel.java`: implements `ChatModel`, records call count and last prompt, returns configurable stub text with `[TKT-n]` citations for tests
- [ ] T007 [P] Create `backend/src/test/java/com/ttn/tickets/assistant/config/AssistantIntegrationTestConfiguration.java`: `@TestConfiguration` exposing fake `EmbeddingModel` and `RecordingChatModel` as `@Primary` for integration tests
- [ ] T008 Update `backend/src/test/java/com/ttn/tickets/integration/PostgresIntegrationTest.java`: truncate `assistant_ticket_chunk` in `@BeforeEach`; ensure pgvector image unchanged (plan pin)
- [ ] T009 [P] Configure `backend/pom.xml` `maven-surefire-plugin` and `maven-failsafe-plugin` with `groups`/`excludedGroups` so default lifecycle excludes JUnit `@Tag("llm")` (plan D8)
- [ ] T010 [P] Create package skeleton under `backend/src/main/java/com/ttn/tickets/assistant/{config,chunking,refresh,retrieval,generation,web,web/dto}/` (plan Project Structure)
- [ ] T011 Register `AssistantProperties` and enable scheduling in `backend/src/main/java/com/ttn/tickets/TicketManagementApplication.java` or `backend/src/main/java/com/ttn/tickets/assistant/config/AssistantConfiguration.java`
- [ ] T047 Create `backend/src/main/java/com/ttn/tickets/assistant/config/AssistantBedrockChatConfiguration.java` and `backend/src/main/resources/assistant/grounding-system-prompt.txt`: production `ChatModel` with `BedrockCacheOptions` strategy `SYSTEM_ONLY` when `assistant.bedrock.prompt-caching.enabled` is true; active only outside the integration-test profile (tests use T007)
- [ ] T048 [P] Create `backend/src/test/java/com/ttn/tickets/assistant/AssistantArchitectureTest.java`: assert package `com.ttn.tickets.assistant` has no dependency on `TicketCommandService` or other 001 write paths (spec FR-007, Constitution VII)

**Checkpoint**: `./mvnw -q -DskipTests compile` succeeds; Flyway V4 applies on Testcontainers; `PgVectorStore` bean available for Phase 2 seeding (T025).

---

## Phase 2: Red-first grounding tests (stubs, no production ask logic yet)

**Purpose**: Constitution IV tests fail before `AskService` exists. Depends on Phase 1 (T005–T008, **T025**).

**Goal**: Define no-match (no ChatModel), citation subset failure, FR-016 identifier inclusion.

**Independent Test**: Three integration classes fail until Phase 4 implementation greens them.

- [ ] T012 [P] Create stub `backend/src/main/java/com/ttn/tickets/assistant/web/AiAskController.java` with `POST /api/ai/ask` returning 501 or wiring to a not-yet-implemented `AskService` (contract `specs/002-ticket-assistant/contracts/openapi.yaml`)
- [ ] T013 [P] Create `backend/src/test/java/com/ttn/tickets/integration/assistant/GroundingNoMatchIT.java`: seed chunks via `PgVectorStore` + fake embedder; ask unrelated question; expect `outcome=NO_RELEVANT_TICKETS`, no answer text; assert `RecordingChatModel` call count **0** (spec US2, FR-003)
- [ ] T014 [P] Create `backend/src/test/java/com/ttn/tickets/integration/assistant/GroundingCitationSubsetIT.java`: stub chat returns citation `TKT-9999` not in retrieved set; expect `CITATION_VERIFICATION_FAILED`; assert exactly **one** chat call (spec C1, FR-005)
- [ ] T015 [P] Create `backend/src/test/java/com/ttn/tickets/integration/assistant/GroundingIdentifierInclusionIT.java`: ticket exists but similarity below threshold; question contains `tkt-1001`; assert forced ticket's chunks are in the retrieved set passed to generation regardless of threshold; with stub chat citing that ticket, `outcome` is `GROUNDED` and `sources` contains it (spec US1 scenario 6, FR-016; plan Ask flow)
- [ ] T016 Confirm red run: `./mvnw test -Dtest=GroundingNoMatchIT,GroundingCitationSubsetIT,GroundingIdentifierInclusionIT` — all three fail until Phase 4; record in commit message

**Checkpoint**: Red tests committed and failing for documented reasons.

---

## Phase 3: User Story 3 — Knowledge stays current (Priority: P1)

**Goal**: Process refresh intents: collapse, current-state index, retry → failed.

**Independent Test**: After processor runs, chunks match ticket DB; two intents → one refresh; failures visible.

### Tests for User Story 3 (write first within phase)

- [ ] T017 [P] [US3] Create `backend/src/test/java/com/ttn/tickets/integration/assistant/RefreshProcessorIT.java`: new ticket + intent → after processor, chunk metadata matches ticket fields (data-model.md metadata keys); edited description reflected (spec US3 scenarios 1–2)
- [ ] T018 [P] [US3] Add collapse case to `RefreshProcessorIT.java`: two `PENDING` intents same `ticket_id` → one effective index, both `DONE` (FR-009)
- [ ] T019 [P] [US3] Create `backend/src/test/java/com/ttn/tickets/integration/assistant/RefreshRetryFailedIT.java`: failing embed stub → `attempt_count` increments, `next_attempt_at` backoff, then `FAILED` at `assistant.refresh.max-attempts` (data-model.md `ticket_refresh_intent.attempt_count`, `processing_status`)
- [ ] T020 [P] [US3] Create `backend/src/test/java/com/ttn/tickets/integration/assistant/RefreshRollbackIT.java`: no refresh when 001 records no intent on rollback (reuse 001 trigger pattern from `RefreshIntentIT`)

### Implementation for User Story 3

- [ ] T021 [P] [US3] Extend `backend/src/main/java/com/ttn/tickets/domain/RefreshIntent.java` with processing fields mapped to data-model.md `ticket_refresh_intent.processing_status`, `attempt_count`, `next_attempt_at`, `processed_at`, `last_error`
- [ ] T022 [P] [US3] Create `backend/src/main/java/com/ttn/tickets/assistant/chunking/TicketKnowledgeDocumentBuilder.java`: section-aware chunks per `.cursor/rules/rag-vector-store.md`; metadata `ticketId`, `status`, `priority`, `assignee`, `category`, `chunkType`
- [ ] T023 [US3] Extend `backend/src/main/java/com/ttn/tickets/persistence/RefreshIntentRepository.java` with native `@Query` claim `FOR UPDATE SKIP LOCKED` and count queries for pending/failed (plan Refresh processing)
- [ ] T024 [US3] Create `backend/src/main/java/com/ttn/tickets/assistant/refresh/RefreshIntentProcessor.java`: `@Scheduled`, collapse by ticket, load current `Ticket`+comments, delete chunks by metadata filter `ticketId`, `vectorStore.add()` in one `@Transactional` boundary, mark intents per data-model.md `processing_status` (plan D5)

**Checkpoint**: T017–T020 pass; 001 ticket saves still never call `vectorStore.add()` inside ticket transactions.

---

## Phase 4: User Stories 1 and 2 — Grounded ask + no relevant tickets (Priority: P1)

**Goal**: `POST /api/ai/ask` implements full retrieve→single-generate flow; greens Phase 2 tests.

**Independent Test**: Grounded cites ⊆ retrieved; no-match never calls chat; invalid question → 400.

### Tests for User Stories 1 and 2

- [ ] T026 [P] [US2] Extend `GroundingNoMatchIT.java` assertions for empty index (spec US2 scenario 2) and strict threshold (scenario 3)
- [ ] T027 [P] [US1] Create `backend/src/test/java/com/ttn/tickets/integration/assistant/AskGroundedIT.java`: seeded tickets; payment/shipment scenarios smoke; `sources[].ticketId` ⊆ retrieved (SC-001)
- [ ] T028 [P] [US1] Create `backend/src/test/java/com/ttn/tickets/integration/assistant/AskInvalidQuestionIT.java`: blank and >1000 chars → 400 `INVALID_QUESTION` distinct from ask outcomes (spec edge case, FR-014)

### Implementation for User Stories 1 and 2

- [ ] T029 [P] [US1] Create `backend/src/main/java/com/ttn/tickets/assistant/retrieval/QuestionValidator.java`: non-blank, max 1000 chars (spec edge case)
- [ ] T030 [P] [US1] Create `backend/src/main/java/com/ttn/tickets/assistant/retrieval/TicketIdentifierParser.java`: regex aligned with `TicketSearchSpecifications` / FR-016
- [ ] T031 [US1] Create `backend/src/main/java/com/ttn/tickets/assistant/retrieval/AskRetrievalService.java`: similarity search (`assistant.retrieval.top-k`, `similarity-threshold`); metadata fetch for forced ids; merge **before** dedupe; forced exempt from threshold (plan Ask flow, R-06)
- [ ] T032 [P] [US1] Create `backend/src/main/java/com/ttn/tickets/assistant/generation/CitationExtractor.java` and `CitationVerifier.java`: parse `[TKT-n]`; subset check → `CITATION_VERIFICATION_FAILED` without second call (spec C1)
- [ ] T033 [US1] Create `backend/src/main/java/com/ttn/tickets/assistant/generation/GroundedAnswerService.java`: static system prompt + user context; single `ChatModel.call`; map outcomes per `contracts/openapi.yaml`
- [ ] T034 [US1] Create `backend/src/main/java/com/ttn/tickets/assistant/generation/AskService.java`: orchestrate validate → retrieve → empty short-circuit → generate → verify
- [ ] T035 [P] [US1] Create DTOs `backend/src/main/java/com/ttn/tickets/assistant/web/dto/AskRequest.java`, `AskResponse.java`, `TicketSourceDto.java` per contract
- [ ] T036 [US1] Implement `backend/src/main/java/com/ttn/tickets/assistant/web/AiAskController.java`: `POST /api/ai/ask` per `specs/002-ticket-assistant/contracts/openapi.yaml`
- [ ] T037 [P] [US1] Add `INVALID_QUESTION` to `backend/src/main/java/com/ttn/tickets/web/error/ErrorCode.java` and map in `ApiExceptionHandler.java` for assistant validation failures

**Checkpoint**: T013–T015, T026–T028 green; MVP ask path usable after US3 chunks exist.

---

## Phase 5: User Story 4 — Operator status and rebuild (Priority: P2)

**Goal**: `GET /api/ai/refresh/status`, `POST /api/ai/refresh/rebuild` → 202; rebuild enqueues one `PENDING` intent per ticket.

**Independent Test**: Counts match DB; rebuild → processor catches up.

### Tests for User Story 4

- [ ] T038 [P] [US4] Create `backend/src/test/java/com/ttn/tickets/integration/assistant/RefreshStatusIT.java`: pending/failed counts match `ticket_refresh_intent` rows (data-model.md `processing_status`, FR-011)
- [ ] T039 [P] [US4] Create `backend/src/test/java/com/ttn/tickets/integration/assistant/RefreshRebuildIT.java`: `POST /api/ai/refresh/rebuild` → 202; one new `PENDING` intent per ticket; processor clears backlog; ask reflects data (spec US4, plan Rebuild)

### Implementation for User Story 4

- [ ] T040 [P] [US4] Create `backend/src/main/java/com/ttn/tickets/assistant/web/dto/RefreshStatusResponse.java` per contract
- [ ] T041 [US4] Create `backend/src/main/java/com/ttn/tickets/assistant/refresh/RefreshStatusService.java`: aggregate pending/failed counts
- [ ] T042 [US4] Create `backend/src/main/java/com/ttn/tickets/assistant/refresh/KnowledgeRebuildService.java`: insert one `PENDING` intent per ticket with `change_type = 'FIELDS_UPDATED'` (001 `ticket_refresh_intent_change_type_check`); processor does indexing (plan Rebuild)
- [ ] T043 [US4] Create `backend/src/main/java/com/ttn/tickets/assistant/web/RefreshAdminController.java`: `GET /api/ai/refresh/status`, `POST /api/ai/refresh/rebuild` per `specs/002-ticket-assistant/contracts/openapi.yaml`

**Checkpoint**: US4 scenarios 1–3 satisfied with US3 processor running.

---

## Phase 6: Evaluation seed and `@Tag("llm")` retrieval evaluation

**Purpose**: Optional Bedrock evaluation; not part of default CI.

- [ ] T044 [P] Create `backend/src/test/resources/assistant/evaluation-questions.json`: `{question, expectedTicketIds[]}` entries including FR-015 examples **"Which high-priority tickets are related to payment?"** and **"Show me similar resolved tickets"** with expected ticket IDs for hit-rate @K tuning (no separate stub-based FR-015 IT)
- [ ] T045 [P] Create `backend/src/test/java/com/ttn/tickets/integration/assistant/LlmRetrievalEvaluationIT.java`: `@Tag("llm")`; load evaluation JSON; run retrieval or ask against real Bedrock when credentials present; report hit rate @K (excluded by T009)
- [ ] T046 Run full default gate: `cd backend && ./mvnw verify` — all non-`llm` tests pass without AWS; align `specs/002-ticket-assistant/quickstart.md` if needed

---

## Dependencies & Execution Order

### Phase Dependencies

| Phase | Depends on | Delivers |
|-------|------------|----------|
| 1 Setup | 001 backend | BOM, V4, config, fakes, PgVectorStore (T025), prod chat config (T047) |
| 2 Red grounding | 1 | Failing ITs |
| 3 US3 | 1 | Indexing |
| 4 US1+US2 | 1, 2 (reds), 3 (indexed data) | Ask API |
| 5 US4 | 3 | Operator API |
| 6 Eval | 3–4 | Optional LLM metrics |

### User story mapping

| Story | Phase | MVP? |
|-------|-------|------|
| US3 (freshness) | 3 | Required before meaningful ask |
| US1 + US2 (ask) | 4 | **MVP** (with Phase 3 data) |
| US4 (operator) | 5 | P2 |

### Parallel opportunities

**Phase 1**: T002, T003, T005–T007, T009–T010 in parallel after T001; T025 after T004; T048 parallel with T047 after T011.

**Phase 2**: T013–T015 in parallel after T012.

**Phase 3**: T017–T020 parallel; T021–T022 parallel before T024.

**Phase 4**: T026–T028 parallel; T029–T030, T035, T037 parallel before T034.

**Phase 5**: T038–T040 parallel.

**Phase 6**: T044–T045 parallel.

### Parallel example (Phase 2)

```bash
# After T012 stub controller:
# Run together: GroundingNoMatchIT, GroundingCitationSubsetIT, GroundingIdentifierInclusionIT (must fail)
```

### Parallel example (Phase 1)

```bash
# After T001: migration T002, AssistantProperties T003, fake models T005-T006, failsafe tags T009 in parallel
```

## Implementation Strategy

### MVP

1. Complete Phase 1 → Phase 3 (indexed tickets).
2. Complete Phase 4 (ask + green Phase 2).
3. Stop and validate via `quickstart.md` manual smoke.

### Incremental

- Phase 5 adds operator rebuild without changing ask contracts.
- Phase 6 is opt-in Bedrock tuning only.

## Notes

- Never invoke `vectorStore.add()` from `TicketCommandService` (Constitution V / plan D5).
- Cite fields via `data-model.md` when adding constraints to entities or SQL.
- Production Bedrock chat: T047; integration tests always use T007.
- Fabricated-fact detection beyond citation-subset verification is covered by `/review-rag-output` on real answers, not by automated tests (spec FR-006).