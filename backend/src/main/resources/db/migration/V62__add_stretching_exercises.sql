-- Basic stretches: https://www.mayoclinic.org/healthy-lifestyle/fitness/in-depth/stretching/art-20546848
insert into exercises (name, description, tracking_mode, exercise_type, default_warm_up, default_repetitions)
select seed.name, seed.description, 'SECONDS', 'STRETCHING', false, null
from (
    select 'Wall calf stretch' as name, 'Face a wall with one foot behind you; keep the back heel down and knee straight as you bend the front knee. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description
    union all
    select 'Wall hamstring stretch' as name, 'Lie beside a doorway and rest one heel on the wall; gently straighten that leg while keeping the other leg relaxed. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description
    union all
    select 'Standing quadriceps stretch' as name, 'Use a wall for balance, hold one ankle behind you, and gently draw the heel toward your buttock with knees close together. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description
    union all
    select 'Kneeling hip flexor stretch' as name, 'Kneel on one knee with the other foot forward; keep your torso upright and gently move your hips forward. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description
    union all
    select 'Standing IT band stretch' as name, 'Stand near support, cross one leg behind the other, and gently lean away from the back leg to stretch the outer hip. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description
    union all
    select 'Cross-body shoulder stretch' as name, 'Bring one arm across your chest and support it above or below the elbow with the other arm. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description
    union all
    select 'Towel shoulder stretch' as name, 'Hold a towel behind your back with one hand above your shoulder and one below; gently pull upward with the top hand. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description
    union all
    select 'Neck stretch' as name, 'Gently tilt your head forward and slightly to one side while keeping your shoulders relaxed. Hold comfortably without bouncing. Record one hold per set and repeat on the other side.' as description
) seed
where not exists (select 1 from exercises where lower(exercises.name) = lower(seed.name));
