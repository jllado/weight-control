alter table meals
    add column meal_time time null after meal_sequence,
    add column notes text null after fat_grams,
    add column source varchar(32) not null default 'MANUAL' after notes;

create table fasting_periods (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    start_time datetime(3) not null,
    end_time datetime(3) not null,
    notes text,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint chk_fasting_periods_time_range check (end_time > start_time),
    constraint fk_fasting_periods_user foreign key (user_id) references users (id)
);

create index idx_fasting_periods_user_start on fasting_periods(user_id, start_time);
create index idx_fasting_periods_user_end on fasting_periods(user_id, end_time);
