CREATE TABLE deceased_animals (
    animal_id      UUID                     PRIMARY KEY,
    event_id       UUID                     NOT NULL,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
