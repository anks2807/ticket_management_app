CREATE TABLE comment (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id  bigint        NOT NULL REFERENCES ticket (id),
    author     varchar(100)  NOT NULL,
    body       varchar(5000) NOT NULL,
    created_at timestamptz   NOT NULL,
    CONSTRAINT comment_author_not_blank CHECK (btrim(author) <> ''),
    CONSTRAINT comment_body_not_blank CHECK (btrim(body) <> '')
);

CREATE INDEX comment_ticket_idx ON comment (ticket_id, id);
