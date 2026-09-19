ALTER TABLE animals ADD COLUMN created_by VARCHAR(100);
ALTER TABLE animals ADD COLUMN updated_by VARCHAR(100);

UPDATE animals SET created_by = 'system' WHERE created_by IS NULL;

ALTER TABLE animals ALTER COLUMN created_by SET NOT NULL;
