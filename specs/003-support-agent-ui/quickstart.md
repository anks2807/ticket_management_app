# Quickstart: Support Agent Web UI (003)

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Data model**: [data-model.md](./data-model.md)

Run-and-validate guide for the finished UI. Implementation lands under `/speckit-implement`.

## Prerequisites

| Need | Detail |
|------|--------|
| Node.js | **22.18.0** (`nvm use` with `frontend/.nvmrc`) |
| Backend | JDK 21, Docker, running API on **8080** ([001 quickstart](../001-ticket-lifecycle/quickstart.md), [002 quickstart](../002-ticket-assistant/quickstart.md)) |

## One-time setup (post-implement)

```bash
cd frontend
nvm use    # or fnm use
npm ci
npm run generate:api   # openapi-typescript from 001 + 002 specs
```

## Dev run

Terminal 1 — API:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Terminal 2 — UI:

```bash
cd frontend
npm run dev
```

Open the URL Vite prints (default `http://localhost:5173`). API calls go to `/api/*` (proxied to 8080).

## Automated UI tests

```bash
cd frontend
npm test
```

Expected: Vitest passes `parseProblem` and assistant/error component tests.

## Manual acceptance checklist

Backend on `dev` profile with sample tickets. Check each item; all must pass for feature sign-off.

### List (US-1)

- [ ] List loads; tickets newest-updated first
- [ ] Status filter narrows results; combine with keyword
- [ ] Empty search/filter shows empty state (not an error)
- [ ] Paging shows next portion when `totalPages` > 1

### Create (US-2)

- [ ] Valid create navigates to new ticket detail (OPEN, `allowedTransitions` present)
- [ ] Invalid category shows field error from backend; other fields retained
- [ ] Multiple field errors shown at once

### Detail (US-3)

- [ ] Detail shows full ticket + comments
- [ ] Only `allowedTransitions` appear as move actions (OPEN → IN_PROGRESS + CANCELLED)
- [ ] RESOLVED move requires resolution notes; CANCELLED requires reason
- [ ] Disallowed move shows `INVALID_TRANSITION` banner with current status + allowed list
- [ ] CLOSED ticket: field edit shows frozen; comment still works
- [ ] `TICKET_NOT_FOUND` for bad id
- [ ] `TICKET_BUSY` on edit, comment, or transition shows retry message and reload returns current ticket state

### Assistant (US-4)

- [ ] Grounded answer shows text and clickable cited ticket links
- [ ] No-match question shows no-relevant-tickets (not generic error)
- [ ] Citation failure distinct from no-relevant-tickets
- [ ] After opening a cited ticket and back, last answer still visible
- [ ] Empty/too-long question shows invalid-question treatment

### Error discipline

- [ ] No UI-only rule blocks a request the backend would accept
- [ ] Move buttons never include statuses outside `allowedTransitions`

## Scripted end-to-end flow

Use this as a single walkthrough against the dev stack (backend `dev` profile + `npm run dev`). Confirm each step before the next.

1. **Create** — Open **New ticket**, submit a valid ticket (e.g. billing question). Land on detail with status `OPEN` and non-empty `allowedTransitions`.
2. **Move to IN_PROGRESS** — Use **Move to IN PROGRESS** (no extra fields). Detail reloads with updated status and refreshed `allowedTransitions`.
3. **Comment** — Post a comment (author + text). Comment appears in the list and `updatedAt` changes.
4. **Ask** — In the assistant panel, ask a question that should match indexed tickets (e.g. mention words from the ticket title). For a grounded outcome, see answer text and cited ticket links.
5. **Open cited ticket** — Click a citation link; detail opens for that ticket. Navigate back (browser back or **Tickets**). The last assistant answer and question remain visible in the sidebar.
