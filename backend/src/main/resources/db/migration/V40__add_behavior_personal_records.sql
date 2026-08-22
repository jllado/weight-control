create table habit_baselines (
    id bigint not null auto_increment primary key,
    habit_id bigint not null,
    completion_total int not null,
    current_streak int not null,
    best_streak int not null,
    last_date date,
    created_at timestamp not null default current_timestamp,
    constraint uq_habit_baselines_habit unique (habit_id),
    constraint fk_habit_baselines_habit foreign key (habit_id) references habits (id) on delete cascade
);

insert into habit_baselines (habit_id, completion_total, current_streak, best_streak, last_date)
select id, times, current_strike, best_strike, date(last_time_date) from habits;

create table habit_checkins (
    id bigint not null auto_increment primary key,
    habit_id bigint not null,
    checkin_date date not null,
    created_at timestamp not null default current_timestamp,
    constraint uq_habit_checkins_habit_date unique (habit_id, checkin_date),
    constraint fk_habit_checkins_habit foreign key (habit_id) references habits (id) on delete cascade
);

create index idx_habit_checkins_habit_date on habit_checkins(habit_id, checkin_date);

alter table personal_record_snapshots modify record_date date null;
alter table personal_record_snapshots add column subject_type varchar(16) null after exercise_id;
alter table personal_record_snapshots add column subject_id bigint null after subject_type;
alter table personal_record_snapshots add column subject_label varchar(255) null after subject_id;
