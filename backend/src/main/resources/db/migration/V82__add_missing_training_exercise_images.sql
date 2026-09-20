-- Source-manage the existing production training catalog entries without replacing custom pictures.
insert into exercises (name, description, tracking_mode, exercise_type, built_in_image_key)
select seed.name, seed.description, 'REPS', 'TRAINING', seed.image_key
from (
    select 'Curl' as name, 'Bend your elbows to lift the weight, then lower it slowly.' as description, 'dumbbell-curl' as image_key
    union all select 'Dumbbell Side Bend', 'Hold a weight at your side, bend sideways, then return upright.', 'dumbbell-side-bend'
    union all select 'Dumbbell walking lunges', 'Walk forward while holding dumbbells, alternating legs and lowering into a lunge with each step.', 'dumbbell-walking-lunges'
    union all select 'Hip thrust', 'Raise your hips from the floor by driving through your heels, then lower with control.', 'hip-thrust'
    union all select 'Lateral shoulder raise', 'Raise your arms out to the sides to shoulder height, then slowly lower them.', 'lateral-shoulder-raise'
    union all select 'Roman chair back extension', 'Position your hips on the pad, secure your feet, lower your torso by hinging at the hips, then raise your body back up in a controlled motion.', 'roman-chair-back-extension'
    union all select 'Suspension chest press', 'Lean forward holding the straps, then bend and extend your arms to push your body away, keeping your body straight.', 'suspension-chest-press'
) seed
where not exists (select 1 from exercises where lower(exercises.name) = lower(seed.name));

update exercises
set built_in_image_key = case lower(name)
    when 'curl' then 'dumbbell-curl'
    when 'dumbbell side bend' then 'dumbbell-side-bend'
    when 'dumbbell walking lunges' then 'dumbbell-walking-lunges'
    when 'hip thrust' then 'hip-thrust'
    when 'lateral shoulder raise' then 'lateral-shoulder-raise'
    when 'roman chair back extension' then 'roman-chair-back-extension'
    when 'suspension chest press' then 'suspension-chest-press'
end
where built_in_image_key is null
  and custom_image_path is null
  and lower(name) in ('curl', 'dumbbell side bend', 'dumbbell walking lunges', 'hip thrust', 'lateral shoulder raise', 'roman chair back extension', 'suspension chest press');
