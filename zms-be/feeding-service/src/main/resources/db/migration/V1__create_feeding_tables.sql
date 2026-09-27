CREATE TABLE feeding_plans (
    id             UUID         PRIMARY KEY,
    animal_id      UUID         NOT NULL,
    food           VARCHAR(100) NOT NULL,
    quantity_grams INTEGER      NOT NULL,
    feeding_times  VARCHAR(40)  NOT NULL,
    notes          VARCHAR(500),
    status         VARCHAR(20)  NOT NULL,
    started_on     DATE         NOT NULL,
    ended_on       DATE,
    created_by     VARCHAR(100) NOT NULL,
    updated_by     VARCHAR(100),
    version        BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_feeding_plans_animal_id ON feeding_plans (animal_id);

CREATE TABLE feedings (
    id             UUID                     PRIMARY KEY,
    plan_id        UUID                     NOT NULL REFERENCES feeding_plans (id),
    fed_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    quantity_grams INTEGER                  NOT NULL,
    notes          VARCHAR(500),
    recorded_by    VARCHAR(100)             NOT NULL
);

CREATE INDEX idx_feedings_plan_id ON feedings (plan_id);

CREATE TABLE deceased_animals (
    animal_id      UUID                     PRIMARY KEY,
    event_id       UUID                     NOT NULL,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
