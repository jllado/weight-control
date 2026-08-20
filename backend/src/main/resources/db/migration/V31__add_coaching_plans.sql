create table coaching_plans (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    goal varchar(255) not null,
    principles_json text not null,
    priorities_json text not null,
    actions_json text not null,
    start_date date not null,
    review_date date,
    notes text,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uq_coaching_plans_user unique (user_id),
    constraint fk_coaching_plans_user foreign key (user_id) references users (id)
);
