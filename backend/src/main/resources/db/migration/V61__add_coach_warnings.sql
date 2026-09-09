create table coach_warnings (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    type varchar(32) not null,
    status varchar(16) not null,
    explanation text not null,
    evidence text not null,
    action varchar(500) not null,
    onset_date date,
    reviewed_date date not null,
    resolution_rationale text,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    resolved_at timestamp(6),
    request_key varchar(36) not null,
    create_payload text not null,
    version bigint not null,
    active_type varchar(32) generated always as (case when status = 'ACTIVE' then type else null end) stored,
    constraint fk_coach_warning_user foreign key (user_id) references users(id),
    constraint uq_coach_warning_active unique (user_id, active_type),
    constraint uq_coach_warning_request unique (user_id, request_key),
    index ix_coach_warning_history (user_id, status, updated_at)
);
create table coach_warning_revisions (
    id bigint not null auto_increment primary key,
    warning_id bigint not null,
    version bigint not null,
    snapshot text not null,
    constraint fk_coach_warning_revision foreign key (warning_id) references coach_warnings(id),
    constraint uq_coach_warning_revision unique (warning_id, version)
);
