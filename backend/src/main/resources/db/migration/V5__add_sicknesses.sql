create table sicknesses (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    sickness_date date not null,
    type varchar(64) not null,
    severity varchar(16) not null,
    note varchar(500),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uq_sicknesses_user_date unique (user_id, sickness_date),
    constraint fk_sicknesses_user foreign key (user_id) references users (id)
);

create index idx_sicknesses_user_date on sicknesses(user_id, sickness_date);
