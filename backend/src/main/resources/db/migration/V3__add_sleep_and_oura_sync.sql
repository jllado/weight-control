alter table users
    add column oura_access_token varchar(500),
    add column oura_refresh_token varchar(500),
    add column oura_token_expires_at datetime(3),
    add column oura_scopes varchar(255),
    add column oura_last_sync_at datetime(3),
    add column oura_connected_at datetime(3),
    add column oura_sync_error varchar(500);

create table sleeps (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    sleep_date date not null,
    oura_document_id varchar(255),
    sleep_score int,
    total_sleep_duration int,
    efficiency int,
    latency int,
    awake_time int,
    deep_sleep_duration int,
    rem_sleep_duration int,
    light_sleep_duration int,
    average_heart_rate decimal(5,2),
    average_hrv int,
    bedtime_start datetime(3),
    bedtime_end datetime(3),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uq_sleeps_user_date unique (user_id, sleep_date),
    constraint fk_sleeps_user foreign key (user_id) references users (id)
);

create index idx_sleeps_user_date on sleeps(user_id, sleep_date);
