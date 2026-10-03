ALTER TABLE users
    ADD COLUMN weight_reminder_day VARCHAR(9) NOT NULL DEFAULT 'SATURDAY',
    ADD COLUMN blood_pressure_reminder_day VARCHAR(9) NOT NULL DEFAULT 'SATURDAY';
