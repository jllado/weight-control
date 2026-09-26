ALTER TABLE in_app_notifications
    ADD COLUMN rescheduled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE in_app_notifications
    ADD COLUMN reschedule_delivered BOOLEAN NOT NULL DEFAULT FALSE;
