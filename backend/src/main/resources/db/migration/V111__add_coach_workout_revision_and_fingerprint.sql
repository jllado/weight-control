ALTER TABLE workouts ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE workouts ADD COLUMN coach_request_fingerprint VARCHAR(64) NULL;
