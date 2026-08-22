alter table workouts
    modify column updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6);

alter table coaching_plans
    modify column updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6);

create table workout_assessments (
    id bigint not null auto_increment primary key,
    workout_id bigint not null,
    goal_alignment_score int not null,
    estimated_training_demand_score int not null,
    rationale text not null,
    strength text not null,
    improvement text not null,
    next_workout_action text not null,
    goal_snapshot varchar(255) not null,
    plan_updated_at timestamp(6) not null,
    workout_updated_at timestamp(6) not null,
    created_at timestamp(6) not null default current_timestamp(6),
    updated_at timestamp(6) not null default current_timestamp(6) on update current_timestamp(6),
    constraint uq_workout_assessments_workout unique (workout_id),
    constraint chk_workout_assessments_goal_alignment check (goal_alignment_score between 1 and 10),
    constraint chk_workout_assessments_training_demand check (estimated_training_demand_score between 1 and 10),
    constraint fk_workout_assessments_workout foreign key (workout_id) references workouts (id) on delete cascade
);
