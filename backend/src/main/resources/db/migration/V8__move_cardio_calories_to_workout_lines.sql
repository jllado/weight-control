alter table workout_lines
    add column calories int;

update workout_lines line
join exercises exercise on exercise.id = line.exercise_id
left join (
    select workout_line_id, sum(calories) as total_calories
    from workout_segments
    where calories is not null
    group by workout_line_id
) segment_totals on segment_totals.workout_line_id = line.id
set line.calories = segment_totals.total_calories
where exercise.tracking_mode = 'CARDIO';
