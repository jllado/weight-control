ALTER TABLE users
    ADD COLUMN weekly_summary_delivery_day VARCHAR(9) NOT NULL DEFAULT 'MONDAY',
    ADD COLUMN weekly_summary_delivery_time TIME NOT NULL DEFAULT '08:00:00';

ALTER TABLE weekly_summaries ADD COLUMN scheduled_emailed_at TIMESTAMP NULL;
