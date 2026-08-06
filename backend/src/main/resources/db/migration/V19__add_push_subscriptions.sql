create table push_subscriptions (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    endpoint text not null,
    endpoint_hash char(64) not null unique,
    p256dh varchar(255) not null,
    auth varchar(255) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_push_subscriptions_user foreign key (user_id) references users (id) on delete cascade
);

create index idx_push_subscriptions_user on push_subscriptions(user_id);
