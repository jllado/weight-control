create table personal_record_events (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    event_key varchar(64) not null,
    domain varchar(16) not null,
    metric varchar(48) not null,
    direction varchar(16) not null,
    kind varchar(16) not null,
    value decimal(12,2) not null,
    previous_value decimal(12,2),
    record_date date not null,
    current_record boolean not null,
    exercise_id bigint,
    subject_type varchar(16),
    subject_id bigint,
    subject_label varchar(255),
    load_kg decimal(7,2),
    source_type varchar(16) not null,
    source_id bigint,
    line_position int,
    segment_position int,
    constraint uq_personal_record_events_user_key unique (user_id, event_key),
    constraint fk_personal_record_events_user foreign key (user_id) references users (id) on delete cascade,
    constraint fk_personal_record_events_exercise foreign key (exercise_id) references exercises (id)
);

create index idx_personal_record_events_history on personal_record_events(user_id, record_date desc, id desc);
create index idx_personal_record_events_workout on personal_record_events(user_id, source_type, source_id);
create index idx_personal_record_events_metric on personal_record_events(user_id, domain, metric, exercise_id);
