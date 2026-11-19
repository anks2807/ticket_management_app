# Feature Specification: Ticket Lifecycle Management

**Feature Branch**: `001-ticket-lifecycle`

**Created**: 2026-11-19

**Status**: Draft

**Input**: User description: "Ticket lifecycle management for a support team. Support agents create, view, update, comment on, find, and move tickets through their lifecycle (OPEN→IN_PROGRESS→RESOLVED→CLOSED, plus OPEN or IN_PROGRESS→CANCELLED). The feature must capture everything a later assistant needs (including category and resolution notes) and must keep that assistant's knowledge from silently going stale. Validation identifies bad fields. A malformed request and a disallowed move must be distinguishable. Data survives a restart. Out of scope: the assistant itself, the user interface, authentication and roles, deleting tickets, attachments, notifications."

## Clarifications

### Session 2026-11-19

- Q: Should this feature's acceptance stop once a refresh intent is recorded with the ticket change, kept after a restart, and omitted when that change is rolled back, leaving retry, failure visibility, rebuild, and "knowledge matches" to the assistant feature? → A: Yes. This feature stops at atomic, durable intent. Retry, failure visibility, rebuild, and knowledge-matches move to the assistant feature.
- Q: Should a ticket's category be chosen from a fixed list of values, or typed as free text? → A: Fixed list of five values: Billing, Technical, Account, Shipping, Other. An agent must choose one; any other value is refused as invalid input. The list changes only by amending this spec.
- Q: Once a ticket is CLOSED or CANCELLED, which of its details should agents still be able to change: nothing, only comments, or everything including fields? → A: Only comments. Title, description, priority, assignee, category, and resolution notes are frozen once a ticket is CLOSED or CANCELLED. Comments can still be added in any status.
- Q: Should moving a ticket to CANCELLED require a written reason, the same way moving to RESOLVED requires resolution notes? → A: Yes. A non-empty cancellation reason is required for the move to CANCELLED and is saved on the ticket as part of that move. It cannot be changed or removed afterwards.
- Q: Should keyword search also match a ticket's identifier, so that typing TKT-1001 returns that ticket? → A: Yes, for an exact identifier match only. Typing TKT-1001 returns that ticket. A partial identifier such as 1001 does not match by identifier.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create and view a ticket (Priority: P1)

An agent records a new customer issue and opens it to confirm every detail was saved.

**Why this priority**: Nothing else in the support workflow exists until an issue is recorded and can be read back in full.

**Independent Test**: Create a ticket with a title, description, priority, category, and assignee, then open it. The saved ticket shows those details, a new identifier, status OPEN, and its times. Delivers a record the team can act on.

**Acceptance Scenarios**:

1. **Given** no ticket exists for an issue, **When** an agent creates one with a title, description, priority, category, and assignee, **Then** the ticket is saved with a unique identifier of the form TKT-1001, status OPEN, the details entered, a creation time, and a last-updated time equal to the creation time.
2. **Given** a ticket was just created, **When** an agent opens it, **Then** they see the identifier, title, description, priority, assignee, category, status, resolution notes (empty), creation time, last-updated time, and an empty comment list.
3. **Given** an agent omits the assignee, **When** they create a ticket with the other required details, **Then** the ticket is saved with no assignee.
4. **Given** a ticket was created, **When** the system is restarted, **Then** opening the ticket still shows the same details.
5. **Given** a ticket is created, **When** the save succeeds, **Then** a refresh intent for that ticket is recorded in the same save and survives a restart. **Given** the save is rolled back, **Then** no refresh intent is recorded.

---

### User Story 2 - Move a ticket through its lifecycle (Priority: P1)

An agent advances a ticket as they work it, or cancels it, and is stopped when the move is not allowed.

**Why this priority**: Status is how the team knows what is waiting, in progress, solved, finished, or abandoned. A wrong status misleads both agents and the later assistant.

**Independent Test**: Starting from an existing OPEN ticket, perform each allowed move and each disallowed move. Allowed moves change only the status (and resolution notes when resolving, or the cancellation reason when cancelling). Disallowed moves leave the ticket unchanged and name the current status and the moves that are allowed.

**Acceptance Scenarios**:

1. **Given** a ticket is OPEN, **When** an agent moves it to IN_PROGRESS, **Then** the status becomes IN_PROGRESS, the last-updated time changes, and no other field changes.
2. **Given** a ticket is IN_PROGRESS, **When** an agent moves it to RESOLVED and supplies resolution notes, **Then** the status becomes RESOLVED and those notes are saved.
3. **Given** a ticket is RESOLVED, **When** an agent moves it to CLOSED, **Then** the status becomes CLOSED.
4. **Given** a ticket is OPEN or IN_PROGRESS, **When** an agent moves it to CANCELLED with a cancellation reason, **Then** the status becomes CANCELLED and the reason is saved.
5. **Given** a ticket is in any status, **When** an agent requests a move that is not allowed from that status (including moving it to the status it already has), **Then** the ticket is unchanged and the agent is told the current status and which moves are allowed. For CLOSED and CANCELLED, no moves are allowed.
6. **Given** a ticket is IN_PROGRESS, **When** an agent moves it to RESOLVED without resolution notes, **Then** the move is refused as incomplete input, the ticket stays IN_PROGRESS, and the missing notes are identified.
7. **Given** an agent sends a move request that is badly formed (for example an unrecognized status), **When** the system responds, **Then** that response is a different kind of error from a recognized status that the current ticket is not allowed to move to.
8. **Given** a status change is saved, **Then** a refresh intent for that ticket is recorded in the same save, including when the new status is CANCELLED. A refused move records no refresh intent.
9. **Given** a ticket is OPEN or IN_PROGRESS, **When** an agent moves it to CANCELLED without a cancellation reason or with a blank one, **Then** the move is refused as incomplete input, the ticket keeps its status, and the missing reason is identified.
10. **Given** a ticket is OPEN, **When** two moves from OPEN to IN_PROGRESS are saved concurrently, **Then** each is evaluated against the ticket's state at the moment it is saved. One move is saved and the status becomes IN_PROGRESS. The other is evaluated against that new state and refused as a disallowed move, and the ticket stays IN_PROGRESS.

---

### User Story 3 - Edit ticket details (Priority: P2)

An agent corrects a ticket's title, description, priority, assignee, category, or resolution notes without changing its status.

**Why this priority**: Agents need accurate details, and the later assistant depends on category and resolution notes, but the team can already record and progress work with stories 1 and 2.

**Independent Test**: Edit each editable field on an existing ticket and confirm the status, identifier, and comments stay the same. Attempting to change status through this action is refused.

**Acceptance Scenarios**:

1. **Given** an existing ticket, **When** an agent changes its title, description, priority, or assignee, **Then** only those fields and the last-updated time change.
2. **Given** an existing ticket, **When** an agent changes its category, **Then** the new category is saved and the status is unchanged.
3. **Given** a ticket has resolution notes, **When** an agent corrects them, **Then** the corrected notes are saved and cannot be cleared to empty.
4. **Given** an existing ticket, **When** an agent tries to change its status, identifier, or creation time through the edit action, **Then** the edit is refused and the ticket is unchanged.
5. **Given** an edit is saved, **Then** a refresh intent for that ticket is recorded in the same save. A refused edit records no refresh intent.
6. **Given** a ticket is CLOSED or CANCELLED, **When** an agent changes any of its fields (title, description, priority, assignee, category, or resolution notes), **Then** the edit is refused, the ticket is unchanged, and no refresh intent is recorded.

---

### User Story 4 - Comment on a ticket (Priority: P2)

An agent adds a note to a ticket so the conversation stays with the issue.

**Why this priority**: Comments carry the history the team and the later assistant rely on, after the ticket itself exists.

**Independent Test**: Add a comment to an existing ticket and open the ticket. The comment shows the author, the text, and the time, and earlier comments are unchanged.

**Acceptance Scenarios**:

1. **Given** an existing ticket, **When** an agent adds a comment with an author and text, **Then** the comment appears on the ticket with that author, that text, and the time it was added, and the ticket's last-updated time changes.
2. **Given** a ticket already has comments, **When** an agent adds another, **Then** both are shown and the earlier one is unchanged.
3. **Given** a comment was added, **When** the system is restarted, **Then** the comment is still on the ticket.
4. **Given** a comment is saved, **Then** a refresh intent for that ticket is recorded in the same save. A refused comment records no refresh intent.
5. **Given** a ticket is CLOSED or CANCELLED, **When** an agent adds a comment, **Then** the comment is saved with its author, text, and time, and a refresh intent for that ticket is recorded in the same save.

---

### User Story 5 - Find tickets (Priority: P3)

An agent looks through the team's tickets, searches by a word or phrase, and narrows the list to one status.

**Why this priority**: Finding work matters once there are many tickets. Creation, progress, edits, and comments are useful before search exists.

**Independent Test**: With several tickets of different statuses and wording, list them, search for a word that appears on only one, and filter to a single status. Each result matches what was asked.

**Acceptance Scenarios**:

1. **Given** several tickets exist, **When** an agent lists them, **Then** they are ordered with the most recently updated first, and the agent can read them in successive portions rather than only as one complete dump.
2. **Given** tickets with different wording, **When** an agent searches for a keyword, **Then** the results are the tickets whose title, description, category, resolution notes, assignee, or comment text contains that keyword, ignoring capitalization.
3. **Given** tickets in several statuses, **When** an agent filters by one status, **Then** only tickets in that status are returned.
4. **Given** an agent supplies both a keyword and a status, **When** the list is returned, **Then** every result matches both.
5. **Given** nothing matches, **When** an agent searches or filters, **Then** the result is an empty list, not an error.
6. **Given** a ticket with identifier TKT-1001 exists, **When** an agent searches for TKT-1001, **Then** that ticket is returned. **When** an agent searches for 1001, **Then** the identifier does not cause that ticket to match.

---

### Edge Cases

- A required field is missing, blank, or only spaces. The ticket is unchanged, and the response names that field.
- A value exceeds the allowed length, or priority is not one of the allowed priorities. The ticket is unchanged, and the response names the field and the reason.
- A category is not one of Billing, Technical, Account, Shipping, or Other. The ticket is unchanged, and the response names the category field and lists the allowed values.
- A lifecycle move names a status that is not one of OPEN, IN_PROGRESS, RESOLVED, CLOSED, or CANCELLED. This is reported as badly formed input, not as a disallowed move.
- A lifecycle move names a real status the ticket cannot move to, including its current status. The ticket is unchanged. The response states the current status and the allowed moves, and is a different kind of error from badly formed input.
- A ticket is CLOSED or CANCELLED. Every further move is refused, and the response states that no moves are allowed. Field edits are refused as well; comments are still accepted.
- A move to RESOLVED omits resolution notes, or supplies blank notes. The ticket stays in its current status, and the missing notes are identified as incomplete input.
- A move to CANCELLED omits the cancellation reason, or supplies a blank one. The ticket stays in its current status, and the missing reason is identified as incomplete input.
- An agent views, edits, comments on, or moves a ticket that does not exist. The response says it was not found, which is a different kind of error from both badly formed input and a disallowed move.
- An edit tries to clear resolution notes that were already saved. The edit is refused.
- Recording the refresh intent fails. The ticket change is not saved either, because both are written in the same save.
- The system restarts after a change is saved. The change and its refresh intent are both still present.
- Every lifecycle move and every field edit is evaluated against the ticket's state at the moment it is saved. Concurrent requests can never together produce a disallowed move or an edit to a frozen ticket. A request that loses such a race is evaluated against the new state (for example, it becomes a disallowed move). Last-write-wins applies only between concurrent edits of an editable ticket.
- If a ticket is held by another in-progress change for longer than a configured wait, the request is refused as temporarily unavailable with guidance to retry later; the ticket is unchanged and no refresh intent is recorded. This is a fourth kind of outcome, distinct from badly formed input, a disallowed move, and a missing ticket.
- A search keyword matches nothing, or a status filter matches no tickets. The agent gets an empty list.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST let an agent create a ticket by supplying a title, description, priority, and category. Assignee is optional. Category MUST be one of Billing, Technical, Account, Shipping, or Other.
- **FR-002**: The system MUST assign each new ticket a unique, immutable identifier of the form `TKT-` followed by a number (for example TKT-1001), set its status to OPEN, and record the creation time and last-updated time. Agents MUST NOT choose the identifier, the initial status, or these times.
- **FR-003**: The system MUST let an agent view one ticket with its identifier, title, description, priority, assignee, category, status, resolution notes, cancellation reason (once cancelled), creation time, last-updated time, and all comments.
- **FR-004**: The system MUST keep tickets, comments, and recorded refresh intents after a restart.
- **FR-005**: The system MUST check every submitted value and, when input is invalid, MUST identify each invalid field and why it is invalid. Invalid input MUST NOT change stored data.
- **FR-006**: The system MUST report a badly formed request and a disallowed lifecycle move as two different kinds of errors, so they can be told apart without relying on message wording alone.
- **FR-007**: The system MUST report a request about a ticket that does not exist as a third kind of error, distinct from badly formed input and from a disallowed move.
- **FR-008**: Changing status MUST be a separate action from editing ticket fields. The edit action MUST NOT change status, identifier, creation time, or comments. The status action MUST NOT change title, description, priority, assignee, or category.
- **FR-009**: The only allowed status moves are OPEN→IN_PROGRESS, IN_PROGRESS→RESOLVED, RESOLVED→CLOSED, OPEN→CANCELLED, and IN_PROGRESS→CANCELLED. Any other move MUST be refused, MUST leave the ticket unchanged, and MUST tell the agent the current status and the moves allowed from it. A ticket that is CLOSED or CANCELLED has no allowed moves.
- **FR-010**: Moving a ticket to RESOLVED MUST require non-empty resolution notes. Those notes are saved as part of that move. Notes that have been saved MUST NOT be cleared. Moving a ticket to CANCELLED MUST require a non-empty cancellation reason, saved as part of that move. The cancellation reason MUST NOT be changed or removed afterwards.
- **FR-011**: An agent MUST be able to change a ticket's title, description, priority, assignee, and category. A new category MUST be one of Billing, Technical, Account, Shipping, or Other. An agent MUST be able to correct resolution notes after they have been saved. Resolution notes MUST NOT be set by an edit while the ticket has none; they are only introduced by the move to RESOLVED. Each accepted edit MUST update the last-updated time. While a ticket is CLOSED or CANCELLED, no field MAY be changed, including resolution notes; such an edit MUST be refused and the ticket left unchanged.
- **FR-012**: An agent MUST be able to add a comment made of an author and text, in any ticket status including CLOSED and CANCELLED. The system MUST record the time of the comment and MUST update the ticket's last-updated time. Comments MUST NOT be edited or deleted.
- **FR-013**: An agent MUST be able to list tickets, most recently updated first, and MUST be able to request the list in successive portions.
- **FR-014**: An agent MUST be able to search tickets by a keyword. A match is a case-insensitive occurrence in the title, description, category, resolution notes, assignee, or any comment text, or an exact match to the ticket identifier (for example, TKT-1001). The exact identifier match is case-insensitive and ignores surrounding whitespace. A partial identifier match does not count. A keyword and a status filter MUST be usable together, and both then apply. No matches MUST return an empty list.
- **FR-015**: An agent MUST be able to filter the list to a single status. An unrecognized status filter is badly formed input and MUST name the status field.
- **FR-016**: Creating a ticket, changing its fields, adding a comment, and every status move including cancellation MUST record a refresh intent for that ticket in the same save as the change. A change that is refused or not saved MUST NOT record a refresh intent. Refreshing the assistant's knowledge MUST NOT be part of the save, so a slow or unavailable knowledge service cannot block or undo a ticket change.
- **FR-017**: Moved to assistant feature. Retrying failed knowledge refreshes and making outstanding refreshes visible moved to the assistant feature.
- **FR-018**: Removed in clarification session 2026-11-19. Rebuilding the assistant's knowledge moved to the assistant feature.

### Key Entities

- **Ticket**: One customer issue tracked by the team. It has a human-readable identifier, title, description, priority, assignee (optional), category, status, resolution notes (optional until the ticket is resolved), cancellation reason (present only once the ticket is cancelled), a creation time, and a last-updated time. It has zero or more comments.
- **Comment**: A note added to one ticket. It has an author, text, and the time it was added. It belongs to exactly one ticket and stays as it was written.
- **Lifecycle move**: A request to change a ticket's status along the allowed path. It is separate from a field edit. The move to RESOLVED carries resolution notes; the move to CANCELLED carries a cancellation reason.
- **Refresh intent**: A record, written in the same save as a ticket change, that the assistant's knowledge of that ticket must be brought up to date. It survives a restart. Acting on it belongs to the assistant feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-002**: In a review covering every disallowed lifecycle move from every status, 100% of those moves leave the ticket unchanged and report the current status and the allowed moves.
- **SC-003**: In a review of invalid submissions (missing fields, blank fields, values that are too long, a priority that is not allowed, and a category that is not in the fixed list), 100% name each wrong field and the reason, and 100% leave stored data unchanged.
- **SC-004**: In every reviewed case, a badly formed request, a disallowed lifecycle move, and a missing ticket are three distinguishable outcomes.
- **SC-005**: For every reviewed create, field edit, comment, and status move that is saved, a refresh intent for that ticket is recorded in the same save. For every reviewed change that is refused or rolled back, no refresh intent is recorded. Zero saved changes lack a refresh intent.
- **SC-006**: After a restart, 100% of previously saved tickets, their comments, and their recorded refresh intents are still present.
- **SC-007**: From a set of at least 1,000 tickets, an agent can find a known ticket by a keyword and can narrow the list to one status, and that ticket appears in the matching results.
- **SC-008**: 100% of accepted new tickets are viewable immediately with status OPEN and an identifier that no other ticket uses.

## Assumptions

- Agents are the only people using this feature. Distinguishing customers, leads, or administrators is out of scope, so the assignee and the comment author are names the agent types, not accounts.
- A new ticket always starts as OPEN. The agent does not set a status at creation.
- Priority is one of: Low, Medium, High, Urgent.
- Category is one of five fixed values: Billing, Technical, Account, Shipping, Other. The list is defined in this spec and changes only by amending it; this version has no way to add categories at runtime.
- Length limits: title up to 200 characters; description, resolution notes, and cancellation reason up to 5,000 characters each; assignee and comment author up to 100 characters each; comment text up to 5,000 characters. Shorter values are accepted. Empty and whitespace-only values are rejected wherever the field is required.
- Identifiers start at TKT-1001 and increase by one for each new ticket. A number is never reused. A gap in the numbers is acceptable.
- Times are recorded by the system when the change is saved, and are shown as that same moment wherever the ticket is read.
- Resolution notes may be corrected after they are first saved, but not removed.
- The cancellation reason is set by the move to CANCELLED. It cannot be corrected or removed afterwards, because cancelled tickets are frozen.
- Once a ticket is CLOSED or CANCELLED, its fields (including resolution notes) are frozen and further status moves are blocked. Comments remain allowed in every status.
- Comments are append-only.
- Search is a single keyword or phrase matched as a case-insensitive substring of the text fields, or as an exact ticket identifier. It is not a multi-filter query language.
- Lists are ordered by last-updated time, newest first, and are read in portions of 20 tickets at a time.
- Every lifecycle move and every field edit is evaluated against the ticket's state at the moment it is saved. Concurrent requests can never together produce a disallowed move or an edit to a frozen ticket. A request that loses such a race is evaluated against the new state (for example, it becomes a disallowed move). Last-write-wins applies only between concurrent edits of an editable ticket.
- "The assistant's knowledge" means the information the separate assistant feature will answer from. This feature does not answer questions. It records what that knowledge must reflect.
- Constitution Principle V requires retry, failure visibility, and a full rebuild. Those are delivered by the assistant feature, so the system does not meet Principle V until that feature is complete.

## Out of Scope

- The assistant that answers questions, including how it phrases answers.
- Retrying failed knowledge refreshes, failure visibility, and rebuilding the assistant's knowledge (assistant feature).
- Any user interface. This feature defines the behavior agents rely on, not screens.
- Authentication, accounts, and roles.
- Deleting a ticket, editing a comment, or deleting a comment.
- Attachments.
- Notifications, assignments workflows beyond storing an assignee name, and service-level timers.
