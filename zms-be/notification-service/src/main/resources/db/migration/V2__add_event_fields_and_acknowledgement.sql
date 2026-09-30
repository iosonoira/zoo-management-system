ALTER TABLE notifications ADD COLUMN performed_by      VARCHAR(100);
ALTER TABLE notifications ADD COLUMN animal_name       VARCHAR(100);
ALTER TABLE notifications ADD COLUMN species           VARCHAR(100);
ALTER TABLE notifications ADD COLUMN dangerous         BOOLEAN;
ALTER TABLE notifications ADD COLUMN previous_status   VARCHAR(30);
ALTER TABLE notifications ADD COLUMN new_status        VARCHAR(30);
ALTER TABLE notifications ADD COLUMN from_enclosure_id UUID;
ALTER TABLE notifications ADD COLUMN to_enclosure_id   UUID;
ALTER TABLE notifications ADD COLUMN acknowledged_by   VARCHAR(100);
ALTER TABLE notifications ADD COLUMN acknowledged_at   TIMESTAMPTZ;

CREATE INDEX idx_notifications_animal_id ON notifications (animal_id);
CREATE INDEX idx_notifications_occurred_at_id ON notifications (occurred_at DESC, id);
