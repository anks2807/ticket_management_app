CREATE TABLE ticket_refresh_intent (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id   bigint      NOT NULL REFERENCES ticket (id),
    change_type varchar(20) NOT NULL,
    created_at  timestamptz NOT NULL,
    CONSTRAINT ticket_refresh_intent_change_type_check CHECK (
        change_type IN ('TICKET_CREATED', 'FIELDS_UPDATED', 'COMMENT_ADDED', 'STATUS_CHANGED')
    )
);
