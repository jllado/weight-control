create table decision_outcomes (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    outcome_date date not null,
    outcome varchar(8) not null,
    created_at timestamp not null default current_timestamp,
    constraint chk_decision_outcomes_outcome check (outcome in ('WIN', 'MISS')),
    constraint fk_decision_outcomes_user foreign key (user_id) references users (id)
);

create index idx_decision_outcomes_user_date on decision_outcomes(user_id, outcome_date, id);
