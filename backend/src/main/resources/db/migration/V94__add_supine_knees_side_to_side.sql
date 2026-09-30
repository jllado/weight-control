-- Add the bilateral supine stretch while preserving matching custom exercises.
insert into exercises (name, description, tracking_mode, exercise_type, built_in_image_key)
select seed.name, seed.description, 'SECONDS', 'STRETCHING', seed.image_key
from (
    select 'Supine knees side to side' as name, 'Lie on your back with both knees bent, feet supported on the mat, and arms spread. Keep your knees together and gently lower them toward one side while both shoulders stay grounded. Hold comfortably without forcing your knees to the floor, return to the center, and repeat on the other side. Record one hold per set.' as description, 'supine-knees-side-to-side' as image_key
) seed
where not exists (select 1 from exercises where lower(exercises.name) = lower(seed.name));
