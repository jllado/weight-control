ALTER TABLE workouts ADD COLUMN recording_key VARCHAR(36) NULL;
CREATE UNIQUE INDEX idx_workouts_user_recording_key ON workouts (user_id, recording_key);
