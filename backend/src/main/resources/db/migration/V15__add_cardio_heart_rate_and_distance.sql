alter table workout_lines
    add column average_heart_rate int;

alter table workout_segments
    add column distance_km decimal(7,2);
