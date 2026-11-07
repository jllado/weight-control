ALTER TABLE routines ADD COLUMN cadence VARCHAR(6) NOT NULL DEFAULT 'DAILY';
ALTER TABLE routine_reminders ADD COLUMN reminder_day VARCHAR(9);
ALTER TABLE routine_reminders ADD COLUMN reminder_day_key VARCHAR(9) AS (COALESCE(reminder_day, 'DAILY')) PERSISTENT;
ALTER TABLE routine_reminders ADD CONSTRAINT uq_routine_reminders_pair UNIQUE (routine_id, reminder_day_key, reminder_time);
ALTER TABLE routine_reminders DROP INDEX uq_routine_reminders_routine_time;
