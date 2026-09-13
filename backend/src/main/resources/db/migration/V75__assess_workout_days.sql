alter table workout_assessments
    add column user_id bigint null,
    add column workout_date date null;

-- Session feedback cannot be reused as an assessment of a multi-session day.
delete a from workout_assessments a
join workouts w on w.id = a.workout_id
join (select user_id, workout_date from workouts group by user_id, workout_date having count(*) > 1) multiple
    on multiple.user_id = w.user_id and multiple.workout_date = w.workout_date;

update workout_assessments a join workouts w on w.id = a.workout_id
set a.user_id = w.user_id, a.workout_date = w.workout_date;

alter table workout_assessments
    drop foreign key fk_workout_assessments_workout,
    drop index uq_workout_assessments_workout,
    drop column workout_id,
    modify column user_id bigint not null,
    modify column workout_date date not null,
    add constraint uq_workout_assessments_day unique (user_id, workout_date),
    add constraint fk_workout_assessments_user foreign key (user_id) references users (id) on delete cascade;
