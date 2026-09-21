CREATE TABLE medical_records (
    id            UUID          PRIMARY KEY,
    animal_id     UUID          NOT NULL,
    reason        VARCHAR(200)  NOT NULL,
    diagnosis     VARCHAR(1000) NOT NULL,
    examined_on   DATE          NOT NULL,
    veterinarian  VARCHAR(100)  NOT NULL,
    created_by    VARCHAR(100)  NOT NULL,
    updated_by    VARCHAR(100),
    version       BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_medical_records_animal_id ON medical_records (animal_id);

CREATE TABLE treatments (
    id                UUID         PRIMARY KEY,
    medical_record_id UUID         NOT NULL REFERENCES medical_records (id),
    description       VARCHAR(500) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    started_on        DATE,
    ended_on          DATE,
    created_by        VARCHAR(100) NOT NULL,
    updated_by        VARCHAR(100),
    version           BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_treatments_medical_record_id ON treatments (medical_record_id);
