create table dashboard_reflections (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    reflection_date date not null,
    window_start date not null,
    window_end date not null,
    generated_at timestamp not null,
    model varchar(100) not null,
    title varchar(255) not null,
    summary text not null,
    positive_signals_json text not null,
    watchouts_json text not null,
    next_actions_json text not null,
    constraint uq_dashboard_reflections_user_date unique (user_id, reflection_date),
    constraint fk_dashboard_reflections_user foreign key (user_id) references users (id)
);
