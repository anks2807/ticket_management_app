# UI routes and screens (003)

| Path | Screen | Primary API |
|------|--------|-------------|
| `/` | Ticket list (search, status filter, paging) | `GET /api/tickets` |
| `/tickets/new` | Create ticket | `POST /api/tickets` |
| `/tickets/:ticketId` | Detail (edit, comments, transitions) | `GET/PATCH/POST ...` |

**Layout**: Root layout includes navigation (List | New) and **assistant panel** (always visible on desktop).

**Transitions**: Buttons rendered per `allowedTransitions`; dialog for RESOLVED (resolution notes) and CANCELLED (cancellation reason).

**Links**: Cited ticket IDs → `/tickets/:ticketId`.

Out of scope: auth routes, mobile breakpoints, operator refresh APIs.
