ALTER TABLE users
    ADD COLUMN weight_reminder_time TIME NOT NULL DEFAULT '05:00:00',
    ADD COLUMN blood_pressure_reminder_time TIME NOT NULL DEFAULT '05:15:00';
