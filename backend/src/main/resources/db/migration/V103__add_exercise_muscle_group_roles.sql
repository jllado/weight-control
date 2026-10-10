create table exercise_muscle_group_assignments (
    exercise_id bigint not null,
    muscle_group varchar(16) not null,
    role varchar(16) not null,
    primary key (exercise_id, muscle_group),
    constraint fk_exercise_muscle_group_assignments_exercise foreign key (exercise_id) references exercises (id),
    constraint ck_exercise_muscle_group_assignments_group check (muscle_group in ('CHEST', 'BACK', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'FOREARMS', 'CORE', 'GLUTES', 'QUADRICEPS', 'HAMSTRINGS', 'CALVES')),
    constraint ck_exercise_muscle_group_assignments_role check (role in ('PRIMARY', 'SECONDARY'))
);

insert into exercise_muscle_group_assignments (exercise_id, muscle_group, role)
select id, primary_muscle_group, 'PRIMARY' from exercises where primary_muscle_group is not null;

-- Secondary roles use 0.5 equivalent-set credit as an explicit accounting convention, not a physiological intensity estimate.
insert into exercise_muscle_group_assignments (exercise_id, muscle_group, role)
select e.id, mappings.muscle_group, 'SECONDARY'
from exercises e
join (
    select 'push-up' exercise_name, 'TRICEPS' muscle_group union all select 'push-up', 'SHOULDERS'
    union all select 'bench press', 'TRICEPS' union all select 'bench press', 'SHOULDERS'
    union all select 'weighted dip', 'CHEST' union all select 'weighted dip', 'SHOULDERS'
    union all select 'suspension chest press', 'TRICEPS' union all select 'suspension chest press', 'SHOULDERS'
    union all select 'pull-up', 'BICEPS' union all select 'pull-up', 'FOREARMS'
    union all select 'chin-up', 'BICEPS' union all select 'chin-up', 'FOREARMS'
    union all select 'barbell row', 'BICEPS' union all select 'barbell row', 'SHOULDERS'
    union all select 'roman chair back extension', 'GLUTES' union all select 'roman chair back extension', 'HAMSTRINGS'
    union all select 'overhead press', 'TRICEPS'
    union all select 'half-kneeling single-arm dumbbell press', 'TRICEPS' union all select 'half-kneeling single-arm dumbbell press', 'CORE'
    union all select 'band pull-aparts', 'BACK'
    union all select 'curl', 'FOREARMS'
    union all select 'parallel bar support hold', 'SHOULDERS' union all select 'parallel bar support hold', 'CHEST'
    union all select 'plank', 'SHOULDERS'
    union all select 'hip thrust', 'HAMSTRINGS'
    union all select 'squat', 'GLUTES'
    union all select 'bulgarian split squat', 'GLUTES'
    union all select 'box step-up', 'GLUTES'
    union all select 'dumbbell walking lunges', 'GLUTES'
    union all select 'deadlift', 'GLUTES' union all select 'deadlift', 'QUADRICEPS' union all select 'deadlift', 'BACK' union all select 'deadlift', 'FOREARMS'
) mappings on lower(e.name) = mappings.exercise_name
where e.exercise_type = 'TRAINING' and e.tracking_mode in ('REPS', 'SECONDS');
