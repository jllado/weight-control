ALTER TABLE workouts
    ADD COLUMN start_time TIME NULL,
    ADD COLUMN duration_minutes INT NULL,
    ADD COLUMN warm_up_minutes INT NULL,
    ADD COLUMN training_minutes INT NULL,
    ADD COLUMN stretching_minutes INT NULL;
