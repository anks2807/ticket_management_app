# Feature Specification: Support Agent Web UI

**Feature Branch**: `003-support-agent-ui`

**Created**: 2026-11-20

**Status**: Draft

**Input**: User description: "Ticket management web UI for support agents, using the existing backend capabilities from features 001 and 002. User stories: P1 list tickets with keyword search, status filter, and paging; P1 create a ticket with field-level error messages from the backend; P1 view ticket details, edit fields, add comments, and move status using only the moves the backend reports as allowed (with required resolution notes / cancellation reason); P2 ask the assistant a question and see the answer with clickable cited tickets, or a clear \"no relevant tickets found\" message. Rules: every backend error is shown meaningfully (field errors next to fields; disallowed moves and frozen tickets explained); the UI never enforces business rules the backend doesn't — it only reflects them. Out of scope: authentication, real-time updates, mobile layout."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find and browse tickets (Priority: P1)

A support agent opens the application and works through the team's ticket queue: they see tickets ordered
with the most recently updated first, move to the next page when the list is long, narrow the list to one
status, and search by a keyword or exact ticket identifier.

**Why this priority**: Agents cannot act on issues they cannot find. Browsing and search are the entry
point for every other workflow in this UI.

**Independent Test**: With several tickets of different statuses and wording in the backend, open the list,
apply a status filter, search for a keyword that matches one ticket, and page through results. Each screen
shows only what the backend returned for that query, with no extra client-side filtering that could hide
valid matches.

**Acceptance Scenarios**:

1. **Given** multiple tickets exist, **When** an agent opens the ticket list without filters, **Then** tickets
   appear in the order the backend returns (most recently updated first) and the agent can request the next
   portion when more exist than one page holds.
2. **Given** tickets in several statuses, **When** an agent chooses one status filter, **Then** only tickets in
   that status are shown, matching the backend list response.
3. **Given** tickets with different wording, **When** an agent enters a search keyword and submits, **Then** the
   list shows only tickets the backend returns for that keyword (including an exact identifier search when
   applicable).
4. **Given** an agent has both a keyword and a status selected, **When** the list is loaded, **Then** every
   shown ticket satisfies both criteria as returned by the backend.
5. **Given** no tickets match the query, **When** the list loads, **Then** the agent sees an empty state
   message, not an error.
6. **Given** the backend cannot satisfy the list request (for example service unavailable), **When** the agent
   loads or refreshes the list, **Then** a clear, non-technical message explains that tickets could not be
   loaded and the agent can try again.

---

### User Story 2 - Create a ticket (Priority: P1)

A support agent records a new customer issue by filling in the create form and submitting. When the backend
accepts the save, the agent is taken to the new ticket's detail view. When the backend rejects input, the
agent sees what to fix without losing the rest of what they typed.

**Why this priority**: Creating tickets is the primary way new work enters the system; failed creates must
be recoverable and understandable.

**Independent Test**: Submit valid create data and confirm navigation to the new ticket. Submit invalid data
(one missing required field, one invalid category, one over-length value) and confirm field-specific feedback
from the backend appears beside the matching fields and the form retains entered values where appropriate.

**Acceptance Scenarios**:

1. **Given** the agent is on the create form, **When** they submit valid title, description, priority,
   category, and optional assignee, **Then** the new ticket is shown in detail view with identifier, OPEN
   status, and saved fields as returned by the backend.
2. **Given** the backend rejects one or more fields on create, **When** the response is received, **Then**
   each named invalid field shows the backend's message next to that field, other fields keep the agent's
   entries, and no ticket is shown as created.
3. **Given** the backend reports a non-field error on create (for example temporary unavailability), **When**
   the response is received, **Then** the agent sees the backend's human-readable message in a prominent
   place, distinct from field-level validation.
4. **Given** a ticket was created successfully, **When** the agent returns to the list, **Then** the new
   ticket can be found by search or appears in the list according to backend ordering.

---

### User Story 3 - Work a ticket (view, edit, comment, move status) (Priority: P1)

A support agent opens a ticket, reads its full detail (fields, status, comments, resolution notes or
cancellation reason when present), updates editable fields, adds comments, and changes status only through
actions the backend currently allows—supplying resolution notes or a cancellation reason when required.

**Why this priority**: This is day-to-day ticket handling; the UI must mirror backend rules so agents
trust what they see and are never misled by client-side guesses.

**Independent Test**: On an OPEN ticket, perform an allowed move, an edit, and a comment; confirm the detail
view reflects backend data after each success. Attempt a disallowed move and a field edit on a frozen ticket;
confirm the backend error is shown clearly and the displayed ticket state is unchanged until a successful
reload.

**Acceptance Scenarios**:

1. **Given** a ticket exists, **When** the agent opens it, **Then** they see identifier, title, description,
   priority, assignee, category, status, resolution notes, cancellation reason (when applicable), timestamps,
   and all comments as returned by the backend.
2. **Given** the backend reports which status moves are allowed for this ticket, **When** the agent views the
   ticket, **Then** only those moves are offered as actions (the UI MUST NOT offer moves the backend did not
   report). **When** the agent completes an allowed move with required notes or reason, **Then** the detail
   view shows the updated status and saved notes or reason after success.
3. **Given** the agent selects a move that requires resolution notes or a cancellation reason, **When** they
   submit without that text, **Then** the backend's incomplete-input feedback is shown and tied to the
   required input, and the ticket display stays at the prior state until reload confirms otherwise.
4. **Given** the backend refuses a lifecycle move (disallowed from current status), **When** the response is
   received, **Then** the agent sees the current status and which moves are allowed, as provided by the
   backend, and the UI does not present the refused move as successful.
5. **Given** the ticket is not CLOSED or CANCELLED, **When** the agent edits title, description, priority,
   assignee, or category and the backend accepts, **Then** the detail view shows the updated fields; **When**
   the backend rejects the edit, **Then** field-level or overall messages from the backend are shown and the
   prior successful load remains the source of truth until refreshed.
6. **Given** the ticket is CLOSED or CANCELLED, **When** the agent attempts a field edit, **Then** the backend
   refusal is explained (frozen ticket) and no fields appear successfully changed. **When** the agent adds a
   comment, **Then** the new comment appears after a successful backend response.
7. **Given** the agent adds a comment, **When** the backend accepts, **Then** the comment appears with author,
   text, and time as returned. **When** the backend rejects (for example invalid field), **Then** feedback is
   shown and the comment list does not show a phantom entry.
8. **Given** the ticket does not exist, **When** the agent opens its detail, **Then** a clear not-found message
   is shown, distinct from validation and disallowed-move errors.
9. **Given** the backend reports the ticket is temporarily busy, **When** the agent submits an edit, comment,
   or move, **Then** the busy/unavailable message is shown with guidance to retry, and the agent can reload the
   ticket to see current state.

---

### User Story 4 - Ask the ticket assistant (Priority: P2)

A support agent asks a natural-language question about tickets and reads the outcome: a grounded answer with
cited tickets they can open, an explicit no-relevant-tickets result, or a clear error when the question or
service response is invalid.

**Why this priority**: The assistant speeds research once core ticket handling works; it is secondary to
list, create, and detail workflows.

**Independent Test**: With indexed tickets in the backend, ask a question that should cite known tickets and
follow each citation to ticket detail. Ask a question with no relevant matches and confirm the
no-relevant-tickets presentation. Submit an invalid question and confirm error text distinct from the empty
knowledge outcome.

**Acceptance Scenarios**:

1. **Given** the assistant returns a grounded answer, **When** the agent views the result, **Then** they see
   the answer text and each cited ticket identifier as an actionable link that opens that ticket's detail view.
2. **Given** the assistant returns no-relevant-tickets, **When** the agent views the result, **Then** they see
   an explicit message that no relevant tickets were found (not a generic error and not an empty answer that
   implies facts).
3. **Given** the assistant returns citation verification failure, **When** the agent views the result, **Then**
   they see a clear failure message and no answer presented as trustworthy grounded content.
4. **Given** the question is rejected as invalid input, **When** the agent submits it, **Then** the backend
   error is shown and is distinguishable from the no-relevant-tickets outcome.
5. **Given** the agent is viewing an answer, **When** they navigate to a cited ticket and return, **Then** they
   can still read the last question and result in the assistant area until they start a new question (no
   requirement for real-time refresh of answers).

---

### Edge Cases

- List or detail request fails due to network or server error → show a recoverable error with retry or
  navigation back; do not show stale data as current without indication.
- Backend returns pagination with no further pages → hide or disable "next" according to backend signals;
  the UI MUST NOT invent page sizes or ordering unlike the backend.
- Create or edit returns multiple field errors → all named fields show messages simultaneously.
- Allowed moves list is empty (CLOSED or CANCELLED) → no status move actions offered; messaging may note that
  the ticket is terminal; comments remain available per backend rules.
- Move form: agent enters notes then backend refuses for concurrency → show disallowed-move or busy error from
  backend; reload ticket to refresh allowed moves.
- Search with leading/trailing spaces → submit as entered; backend rules apply (identifier exact match
  trimming per feature 001).
- Assistant ask while ticket list is open → failures on ask do not corrupt list state.
- Very long ticket text in detail → readable layout on desktop; no mobile-specific layout required.
- Empty assignee on create and edit → display matches backend (blank or explicit "unassigned" label if the UI
  uses labels, without blocking optional assignee).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The UI MUST let an agent list tickets using the backend's list capability, including successive
  portions (paging), ordering, keyword search, and optional status filter, alone or combined.
- **FR-002**: The UI MUST let an agent create a ticket with title, description, priority, category, and
  optional assignee, submitting values the backend accepts for those fields.
- **FR-003**: The UI MUST let an agent open one ticket and display all fields and comments the backend returns
  for that ticket.
- **FR-004**: The UI MUST let an agent edit ticket fields only through the backend edit action, and only when
  the backend allows edits for that ticket's current state.
- **FR-005**: The UI MUST let an agent add a comment with author and text through the backend comment action.
- **FR-006**: The UI MUST let an agent change status only through the backend lifecycle move action, and MUST
  collect resolution notes or cancellation reason when the backend requires them for that move.
- **FR-007**: The UI MUST determine which status move actions to show from the `allowedTransitions` field of the
  backend ticket response for the loaded ticket. It MUST NOT hide moves the backend allows, and MUST NOT enable
  moves the backend does not allow.
- **FR-008**: The UI MUST render backend validation and field errors adjacent to the corresponding input fields
  whenever the backend identifies fields by name.
- **FR-009**: The UI MUST present backend errors for disallowed lifecycle moves, missing tickets, temporarily
  busy tickets, and malformed input using the backend's messages and error kinds so agents can distinguish them
  without relying on guesswork.
- **FR-010**: The UI MUST NOT implement ticket lifecycle rules, field freeze rules, or search matching logic beyond
  displaying backend responses. Option lists for priority, category, and status MAY be derived from the published
  API contract (OpenAPI enums); that is presentation convenience, not business-rule enforcement—the backend
  remains the validator. Optional client-side hints MUST NOT block submissions the backend might still accept or
  accept actions the backend would reject.
- **FR-011**: After any successful create, edit, comment, or status move, the UI MUST show ticket state from a
  fresh backend read or from the success response body when it contains the authoritative ticket snapshot.
- **FR-012**: The UI MUST let an agent submit one natural-language question to the assistant feature and display
  grounded answers with cited ticket identifiers, no-relevant-tickets outcomes, citation verification failure,
  and invalid-question errors according to the backend response shape.
- **FR-013**: The UI MUST make each cited ticket identifier in a grounded answer navigable to that ticket's
  detail view.
- **FR-014**: The UI MUST provide navigation among list, create, ticket detail, and assistant areas suitable for
  desktop use without requiring authentication flows in this feature.

### Key Entities

- **Ticket (view)**: Presentation of one issue: identifier, descriptive fields, status, notes and reason when
  present, timestamps, and comments—always sourced from backend reads.
- **Ticket list query**: Keyword, optional status filter, and page position the agent sends to the backend list
  capability.
- **Lifecycle action**: A status move the agent initiates with optional resolution notes or cancellation reason;
  availability comes from the backend only.
- **Assistant result**: One of grounded answer (text plus cited identifiers), no relevant tickets, citation
  verification failure, or invalid question, as returned by the assistant backend.
- **Field error**: Backend-supplied association of a field name, message, and optional error code for forms.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a structured review of ten list scenarios (unfiltered, each status filter, keyword search,
  combined filters, empty results, and paging), 100% of screens match the backend result set for the same query
  parameters.
- **SC-002**: In a review of create and edit flows covering every field named in invalid-input responses from
  feature 001, 100% show the backend message on the correct field without discarding other entered values unless
  the backend response omits them.
- **SC-003**: In a review covering every allowed and disallowed lifecycle move from every status, 100% of
  offered move actions match the backend allowed set at load time, and 100% of refused moves show the backend
  explanation without updating displayed status locally.
- **SC-004**: In a review of assistant outcomes (grounded answer, no relevant tickets, invalid question,
  citation failure), 100% are visually distinct and 100% of cited identifiers in grounded answers link to the
  correct ticket detail.
- **SC-005**: Agents completing a scripted workflow (create ticket → move to IN_PROGRESS → add comment → ask
  assistant → open cited ticket) can finish without encountering client-only validation that contradicts the
  backend in 100% of trial runs.

## Assumptions

- Features **001-ticket-lifecycle** and **002-ticket-assistant** are available and unchanged in their
  contracts; this UI is a client only.
- Agents use desktop or laptop browsers with a minimum comfortable width; responsive mobile layouts are out of
  scope.
- No login, session, or role UI is required; assignee and comment author are free-text names as in feature 001.
- Lists use the backend's default page size (20 tickets per portion per feature 001 assumptions) unless the
  backend exposes a configurable size later; the UI follows whatever the backend documents.
- Allowed status moves for a ticket come from the ticket response's `allowedTransitions` field; the UI does not
  hard-code the state machine for enabling actions.
- Real-time push updates (websockets, live list refresh) are out of scope; agents refresh or navigate to see new
  data.
- Operator-only assistant capabilities (refresh queue status, full rebuild) are out of scope unless added in a
  later feature; agents use question-answer only.
- The UI may use any web technology stack chosen in planning, provided it honors these behavioral requirements.

## Dependencies

- **001-ticket-lifecycle**: Create, read, update fields, comments, list with search/filter/paging, lifecycle
  moves, and structured error responses.
- **002-ticket-assistant**: Grounded question endpoint and response outcomes agents can interpret.

## Out of Scope

- Authentication, authorization, accounts, and role-based UI.
- Real-time updates and collaborative live editing indicators.
- Mobile-first or dedicated mobile layouts.
- Ticket delete, comment edit/delete, attachments, notifications.
- Client-side enforcement of lifecycle, categories, priorities, or search rules not reflected in backend
  responses.
- Operator refresh backlog and full rebuild screens (assistant feature P2 operator story).
- Offline use and installable progressive web app behavior.
