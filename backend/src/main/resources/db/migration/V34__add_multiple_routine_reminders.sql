create table routine_reminders (
    id bigint not null auto_increment primary key,
    routine_id bigint not null,
    reminder_time time not null,
    reminder_snoozed_until datetime(3),
    constraint uq_routine_reminders_routine_time unique (routine_id, reminder_time),
    constraint fk_routine_reminders_routine foreign key (routine_id) references routines (id) on delete cascade
);

create index idx_routine_reminders_time on routine_reminders(reminder_time);
create index idx_routine_reminders_snoozed_until on routine_reminders(reminder_snoozed_until);

insert into routine_reminders (routine_id, reminder_time, reminder_snoozed_until)
select id, reminder_time, reminder_snoozed_until
from routines
where reminder_time is not null;

alter table in_app_notifications add column routine_reminder_id bigint;

update in_app_notifications notification
join routine_reminders reminder on reminder.routine_id = notification.routine_id
set notification.routine_reminder_id = reminder.id,
    notification.deduplication_key = concat('ROUTINE:', reminder.id, ':', notification.reminder_date)
where notification.type = 'ROUTINE';

delete notification
from in_app_notifications notification
left join routine_reminders reminder on reminder.id = notification.routine_reminder_id
where notification.type = 'ROUTINE' and reminder.id is null;

alter table in_app_notifications
    add constraint fk_in_app_notifications_routine_reminder foreign key (routine_reminder_id) references routine_reminders (id) on delete cascade,
    drop foreign key fk_in_app_notifications_routine,
    drop column routine_id;

alter table routines
    drop index idx_routines_reminder_snoozed_until,
    drop column reminder_time,
    drop column reminder_snoozed_until;
