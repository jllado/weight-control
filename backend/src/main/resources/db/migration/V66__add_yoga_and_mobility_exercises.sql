-- Additional yoga poses and active holds; preserve matching custom exercises.
insert into exercises (name, description, tracking_mode, exercise_type, default_warm_up, default_repetitions, built_in_image_key)
select seed.name, seed.description, 'SECONDS', 'STRETCHING', false, null, seed.image_key
from (
    select 'Lying straight-leg hold' as name, 'Lie on your back with one leg resting straight on the mat; raise the other leg with its knee straight and hold without using your hands or a strap. Keep your head relaxed and use a comfortable angle; reaching beyond 90 degrees is not required. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description, 'lying-straight-leg-hold' as image_key
    union all
    select 'All-fours rotation' as name, 'Start on hands and knees; keep both knees and one hand grounded, rotate your chest, and reach the other arm toward the ceiling. Keep your hips above your knees and use a comfortable range. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description, 'all-fours-rotation' as image_key
    union all
    select 'Lying spinal twist' as name, 'Lie on your back with arms spread on the floor; bend one knee and let it cross toward the opposite side while the other leg stays extended. Keep your shoulders relaxed; the knee does not need to reach the floor. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description, 'lying-spinal-twist' as image_key
    union all
    select 'Seated 90/90 hip stretch' as name, 'Sit with one leg bent in front and the other bent to the side and behind, with both knees roughly at right angles. Keep your torso long and lean gently over the front leg; use your hands for balance. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description, 'seated-90-90-hip-stretch' as image_key
    union all
    select 'Downward-facing dog' as name, 'From hands and knees, tuck your toes and lift your hips up and back with your hands grounded. Keep your knees softly bent as needed and lengthen your back; your heels do not need to touch the floor. Hold comfortably without bouncing. Record one hold per set.' as description, 'downward-facing-dog' as image_key
    union all
    select 'Sphinx pose' as name, 'Lie face down with your legs extended; place your elbows under your shoulders and rest your forearms on the mat. Gently lift your chest while keeping your pelvis grounded and shoulders relaxed. Hold comfortably without bouncing. Record one hold per set.' as description, 'sphinx-pose' as image_key
    union all
    select 'Extended puppy pose' as name, 'Start on hands and knees; keep your hips above your knees, walk your hands forward, and gently lower your chest. Let your head relax without forcing your chest or forehead to the floor. Hold comfortably without bouncing. Record one hold per set.' as description, 'extended-puppy-pose' as image_key
    union all
    select 'Happy baby pose' as name, 'Lie on your back, bend your knees toward your torso, and hold the outer edges of your feet. Let your knees open beside your torso with soles facing upward; keep your head and shoulders resting on the mat. Hold comfortably without bouncing. Record one hold per set.' as description, 'happy-baby-pose' as image_key
    union all
    select 'Seated forward fold' as name, 'Sit with your legs together in front of you and knees softly bent as needed. Lengthen your back and lean forward from your hips with your hands resting comfortably on your legs; reaching your feet is not required. Hold comfortably without bouncing. Record one hold per set.' as description, 'seated-forward-fold' as image_key
    union all
    select 'Seated wide-leg forward fold' as name, 'Sit with your legs comfortably apart and knees softly bent as needed. Lengthen your back and lean forward from your hips with your hands on the floor in front; your chest does not need to reach the floor. Hold comfortably without bouncing. Record one hold per set.' as description, 'seated-wide-leg-forward-fold' as image_key
) seed
where not exists (select 1 from exercises where lower(exercises.name) = lower(seed.name));
