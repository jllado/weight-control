alter table users
    add column morning_check_in_reminder_time time not null default '07:30:00',
    add column midday_check_in_reminder_time time not null default '13:30:00',
    add column evening_check_in_reminder_time time not null default '20:30:00';
