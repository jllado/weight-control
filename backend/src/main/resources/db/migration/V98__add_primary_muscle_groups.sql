-- Reviewed current global catalog, including additional production entries; see docs/training-balance/plan.md.
alter table exercises add column primary_muscle_group varchar(16) null;

update exercises
set primary_muscle_group = case lower(name)
    when 'push-up' then 'CHEST'
    when 'bench press' then 'CHEST'
    when 'weighted dip' then 'CHEST'
    when 'suspension chest press' then 'CHEST'
    when 'pull-up' then 'BACK'
    when 'chin-up' then 'BACK'
    when 'barbell row' then 'BACK'
    when 'jefferson curl' then 'BACK'
    when 'roman chair back extension' then 'BACK'
    when 'overhead press' then 'SHOULDERS'
    when 'band lateral raise' then 'SHOULDERS'
    when 'lateral shoulder raise' then 'SHOULDERS'
    when 'half-kneeling single-arm dumbbell press' then 'SHOULDERS'
    when 'band pull-aparts' then 'SHOULDERS'
    when 'curl' then 'BICEPS'
    when 'parallel bar support hold' then 'TRICEPS'
    when 'dead bug' then 'CORE'
    when 'plank' then 'CORE'
    when 'abdominal crunch' then 'CORE'
    when 'dumbbell side bend' then 'CORE'
    when 'banded hip abduction' then 'GLUTES'
    when 'hip thrust' then 'GLUTES'
    when 'banded clamshell' then 'GLUTES'
    when 'squat' then 'QUADRICEPS'
    when 'bulgarian split squat' then 'QUADRICEPS'
    when 'box step-up' then 'QUADRICEPS'
    when 'wall sit' then 'QUADRICEPS'
    when 'dumbbell walking lunges' then 'QUADRICEPS'
    when 'deadlift' then 'HAMSTRINGS'
end
where exercise_type = 'TRAINING' and tracking_mode in ('REPS', 'SECONDS');

-- Require complete classification for current and future strength exercises; excluded activities have no group.
alter table exercises add constraint ck_exercises_primary_muscle_group check (
    (exercise_type = 'TRAINING' and tracking_mode in ('REPS', 'SECONDS') and primary_muscle_group is not null
        and primary_muscle_group in ('CHEST', 'BACK', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'FOREARMS', 'CORE', 'GLUTES', 'QUADRICEPS', 'HAMSTRINGS', 'CALVES'))
    or ((exercise_type <> 'TRAINING' or tracking_mode = 'CARDIO') and primary_muscle_group is null)
);
