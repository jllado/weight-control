ALTER TABLE workouts ADD COLUMN session_reference VARCHAR(36) NULL;
UPDATE workouts SET session_reference = UUID();
ALTER TABLE workouts
    MODIFY COLUMN session_reference VARCHAR(36) NOT NULL,
    ADD CONSTRAINT uq_workouts_session_reference UNIQUE (session_reference),
    DROP INDEX uq_workouts_user_date;
