# Implementation Plan: Support Agent Web UI

**Branch**: `003-support-agent-ui` | **Date**: 2026-11-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-support-agent-ui/spec.md` plus user `/speckit-plan` technical constraints.

## Summary

Adds a **React** single-page application under `frontend/` that consumes the existing **001** ticket lifecycle and **002** assistant REST APIs on `http://localhost:8080`. Agents list, create, work tickets, and ask the assistant. The UI **reflects** backend rules only: lifecycle move buttons come from `allowedTransitions` on the ticket response; validation and disallowed moves are shown from RFC 9457 problem responses.

Approach:

- **Stack**: React 18 + TypeScript + Vite; **react-router** for navigation; **no** global state library—`fetch` + component/local state (Constitution IX).
- **API client**: One typed module; types and enum option lists from **openapi-typescript** on `specs/001-ticket-lifecycle/contracts/openapi.yaml` and `specs/002-ticket-assistant/contracts/openapi.yaml` (or hand-maintained parity until codegen runs in tasks).
- **Dev proxy**: Vite `/api` → `http://localhost:8080` (same-origin in dev; no backend CORS change).
- **Errors**: Single problem-details parser → `errorCode`-driven UI (field errors, transition banner, frozen, not-found, busy retry, fallback).
- **Tests**: Vitest + RTL for parser and outcome/error components; manual acceptance checklist in [quickstart.md](./quickstart.md).

## Technical Context

**Language/Version**: TypeScript 5.7.x on **Node 22.18.0** (Active LTS; pinned in `frontend/package.json#engines` and `frontend/.nvmrc`)

**Pinned versions** (do not drift; plan amendment required to change):

| Component | Version | Notes |
|-----------|---------|-------|
| Node.js | **22.18.0** | Active LTS; `.nvmrc` at `frontend/.nvmrc` |
| React | 18.3.1 | |
| react-dom | 18.3.1 | |
| react-router-dom | 6.28.0 | |
| Vite | 6.0.x | `@vitejs/plugin-react` |
| Vitest | 3.0.x | `jsdom` environment |
| @testing-library/react | 16.x | |
| openapi-typescript | 7.x | Dev dependency; codegen script |

**Primary Dependencies**: `fetch` (no axios); problem+json parser only.

**Storage**: None client-side beyond in-memory React state (last assistant result retained across navigation per spec).

**Testing**: Vitest unit/component tests for `parseProblem` and assistant/ticket error presentation; no Playwright in v1 (manual checklist).

**Target Platform**: Desktop browsers; dev server default Vite port (5173) proxying API.

**Project Type**: Web application (`frontend/` + existing `backend/`).

**Constraints**: No auth UI; no mobile layout; no client lifecycle state machine; no Redux/Zustand/React Query unless spec/plan amended.

**Scale/Scope**: ~4 routes, ~8–12 presentational components, one API client module.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| # | Principle | Post-design | Notes |
|---|-----------|-------------|-------|
| I | Domain Integrity | **PASS** | Moves from `allowedTransitions`; backend validates all actions |
| II | Validation at the Boundary | **PASS** (client) | UI displays server errors; does not replace validation |
| III | Test-First | **PASS** (scope) | Backend ITs unchanged; UI tests parser + error/outcome components |
| IV | Grounded AI Only | **PASS** | Renders 002 outcomes; no client-side answer fabrication |
| V | Knowledge Freshness | N/A | No indexing in UI |
| VI | Configuration | **PASS** | Proxy target via Vite config env; no hardcoded AI params |
| VII | Bounded AI Scope | **PASS** | Ask-only; read-only ticket APIs |
| VIII | Security | **PASS** | No secrets in `frontend/` |
| IX | Simplicity | **PASS** | No state-management library; minimal deps |
| X | AI Accountability | **PASS** | UI code reviewed like hand-written |

## Decisions

| # | Decision | Ref |
|---|----------|-----|
| D1 | New `frontend/` package; backend uses 001 `allowedTransitions` | User, spec |
| D2 | Node **22.18.0** LTS pin | R-01 |
| D3 | `openapi-typescript` → `schema-001.ts` + `schema-002.ts`; thin `client.ts` | R-02 |
| D4 | Form enums from OpenAPI only (FR-010) | R-03 |
| D5 | `parseProblem` per [error-treatment.md](./contracts/error-treatment.md) | R-04 |
| D6 | Assistant last result in app shell state | R-05 |
| D7 | Vitest: parser + ProblemAlert + assistant outcome components | R-06 |

See [research.md](./research.md).

## Project Structure

### Documentation

```text
specs/003-support-agent-ui/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── backend-apis.md
│   ├── error-treatment.md
│   └── ui-routes.md
└── spec.md
```

### Source (implement phase)

```text
frontend/
├── .nvmrc
├── package.json
├── vite.config.ts
└── src/
    ├── api/client.ts
    ├── errors/parseProblem.ts
    ├── components/
    └── pages/
```

**Structure Decision**: `frontend/` SPA; `backend/` unchanged API host.

## API integration

| Operation | Method | Path |
|-----------|--------|------|
| List/search | GET | `/api/tickets?q&status&page` |
| Create | POST | `/api/tickets` |
| View | GET | `/api/tickets/{id}` |
| Edit | PATCH | `/api/tickets/{id}` |
| Comment | POST | `/api/tickets/{id}/comments` |
| Transition | POST | `/api/tickets/{id}/transitions` |
| Ask | POST | `/api/ai/ask` |

See [contracts/backend-apis.md](./contracts/backend-apis.md).

## Test strategy

Unit/fixture tests for `parseProblem`; RTL for error and assistant outcome components; manual E2E in [quickstart.md](./quickstart.md).

## Complexity Tracking

| Item | Why |
|------|-----|
| Two OpenAPI codegen outputs | 001 and 002 specs owned separately |
| App-level assistant state | Spec: keep last ask result across navigation |
