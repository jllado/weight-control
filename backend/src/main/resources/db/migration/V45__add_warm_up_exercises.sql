alter table exercises
    add column exercise_type varchar(16) not null default 'TRAINING',
    add column default_warm_up boolean not null default false,
    add column default_repetitions int;

insert into exercises (name, description, tracking_mode, exercise_type, default_warm_up, default_repetitions) values
    ('McGill Big Three', 'Modified curl-up, side plank, and bird-dog.', 'REPS', 'WARM_UP', true, 6),
    ('Light cardio', 'Easy walking, cycling, or elliptical movement to gradually raise your heart rate.', 'CARDIO', 'WARM_UP', false, null),
    ('Arm circles', 'Controlled forward and backward shoulder circles.', 'SECONDS', 'WARM_UP', false, null),
    ('Cat-cow', 'Controlled spinal flexion and extension on hands and knees.', 'REPS', 'WARM_UP', false, null),
    ('Hip circles', 'Controlled hip circles through a comfortable range of motion.', 'SECONDS', 'WARM_UP', false, null),
    ('Leg swings', 'Controlled forward-back and side-to-side leg swings.', 'REPS', 'WARM_UP', false, null),
    ('Bodyweight squats', 'Controlled unloaded squats through a comfortable range of motion.', 'REPS', 'WARM_UP', false, null),
    ('Band pull-aparts', 'Controlled band pull-aparts to prepare the upper back and shoulders.', 'REPS', 'WARM_UP', false, null);

update workout_lines set position = position + 1;

insert into workout_lines (workout_id, exercise_id, position)
select workouts.id, exercises.id, 0
from workouts cross join exercises
where exercises.name = 'McGill Big Three';

insert into workout_segments (workout_line_id, position, repetitions)
select workout_lines.id, 0, exercises.default_repetitions
from workout_lines join exercises on exercises.id = workout_lines.exercise_id
where exercises.name = 'McGill Big Three';
