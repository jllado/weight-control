ALTER TABLE dashboard_reflections
    ADD COLUMN meals_summary VARCHAR(200) NULL,
    ADD COLUMN meals_next_action VARCHAR(120) NULL,
    ADD COLUMN workouts_summary VARCHAR(200) NULL,
    ADD COLUMN workouts_next_action VARCHAR(120) NULL;
