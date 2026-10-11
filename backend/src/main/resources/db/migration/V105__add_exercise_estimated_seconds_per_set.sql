ALTER TABLE exercises
    ADD COLUMN estimated_seconds_per_set INT NULL,
    ADD CONSTRAINT ck_exercises_estimated_seconds_per_set CHECK (estimated_seconds_per_set IS NULL OR estimated_seconds_per_set > 0);
