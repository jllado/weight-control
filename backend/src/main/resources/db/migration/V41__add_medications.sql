create table medications (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    name varchar(255) not null,
    dose_amount decimal(10, 3) not null,
    dose_unit varchar(32) not null,
    notes text,
    start_date date not null,
    end_date date not null,
    repeat_every integer not null,
    repeat_unit varchar(16) not null,
    active boolean not null default true,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_medications_user foreign key (user_id) references users (id) on delete cascade
);

create index idx_medications_user_dates on medications(user_id, active, start_date, end_date);

create table medication_reminder_times (
    id bigint not null auto_increment primary key,
    medication_id bigint not null,
    reminder_time time not null,
    constraint uq_medication_reminder_times unique (medication_id, reminder_time),
    constraint fk_medication_reminder_times_medication foreign key (medication_id) references medications (id) on delete cascade
);

create index idx_medication_reminder_times_time on medication_reminder_times(reminder_time);

create table medication_doses (
    id bigint not null auto_increment primary key,
    medication_id bigint not null,
    scheduled_at datetime(3) not null,
    status varchar(16) not null,
    source varchar(16) not null,
    taken_at datetime(3),
    snoozed_until datetime(3),
    medication_name varchar(255) not null,
    dose_amount decimal(10, 3) not null,
    dose_unit varchar(32) not null,
    notes text,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uq_medication_doses_schedule unique (medication_id, scheduled_at),
    constraint fk_medication_doses_medication foreign key (medication_id) references medications (id) on delete cascade
);

create index idx_medication_doses_medication_schedule on medication_doses(medication_id, scheduled_at);
create index idx_medication_doses_snoozed on medication_doses(status, snoozed_until);

alter table in_app_notifications add column medication_dose_id bigint;
alter table in_app_notifications add constraint fk_in_app_notifications_medication_dose foreign key (medication_dose_id) references medication_doses (id) on delete cascade;
