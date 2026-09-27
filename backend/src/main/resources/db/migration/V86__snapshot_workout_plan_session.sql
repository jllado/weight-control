ALTER TABLE workouts
    ADD COLUMN planned_session_name VARCHAR(100) NULL,
    ADD COLUMN planned_targets_json LONGTEXT NULL;
