create table moods (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    mood_date date not null,
    value int not null,
    note varchar(500),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uq_moods_user_date unique (user_id, mood_date),
    constraint fk_moods_user foreign key (user_id) references users (id)
);

create index idx_moods_user_date on moods(user_id, mood_date);
