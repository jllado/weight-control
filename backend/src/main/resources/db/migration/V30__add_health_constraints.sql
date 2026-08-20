create table health_constraints (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    type varchar(32) not null,
    title varchar(255) not null,
    details text not null,
    source varchar(32) not null,
    start_date date not null,
    end_date date,
    active boolean not null default true,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_health_constraints_user foreign key (user_id) references users (id)
);

create index idx_health_constraints_user_active_dates on health_constraints(user_id, active, start_date, end_date);
