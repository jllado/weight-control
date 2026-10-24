insert into exercises (name, description, tracking_mode, exercise_type, built_in_image_key, primary_muscle_group)
select 'Dumbbell overhead triceps extension',
       'Stand upright and hold one dumbbell overhead with both hands. Bend your elbows to lower it behind your head, then extend your arms under control while keeping your upper arms still.',
       'REPS', 'TRAINING', 'dumbbell-overhead-triceps-extension', 'TRICEPS'
where not exists (
    select 1 from exercises where lower(name) = 'dumbbell overhead triceps extension'
);

update exercises
set built_in_image_key = 'dumbbell-overhead-triceps-extension'
where lower(name) = 'dumbbell overhead triceps extension'
  and built_in_image_key is null
  and custom_image_path is null;

insert into exercise_muscle_group_assignments (exercise_id, muscle_group, role)
select e.id, e.primary_muscle_group, 'PRIMARY'
from exercises e
where lower(e.name) = 'dumbbell overhead triceps extension'
  and e.primary_muscle_group is not null
  and not exists (
      select 1 from exercise_muscle_group_assignments a
      where a.exercise_id = e.id
        and a.muscle_group = e.primary_muscle_group
        and a.role = 'PRIMARY'
  );
