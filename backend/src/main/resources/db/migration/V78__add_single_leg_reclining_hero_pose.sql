-- Single-leg reclined quadriceps and hip-flexor stretch; preserve matching custom exercises.
insert into exercises (name, description, tracking_mode, exercise_type, built_in_image_key)
select seed.name, seed.description, 'SECONDS', 'STRETCHING', seed.image_key
from (
    select 'Single-leg reclining hero pose' as name, 'Sit on the floor with one leg extended forward and the other folded beside the same-side hip, with that foot pointing backward. Gradually recline onto your hands, elbows, back, or a bolster while keeping your hips comfortable and the bent knee aligned forward. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description, 'single-leg-reclining-hero-pose' as image_key
) seed
where not exists (select 1 from exercises where lower(exercises.name) = lower(seed.name));
