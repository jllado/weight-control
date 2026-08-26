alter table fasting_periods
    add column source varchar(16) not null default 'MANUAL' after user_id,
    modify column end_time datetime null;

create index idx_fasting_periods_user_source on fasting_periods(user_id, source);
