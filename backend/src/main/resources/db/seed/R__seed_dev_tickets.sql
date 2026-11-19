-- Dev-only repeatable seed (data-model.md Ticket.status, RefreshIntent.changeType TICKET_CREATED).
-- Guard: populate only when the ticket table is empty (research R-03, D9).

DO $seed$
DECLARE
    ts timestamptz := timezone('UTC', now());
    tid bigint;
BEGIN
    IF EXISTS (SELECT 1 FROM ticket) THEN
        RETURN;
    END IF;

    tid := nextval('ticket_id_seq');
    INSERT INTO ticket (id, title, description, priority, category, assignee, status, resolution_notes, cancellation_reason, created_at, updated_at)
    VALUES (tid, 'Dev OPEN sample', 'Sample ticket in OPEN status', 'MEDIUM', 'TECHNICAL', 'dev-agent@example.com',
            'OPEN', NULL, NULL, ts, ts);
    INSERT INTO comment (ticket_id, author, body, created_at)
    VALUES (tid, 'dev-agent@example.com', 'Initial dev comment on OPEN ticket', ts);
    INSERT INTO ticket_refresh_intent (ticket_id, change_type, created_at)
    VALUES (tid, 'TICKET_CREATED', ts);

    tid := nextval('ticket_id_seq');
    INSERT INTO ticket (id, title, description, priority, category, assignee, status, resolution_notes, cancellation_reason, created_at, updated_at)
    VALUES (tid, 'Dev IN_PROGRESS sample', 'Sample ticket in IN_PROGRESS status', 'HIGH', 'BILLING', NULL,
            'IN_PROGRESS', NULL, NULL, ts, ts);
    INSERT INTO comment (ticket_id, author, body, created_at)
    VALUES (tid, 'billing@example.com', 'Working the invoice', ts);
    INSERT INTO ticket_refresh_intent (ticket_id, change_type, created_at)
    VALUES (tid, 'TICKET_CREATED', ts);

    tid := nextval('ticket_id_seq');
    INSERT INTO ticket (id, title, description, priority, category, assignee, status, resolution_notes, cancellation_reason, created_at, updated_at)
    VALUES (tid, 'Dev RESOLVED sample', 'Sample ticket in RESOLVED status', 'LOW', 'ACCOUNT', 'account@example.com',
            'RESOLVED', 'Reset credentials and verified login', NULL, ts, ts);
    INSERT INTO comment (ticket_id, author, body, created_at)
    VALUES (tid, 'account@example.com', 'User confirmed access restored', ts);
    INSERT INTO ticket_refresh_intent (ticket_id, change_type, created_at)
    VALUES (tid, 'TICKET_CREATED', ts);

    tid := nextval('ticket_id_seq');
    INSERT INTO ticket (id, title, description, priority, category, assignee, status, resolution_notes, cancellation_reason, created_at, updated_at)
    VALUES (tid, 'Dev CLOSED sample', 'Sample ticket in CLOSED status', 'URGENT', 'SHIPPING', 'ops@example.com',
            'CLOSED', 'Package rerouted and delivered', NULL, ts, ts);
    INSERT INTO comment (ticket_id, author, body, created_at)
    VALUES (tid, 'ops@example.com', 'Carrier confirmed delivery', ts);
    INSERT INTO ticket_refresh_intent (ticket_id, change_type, created_at)
    VALUES (tid, 'TICKET_CREATED', ts);

    tid := nextval('ticket_id_seq');
    INSERT INTO ticket (id, title, description, priority, category, assignee, status, resolution_notes, cancellation_reason, created_at, updated_at)
    VALUES (tid, 'Dev CANCELLED sample', 'Sample ticket in CANCELLED status', 'MEDIUM', 'OTHER', NULL,
            'CANCELLED', NULL, 'Duplicate request', ts, ts);
    INSERT INTO comment (ticket_id, author, body, created_at)
    VALUES (tid, 'support@example.com', 'Matched to existing ticket', ts);
    INSERT INTO ticket_refresh_intent (ticket_id, change_type, created_at)
    VALUES (tid, 'TICKET_CREATED', ts);
END;
$seed$;