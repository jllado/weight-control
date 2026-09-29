ALTER TABLE workouts
    ADD COLUMN sauna_session BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN sauna_rounds_json LONGTEXT NULL,
    ADD COLUMN planned_sauna_rounds_json LONGTEXT NULL;
