create table personal_record_settings (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    metric varchar(64) not null,
    mode varchar(16) not null,
    constraint uq_personal_record_settings_user_metric unique (user_id, metric),
    constraint fk_personal_record_settings_user foreign key (user_id) references users (id) on delete cascade
);
