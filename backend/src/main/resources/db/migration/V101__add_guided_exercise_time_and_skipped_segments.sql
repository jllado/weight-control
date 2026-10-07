ALTER TABLE workout_lines
    ADD COLUMN exercise_duration_seconds INT NULL;

ALTER TABLE workout_segments
    ADD COLUMN skipped BOOLEAN NOT NULL DEFAULT FALSE;
