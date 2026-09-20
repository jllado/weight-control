-- Half-kneeling unilateral overhead press; preserve matching custom exercises.
insert into exercises (name, description, tracking_mode, exercise_type, built_in_image_key)
select seed.name, seed.description, 'REPS', 'TRAINING', seed.image_key
from (
    select 'Half-kneeling single-arm dumbbell press' as name, 'Start in a half-kneeling position with one knee on a mat and the opposite foot flat on the floor. Hold a dumbbell at shoulder height with one hand, keep your torso tall, press it overhead, then lower with control. Record repetitions for one side, then repeat on the other side.' as description, 'half-kneeling-single-arm-dumbbell-press' as image_key
) seed
where not exists (select 1 from exercises where lower(exercises.name) = lower(seed.name));
