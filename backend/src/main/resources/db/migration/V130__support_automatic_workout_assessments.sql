alter table workout_assessments
    add column automatic boolean not null default false,
    modify column plan_updated_at timestamp(6) null;
