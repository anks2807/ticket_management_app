---
description: "Task list for Support Agent Web UI (003)"
---

# Tasks: Support Agent Web UI

**Input**: Design documents from `/specs/003-support-agent-ui/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [research.md](./research.md)

**Tests**: Vitest + React Testing Library per plan — parser tests **before** `parseProblem` implementation; assistant/error component tests in Phase 2.

**Organization**: Two implementation phases (per user `/speckit-tasks` input), then a short final phase. **Forbidden**: any task that defines a client-side transition table or `allowedMoves` constant — use `Ticket.allowedTransitions` from API responses only ([data-model.md](./data-model.md)).

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Scaffold, API client, errors, list & create (MVP)

**Purpose**: Runnable `frontend/` with proxy, typed client, problem parser (test-first), routing, ticket list (US-1), create form (US-2).

**Goal**: Agent can list/search/page tickets and create tickets with field-level backend errors.

**Independent Test**: With backend on `:8080`, open list and create flows per [quickstart.md](./quickstart.md) list + create sections; `cd frontend && npm test` passes parser tests.

### Scaffold & toolchain

- [x] T001 Create `frontend/package.json` with exact (no `^`/`~`) versions for `vite`, `react`, `react-dom`, and `typescript`, plus Vitest 3, `engines.node` 22.18.0, and scripts `dev`, `test`, `generate:api`
- [x] T002 Create `frontend/.nvmrc` with `22.18.0`
- [x] T003 Create `frontend/vite.config.ts` with `@vitejs/plugin-react`, `/api` proxy to `http://localhost:8080`, and Vitest `jsdom` config
- [x] T004 Create `frontend/tsconfig.json`, `frontend/tsconfig.app.json`, and `frontend/index.html` bootstrapping `frontend/src/main.tsx`
- [x] T005 Create `frontend/src/main.tsx` mounting React 18 with `frontend/src/App.tsx`
- [x] T006 Create `frontend/src/index.css` with minimal desktop layout styles

### API types & client

- [x] T007 [P] Add codegen script in `frontend/package.json` writing `frontend/src/api/schema-001.ts` from `specs/001-ticket-lifecycle/contracts/openapi.yaml` (openapi-typescript)
- [x] T008 [P] Add codegen script in `frontend/package.json` writing `frontend/src/api/schema-002.ts` from `specs/002-ticket-assistant/contracts/openapi.yaml`
- [x] T009 Run codegen and commit generated `frontend/src/api/schema-001.ts` and `frontend/src/api/schema-002.ts` (or hand-written equivalents matching OpenAPI until codegen runs)
- [x] T010 [P] Create `frontend/src/lib/openapiEnums.ts` exporting Priority/Category/TicketStatus option labels from generated schema unions only (data-model.md API shapes; no lifecycle logic)
- [x] T011 Implement `frontend/src/api/client.ts` with `fetch` helpers `listTickets`, `createTicket` typed via schema-001 (relative `/api` paths)

### Problem parser (test-first)

- [x] T012 [P] Create `frontend/src/errors/__fixtures__/problems.json` mirroring backend problem shapes from [contracts/error-treatment.md](./contracts/error-treatment.md)
- [x] T013 [P] Write failing Vitest tests in `frontend/src/errors/parseProblem.test.ts` for each `ParseProblemResult` variant (data-model.md: fieldErrors, invalidTransition, frozen, notFound, busy, generic)
- [x] T014 Implement `frontend/src/errors/types.ts` discriminated union for parse results per data-model.md
- [x] T015 Implement `frontend/src/errors/parseProblem.ts` until `frontend/src/errors/parseProblem.test.ts` passes (map `errorCode` per error-treatment contract; include `INVALID_TRANSITION` `currentStatus` and `allowedTransitions` from response body only)

### Shared UI & routing

- [x] T016 [P] Implement `frontend/src/components/FieldErrors.tsx` rendering data-model.md `fieldErrors` map under inputs
- [x] T017 [P] Implement `frontend/src/components/ProblemAlert.tsx` for invalidTransition, frozen, notFound, busy, and generic variants (no allowed-moves constants; show transition lists from parsed problem body only)
- [x] T018 Implement `frontend/src/routes.tsx` with `/`, `/tickets/new`, `/tickets/:ticketId` per [contracts/ui-routes.md](./contracts/ui-routes.md)
- [x] T019 Implement `frontend/src/App.tsx` with router, nav links, layout shell, and placeholder `AssistantSession` state holder (data-model.md AssistantSession)
- [x] T020 [P] Implement `frontend/src/components/EnumSelect.tsx` in `frontend/src/components/EnumSelect.tsx` using `frontend/src/lib/openapiEnums.ts` only

### User Story 1 — List tickets (Priority: P1)

**Independent Test**: List loads; search + status filter + paging match backend; empty list is not an error; failed load shows retry message (spec US-1).

- [x] T021 [US1] Implement `frontend/src/pages/TicketListPage.tsx` with `ListQuery` state (data-model.md ListQuery: `q`, `status`, `page`) calling `listTickets`
- [x] T022 [P] [US1] Implement `frontend/src/components/TicketListFilters.tsx` for keyword, status filter (enum options from `frontend/src/lib/openapiEnums.ts`), and page controls
- [x] T023 [P] [US1] Implement `frontend/src/components/TicketListTable.tsx` displaying `TicketSummary` rows from `TicketPage.items`
- [x] T024 [US1] Add empty-state and load-error handling in `frontend/src/pages/TicketListPage.tsx` using `frontend/src/components/ProblemAlert.tsx` where applicable

### User Story 2 — Create ticket (Priority: P1)

**Independent Test**: Valid create navigates to detail; invalid fields show backend messages; draft retained on error (spec US-2).

- [x] T025 [US2] Implement `frontend/src/pages/CreateTicketPage.tsx` with `CreateFormDraft` (data-model.md) and `CreateTicketRequest` fields
- [x] T026 [US2] Wire `createTicket` submit, success navigation to `/tickets/:ticketId`, and `VALIDATION_FAILED` field errors via `frontend/src/components/FieldErrors.tsx` in `frontend/src/pages/CreateTicketPage.tsx`; on **any** error, keep all entered create-form values (data-model.md CreateFormDraft)
- [x] T027 [US2] Add `getTicket` to `frontend/src/api/client.ts` and implement read-only `frontend/src/pages/TicketDetailPlaceholderPage.tsx` (identifier, title, status from GET); register `/tickets/:ticketId` in `frontend/src/routes.tsx` (replaced by full detail in T028)

**Checkpoint Phase 1**: `npm run dev` + backend dev profile → list + create workflows including navigation to placeholder detail; `npm test` green for parser.

---

## Phase 2: Detail, transitions, assistant (P1 work + P2 assistant)

**Purpose**: Ticket detail/edits/comments/moves from `allowedTransitions` only; assistant panel with outcome-specific UI and Vitest coverage.

**Goal**: Full agent workflow except manual polish; assistant last result survives navigation (spec US-3, US-4).

**Independent Test**: Detail edit/comment/transition; assistant ask outcomes; `npm test` includes outcome/error component tests.

### User Story 3 — Work a ticket (Priority: P1)

**Independent Test**: Detail shows ticket; moves only from `allowedTransitions`; frozen/transition errors from parser; comments append (spec US-3).

- [x] T028 [US3] Replace `frontend/src/pages/TicketDetailPlaceholderPage.tsx` with full `frontend/src/pages/TicketDetailPage.tsx` (not-found via `frontend/src/components/ProblemAlert.tsx`); extend `frontend/src/api/client.ts` with `editTicket`, `addComment`, `transitionTicket` (PATCH merge patch, transition POST)
- [x] T029 [P] [US3] Implement `frontend/src/components/TicketDetailView.tsx` showing all ticket fields and comments from GET response
- [x] T030 [P] [US3] Implement `frontend/src/components/TicketEditForm.tsx` submitting `EditTicketRequest` via `editTicket` (no status edits); on `VALIDATION_FAILED`, apply `frontend/src/components/FieldErrors.tsx` and retain `DetailDraft` (data-model.md) on failure
- [x] T031 [US3] Wire edit success reload and `TICKET_FROZEN` / `RESOLUTION_NOTES_NOT_EDITABLE` in `frontend/src/pages/TicketDetailPage.tsx`
- [x] T032 [P] [US3] Implement `frontend/src/components/CommentForm.tsx` for `AddCommentRequest` fields (data-model.md)
- [x] T033 [US3] Wire comment POST and comment list refresh in `frontend/src/pages/TicketDetailPage.tsx`
- [x] T034 [P] [US3] Implement `frontend/src/components/TransitionButtons.tsx` rendering one control per entry in `ticket.allowedTransitions` only (never derive moves from `TicketStatus` enum)
- [x] T035 [P] [US3] Implement `frontend/src/components/TransitionDialog.tsx` collecting resolution notes or cancellation reason per target status; submit `TransitionRequest`
- [x] T036 [US3] Wire transition flow and `INVALID_TRANSITION` banner (parsed `allowedTransitions` from problem body) in `frontend/src/pages/TicketDetailPage.tsx`
- [x] T037 [US3] Wire `TICKET_BUSY` (503) on edit, comment, and transition in `frontend/src/pages/TicketDetailPage.tsx`: show retry message from `parseProblem` plus a reload button that re-fetches the ticket

### User Story 4 — Assistant (Priority: P2)

**Independent Test**: Grounded citations link to tickets; no-relevant-tickets distinct; citation failure distinct; invalid question via parser; last result kept when navigating (spec US-4).

- [x] T038 [US4] Extend `frontend/src/api/client.ts` with `askAssistant` typed via `frontend/src/api/schema-002.ts`
- [x] T039 [P] [US4] Implement `frontend/src/components/assistant/AskForm.tsx` for `AskRequest.question` with max length **1000** from `specs/002-ticket-assistant/contracts/openapi.yaml` / `frontend/src/api/schema-002.ts`
- [x] T040 [P] [US4] Implement `frontend/src/components/assistant/GroundedAnswer.tsx` with answer text and links to `/tickets/:ticketId` for each `AskResponse.sources[].ticketId`
- [x] T041 [P] [US4] Implement `frontend/src/components/assistant/NoRelevantTickets.tsx` for outcome `NO_RELEVANT_TICKETS`
- [x] T042 [P] [US4] Implement `frontend/src/components/assistant/CitationFailure.tsx` for outcome `CITATION_VERIFICATION_FAILED`
- [x] T043 [US4] Implement `frontend/src/components/assistant/AssistantPanel.tsx` composing ask form and outcome branches; wire `INVALID_QUESTION` via `frontend/src/errors/parseProblem.ts`
- [x] T044 [US4] Wire `AssistantSession` in `frontend/src/App.tsx` so `frontend/src/components/assistant/AssistantPanel.tsx` retains last question/outcome across route changes (data-model.md)

### Vitest — error & outcome rendering (Phase 2)

- [x] T045 [P] Write Vitest + RTL tests in `frontend/src/components/ProblemAlert.test.tsx` for invalidTransition, frozen, busy, and generic renderings
- [x] T046 [P] Write Vitest + RTL tests in `frontend/src/components/assistant/GroundedAnswer.test.tsx`, `frontend/src/components/assistant/NoRelevantTickets.test.tsx`, and `frontend/src/components/assistant/CitationFailure.test.tsx`

**Checkpoint Phase 2**: Scripted manual flow works with backend; `npm test` passes parser + component tests.

---

## Phase 3: Polish & documentation (final)

**Purpose**: Acceptance checklist and repo docs.

- [x] T047 Add a **Scripted end-to-end flow** section only to `specs/003-support-agent-ui/quickstart.md` (create → IN_PROGRESS → comment → ask → open cited ticket); do not rewrite existing checklist sections
- [x] T048 Add **Frontend** section to repository `README.md` covering Node 22.18.0, `frontend/` dev/test commands, and proxy to backend `:8080`

---

## Dependencies & Execution Order

### Phase order

1. **Phase 1** (T001–T027): Sequential toolchain → codegen/client → parser tests (T013) before parser impl (T015) → shared UI → US1 → US2 → placeholder detail (T027).
2. **Phase 2** (T028–T046): T028 replaces placeholder; US3 includes T037 busy handling; US4 after cited-ticket routes; component tests after components exist.
3. **Phase 3** (T047–T048): After Phase 2 checkpoints.

### User story mapping

| Story | Phase | Tasks |
|-------|-------|-------|
| US1 List | 1 | T021–T024 |
| US2 Create + placeholder detail | 1 | T025–T027 |
| US3 Detail / edit / comment / transition | 2 | T028–T037 |
| US4 Assistant | 2 | T038–T046 |

### Parallel opportunities ([P])

| Group | Tasks |
|-------|-------|
| Codegen scripts | T007, T008 |
| Parser fixtures + tests | T012, T013 |
| Shared components | T016, T017, T020 |
| List subcomponents | T022, T023 |
| Detail subcomponents | T029–T032, T034, T035 |
| Assistant pieces | T039–T042 |
| Vitest RTL | T045, T046 |

### MVP scope

Complete **Phase 1** only → list + create + placeholder detail (US1 + US2, T027) against running backend.

### Implementation strategy

1. Finish Phase 1; validate list + create + parser unit tests.
2. Finish Phase 2; validate full agent path + component tests.
3. Run Phase 3 quickstart checklist; document in README.

## Notes

- Transition UI **must** use `ticket.allowedTransitions` from GET/POST responses — never a frontend transition matrix.
- Form enums from OpenAPI only; backend validates all submits (spec FR-010).
- No Redux, Zustand, or React Query (constitution IX).
