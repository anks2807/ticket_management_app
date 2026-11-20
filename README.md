# Ticket Management App

Support ticket lifecycle, grounded AI assistant, and agent web UI.

## Backend

See [specs/001-ticket-lifecycle/quickstart.md](specs/001-ticket-lifecycle/quickstart.md) and [specs/002-ticket-assistant/quickstart.md](specs/002-ticket-assistant/quickstart.md). API listens on **http://localhost:8080** in local dev.

## Frontend

Agent UI for listing, creating, working tickets, and asking the assistant.

| Item | Detail |
|------|--------|
| Node.js | **22.18.0** — use `nvm use` in `frontend/` (see `frontend/.nvmrc`) |
| Location | `frontend/` |
| Dev server | `cd frontend && npm install && npm run dev` (default Vite port, usually 5173) |
| API proxy | Vite proxies `/api/*` to `http://localhost:8080` (see `frontend/vite.config.ts`) |
| Tests | `cd frontend && npm test` |
| API types | `cd frontend && npm run generate:api` (from OpenAPI specs in `specs/`) |

Start the Spring Boot backend before using the UI. Manual acceptance steps: [specs/003-support-agent-ui/quickstart.md](specs/003-support-agent-ui/quickstart.md).