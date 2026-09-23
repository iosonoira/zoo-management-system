CREATE TABLE notifications (
    id          UUID         PRIMARY KEY,
    event_id    UUID         NOT NULL,
    animal_id   UUID         NOT NULL,
    event_type  VARCHAR(30)  NOT NULL,
    severity    VARCHAR(20)  NOT NULL,
    message     VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_notifications_event_id UNIQUE (event_id)
);
