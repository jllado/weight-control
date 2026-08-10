create table back_statuses (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    status_date date not null,
    lower_pain int not null,
    lower_stiffness int not null,
    lower_activity_limitation int not null,
    middle_pain int not null,
    middle_stiffness int not null,
    middle_activity_limitation int not null,
    upper_pain int not null,
    upper_stiffness int not null,
    upper_activity_limitation int not null,
    note varchar(500),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uq_back_statuses_user_date unique (user_id, status_date),
    constraint fk_back_statuses_user foreign key (user_id) references users (id)
);

create index idx_back_statuses_user_date on back_statuses(user_id, status_date);
