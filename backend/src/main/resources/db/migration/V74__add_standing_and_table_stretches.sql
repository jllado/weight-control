-- Additional standing and table-supported stretches; preserve matching custom exercises.
insert into exercises (name, description, tracking_mode, exercise_type, built_in_image_key)
select seed.name, seed.description, 'SECONDS', 'STRETCHING', seed.image_key
from (
    select 'Behind-the-back chest stretch' as name, 'Stand tall and clasp both hands behind your lower back; gently straighten your elbows and lift your hands away from your body while keeping your shoulders relaxed. Hold comfortably without bouncing. Record one hold per set.' as description, 'behind-the-back-chest-stretch' as image_key
    union all
    select 'Table hamstring stretch' as name, 'Stand facing a stable table and rest one heel on it with that leg straight and toes pointing upward; lean forward from your hips and reach toward the raised foot without forcing contact. Keep your standing foot flat and your back long. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description, 'table-hamstring-stretch' as image_key
    union all
    select 'Table-supported shoulder stretch' as name, 'Place one hand on a stable table with your arm straight; step back, move your hips backward, and lower your torso until roughly parallel to the tabletop with your arm alongside your head. Keep your knees softly bent and rest your free hand on your thigh. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description, 'table-supported-shoulder-stretch' as image_key
    union all
    select 'Standing forward fold' as name, 'Stand with your feet hip-width apart and knees softly bent; bend forward from your hips and reach toward your feet, letting your head relax. Rest your hands on your legs if needed; touching your feet is not required. Hold comfortably without bouncing. Record one hold per set.' as description, 'standing-forward-fold' as image_key
) seed
where not exists (select 1 from exercises where lower(exercises.name) = lower(seed.name));
