ALTER TABLE users
    ADD COLUMN weekly_summary_reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN weekly_summary_reminder_day VARCHAR(9) NOT NULL DEFAULT 'MONDAY',
    ADD COLUMN weekly_summary_reminder_time TIME NOT NULL DEFAULT '08:00:00';
