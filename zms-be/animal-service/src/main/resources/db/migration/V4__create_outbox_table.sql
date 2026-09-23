CREATE TABLE outbox_event (
    id uuid PRIMARY KEY,
    aggregate_id uuid NOT NULL,
    event_type varchar(64) NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    published_at timestamptz,
    attempts int NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_event_unpublished ON outbox_event (occurred_at) WHERE published_at IS NULL;
