create table exercises (
    id bigint not null auto_increment primary key,
    name varchar(255) not null,
    description varchar(500) not null,
    tracking_mode varchar(16) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uq_exercises_name unique (name)
);

create table workouts (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    workout_date date not null,
    note varchar(500),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uq_workouts_user_date unique (user_id, workout_date),
    constraint fk_workouts_user foreign key (user_id) references users (id)
);

create index idx_workouts_user_date on workouts(user_id, workout_date);

create table workout_lines (
    id bigint not null auto_increment primary key,
    workout_id bigint not null,
    exercise_id bigint not null,
    position int not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uq_workout_lines_workout_exercise unique (workout_id, exercise_id),
    constraint fk_workout_lines_workout foreign key (workout_id) references workouts (id) on delete cascade,
    constraint fk_workout_lines_exercise foreign key (exercise_id) references exercises (id)
);

create index idx_workout_lines_workout_position on workout_lines(workout_id, position);

create table workout_segments (
    id bigint not null auto_increment primary key,
    workout_line_id bigint not null,
    position int not null,
    repetitions int,
    duration_seconds int,
    weight decimal(7,2),
    speed_kph decimal(6,2),
    incline_percent decimal(6,2),
    resistance_level int,
    calories int,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_workout_segments_line foreign key (workout_line_id) references workout_lines (id) on delete cascade
);

create index idx_workout_segments_line_position on workout_segments(workout_line_id, position);

insert into exercises (name, description, tracking_mode) values
    ('Pull-up', 'Vertical bodyweight pull from a bar.', 'REPS'),
    ('Chin-up', 'Vertical pull with an underhand grip.', 'REPS'),
    ('Push-up', 'Horizontal bodyweight press from the floor.', 'REPS'),
    ('Squat', 'Lower-body squat with bodyweight or external load.', 'REPS'),
    ('Bulgarian split squat', 'Rear-foot-elevated split squat for single-leg strength.', 'REPS'),
    ('Box step-up', 'Step onto a box or bench one leg at a time.', 'REPS'),
    ('Deadlift', 'Hip hinge lift from the floor.', 'REPS'),
    ('Bench press', 'Horizontal press performed lying on a bench.', 'REPS'),
    ('Overhead press', 'Vertical press from shoulder level overhead.', 'REPS'),
    ('Barbell row', 'Bent-over row for upper back and lats.', 'REPS'),
    ('Jefferson curl', 'Slow loaded spinal flexion for hamstring and back mobility.', 'REPS'),
    ('Weighted dip', 'Parallel bar dip performed with added weight.', 'REPS'),
    ('Dead bug', 'Core control exercise with alternating arm and leg movement.', 'REPS'),
    ('Plank', 'Static core brace held in a straight line.', 'SECONDS'),
    ('Wall sit', 'Isometric squat hold against a wall.', 'SECONDS'),
    ('Parallel bar support hold', 'Isometric support hold on dip bars.', 'SECONDS'),
    ('Banded hip abduction', 'Hip abduction against an elastic band.', 'REPS'),
    ('Band lateral raise', 'Shoulder lateral raise using an elastic band.', 'REPS'),
    ('Abdominal crunch', 'Basic trunk flexion exercise for the abdominals.', 'REPS'),
    ('Walking', 'Steady-state walking cardio.', 'CARDIO'),
    ('Running', 'Steady-state or interval running cardio.', 'CARDIO'),
    ('Exercise bike', 'Cardio on a stationary bike.', 'CARDIO'),
    ('Elliptical', 'Cardio on an elliptical trainer.', 'CARDIO');
