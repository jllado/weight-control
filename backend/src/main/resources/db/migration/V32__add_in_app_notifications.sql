create table in_app_notifications (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    type varchar(32) not null,
    routine_id bigint,
    reminder_date date not null,
    period varchar(16),
    title varchar(255) not null,
    message varchar(500) not null,
    available_at datetime(3) not null,
    dismissed_at datetime(3),
    deduplication_key varchar(128) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uq_in_app_notifications_user_key unique (user_id, deduplication_key),
    constraint fk_in_app_notifications_user foreign key (user_id) references users (id) on delete cascade,
    constraint fk_in_app_notifications_routine foreign key (routine_id) references routines (id) on delete cascade
);

create index idx_in_app_notifications_pending on in_app_notifications(user_id, reminder_date, dismissed_at, available_at);
