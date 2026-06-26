create table calories (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    calorie_date date not null,
    calories int not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uq_calories_user_date unique (user_id, calorie_date),
    constraint fk_calories_user foreign key (user_id) references users (id)
);

create index idx_calories_user_date on calories(user_id, calorie_date);
