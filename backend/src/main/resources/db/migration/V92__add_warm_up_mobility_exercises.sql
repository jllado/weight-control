-- Add the requested warm-up movements while preserving matching user exercises.
insert into exercises (name, description, tracking_mode, exercise_type, built_in_image_key)
select seed.name, seed.description, seed.tracking_mode, 'WARM_UP', seed.image_key
from (
    select 'Standing lunge hip-flexor stretch' as name, 'Stand in a split stance with your front knee bent and back leg extended, heel raised. Keep your torso upright and hips facing forward; gently shift forward until you feel the stretch at the front of the rear hip. Hold comfortably without bouncing and repeat on the other side. Record one hold per set.' as description, 'SECONDS' as tracking_mode, 'standing-lunge-hip-flexor-stretch' as image_key
    union all select 'Calf stretch on step', 'Stand with the ball of one foot on a stable step edge and the heel hanging off. Lower the heel gently below the step to feel a calf stretch; keep the other foot grounded for balance. Hold comfortably without bouncing, then switch sides. Record one hold per set.', 'SECONDS', 'calf-stretch-on-step'
    union all select 'Floor sit-to-stand without hands', 'Start seated on the floor. Move to standing and return to the floor without using your hands, keeping the movement controlled. Record one repetition for each complete rise and return.', 'REPS', 'floor-sit-to-stand-without-hands'
    union all select 'Resistance-band shoulder pass-through', 'Hold a resistance band in front at hip height with a wide grip and straight arms. Slowly lift it overhead and continue behind your body, then reverse to the front through a comfortable range. Record one repetition for the full pass-through and return.', 'REPS', 'resistance-band-shoulder-pass-through'
) seed
where not exists (select 1 from exercises where lower(exercises.name) = lower(seed.name));
