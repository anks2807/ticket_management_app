# Research: Support Agent Web UI (003)

**Feature**: [spec.md](./spec.md) | **Date**: 2026-11-20

Phase 0 resolves toolchain pins and integration patterns for the React client.

## R-01 — Node.js version

| | |
|---|---|
| **Decision** | Pin **Node 22.18.0** (Active LTS) in `frontend/.nvmrc` and `package.json` `engines.node`. |
| **Rationale** | User requested LTS pin; plan-time shell reported **v25.8.1** (Current), which is not LTS. Team and CI should align on 22.x for stability. |
| **Alternatives** | Pin 25.x (rejected: not LTS); omit `engines` (rejected: drift risk). |

## R-02 — Type generation from backend contracts

| | |
|---|---|
| **Decision** | Use **openapi-typescript** (v7) with two scripts: `specs/001-ticket-lifecycle/contracts/openapi.yaml` → `src/api/schema-001.ts`, `specs/002-ticket-assistant/contracts/openapi.yaml` → `src/api/schema-002.ts`. |
| **Rationale** | Single typed client module; enums stay aligned with API without encoding lifecycle rules in the client. |
| **Alternatives** | Hand-written types until codegen task; merged OpenAPI (rejected: separate feature ownership). |

## R-03 — Form option lists (FR-010)

| | |
|---|---|
| **Decision** | Select options for priority/category/status from OpenAPI enums in generated types. Lifecycle **move** buttons use `ticket.allowedTransitions` only. |
| **Rationale** | Presentation convenience; backend validates on submit. |

## R-04 — Problem details parsing

| | |
|---|---|
| **Decision** | One `parseProblem` → discriminated union per [contracts/error-treatment.md](./contracts/error-treatment.md). |
| **Rationale** | Spec requires distinct error treatments; matches `ErrorCode` on backend. |

## R-05 — Assistant result retention

| | |
|---|---|
| **Decision** | `AssistantSession` state in app shell (`useState`); no Redux/React Query. |
| **Rationale** | Keep last ask result when opening cited tickets. |

## R-06 — Vite dev proxy

| | |
|---|---|
| **Decision** | Proxy `/api` → `http://localhost:8080`. |
| **Rationale** | Same-origin; no CORS change on backend. |

## R-07 — Vitest scope

| | |
|---|---|
| **Decision** | Unit tests for parser; RTL for error + assistant outcome components; manual E2E in quickstart. |
| **Alternatives** | Playwright (deferred). |
