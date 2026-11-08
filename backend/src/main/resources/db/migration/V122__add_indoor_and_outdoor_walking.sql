-- Preserve generic Walking and any matching custom exercise, including its picture.
insert into exercises (name, description, tracking_mode, exercise_type, cardio_metric, built_in_image_key)
select seed.name, seed.description, 'CARDIO', 'TRAINING', 'SPEED_KPH', seed.image_key
from (
    select 'Indoor walking' as name, 'Walk indoors on a treadmill at a comfortable, steady pace. Record the actual walking duration and speed.' as description, 'indoor-walking' as image_key
    union all
    select 'Outdoor walking', 'Walk outdoors at a comfortable, steady pace. Record the actual walking duration and speed.', 'outdoor-walking'
) seed
where not exists (select 1 from exercises where lower(exercises.name) = lower(seed.name));
