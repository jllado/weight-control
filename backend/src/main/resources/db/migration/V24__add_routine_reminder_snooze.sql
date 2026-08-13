alter table routines add column reminder_snoozed_until datetime(3) null;

create index idx_routines_reminder_snoozed_until on routines(reminder_snoozed_until);
