CREATE SEQUENCE ticket_id_seq START 1001 INCREMENT 1;

CREATE TABLE ticket (
    id                  bigint        PRIMARY KEY,
    title               varchar(200)  NOT NULL,
    description         varchar(5000) NOT NULL,
    priority            varchar(10)   NOT NULL,
    category            varchar(10)   NOT NULL,
    assignee            varchar(100),
    status              varchar(12)   NOT NULL,
    resolution_notes    varchar(5000),
    cancellation_reason varchar(5000),
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    CONSTRAINT ticket_priority_check CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ticket_category_check CHECK (category IN ('BILLING', 'TECHNICAL', 'ACCOUNT', 'SHIPPING', 'OTHER')),
    CONSTRAINT ticket_status_check CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ticket_title_description_not_blank CHECK (btrim(title) <> '' AND btrim(description) <> ''),
    CONSTRAINT ticket_assignee_not_blank CHECK (assignee IS NULL OR btrim(assignee) <> ''),
    CONSTRAINT ticket_resolution_notes_shape CHECK (
        resolution_notes IS NULL
        OR (status IN ('RESOLVED', 'CLOSED') AND btrim(resolution_notes) <> '')
    ),
    CONSTRAINT ticket_cancellation_reason_presence CHECK (
        (status = 'CANCELLED') = (cancellation_reason IS NOT NULL)
    ),
    CONSTRAINT ticket_cancellation_reason_not_blank CHECK (
        cancellation_reason IS NULL OR btrim(cancellation_reason) <> ''
    ),
    CONSTRAINT ticket_updated_at_not_before_created CHECK (updated_at >= created_at)
);

CREATE INDEX ticket_updated_idx ON ticket (updated_at DESC, id DESC);
CREATE INDEX ticket_status_updated_idx ON ticket (status, updated_at DESC, id DESC);
