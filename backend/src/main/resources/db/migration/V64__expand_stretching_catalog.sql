-- Additional timed stretches; existing exercises with matching names are preserved.
insert into exercises (name, description, tracking_mode, exercise_type, default_warm_up, default_repetitions, built_in_image_key)
select seed.name, seed.description, 'SECONDS', 'STRETCHING', false, null, seed.image_key
from (
    select 'Seated butterfly stretch' as name, 'Sit tall with the soles of your feet together and let your knees relax outward; hold your ankles and lean slightly forward from your hips without pressing your knees down. Hold comfortably without bouncing. Record one hold per set.' as description, 'seated-butterfly-stretch' as image_key
    union all
    select 'Lying figure-four stretch' as name, 'Lie on your back with knees bent; cross one ankle over the opposite thigh and hold behind the supporting thigh to gently draw it toward your chest. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description, 'lying-figure-four-stretch' as image_key
    union all
    select 'Knee-to-chest stretch' as name, 'Lie on your back with knees bent and feet flat; hold behind one thigh and gently draw that knee toward your chest while keeping your head relaxed on the floor. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description, 'knee-to-chest-stretch' as image_key
    union all
    select 'Child''s pose stretch' as name, 'Kneel on a mat, sit your hips toward your heels, and reach your arms forward as you lower your chest comfortably toward the floor. Hold comfortably without bouncing. Record one hold per set.' as description, 'childs-pose-stretch' as image_key
    union all
    select 'Doorway chest stretch' as name, 'Stand beside a doorway with one forearm on the frame and your elbow just below shoulder height; take a small step forward until you feel a gentle stretch across your chest. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description, 'doorway-chest-stretch' as image_key
    union all
    select 'Overhead triceps stretch' as name, 'Raise one arm overhead and bend the elbow so your hand reaches behind your neck; use your other hand to gently guide the raised elbow while keeping your ribs relaxed. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description, 'overhead-triceps-stretch' as image_key
    union all
    select 'Seated hamstring stretch' as name, 'Sit near the front of a stable chair with one leg extended and its heel on the floor; keep your back long and lean forward from your hips with your hands resting on the bent-leg thigh. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description, 'seated-hamstring-stretch' as image_key
    union all
    select 'Seated side stretch' as name, 'Sit upright on a stable chair with feet flat; raise one arm overhead and lean gently to the opposite side while keeping both hips on the seat. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description, 'seated-side-stretch' as image_key
    union all
    select 'Wrist flexor stretch' as name, 'Extend one arm forward with your palm up; use the other hand to gently draw the fingers down and back while keeping the elbow comfortably straight. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description, 'wrist-flexor-stretch' as image_key
    union all
    select 'Wrist extensor stretch' as name, 'Extend one arm forward with your palm down; bend the wrist so the fingers point toward the floor and gently guide the back of that hand toward you with your other hand. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description, 'wrist-extensor-stretch' as image_key
    union all
    select 'Bent-knee calf stretch' as name, 'Face a wall with hands supported and one foot a short step behind you; bend both knees gently while keeping the back heel on the floor. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description, 'bent-knee-calf-stretch' as image_key
    union all
    select 'Standing inner-thigh stretch' as name, 'Stand with feet wide and toes forward; bend one knee and shift your hips toward that side while keeping the opposite leg straight and both feet flat. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description, 'standing-inner-thigh-stretch' as image_key
) seed
where not exists (select 1 from exercises where lower(exercises.name) = lower(seed.name));
