CREATE TABLE animals (
    id           UUID         PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    species      VARCHAR(100) NOT NULL,
    dangerous    BOOLEAN      NOT NULL DEFAULT FALSE,
    habitat      VARCHAR(20)  NOT NULL,
    enclosure_id UUID         NOT NULL,
    arrival_date DATE         NOT NULL,
    status       VARCHAR(20)  NOT NULL
);
