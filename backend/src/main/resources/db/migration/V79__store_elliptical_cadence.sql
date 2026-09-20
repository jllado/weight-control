alter table exercises add column cardio_metric varchar(16) null after exercise_type;
update exercises set cardio_metric = 'SPEED_KPH' where tracking_mode = 'CARDIO';
update exercises set cardio_metric = 'CADENCE_RPM' where name = 'Elliptical';

alter table workout_segments add column cadence_rpm decimal(6,2) null after speed_kph;
update workout_segments segments
join workout_lines workout_lines on workout_lines.id = segments.workout_line_id
join exercises on exercises.id = workout_lines.exercise_id
set segments.cadence_rpm = segments.speed_kph,
    segments.speed_kph = null
where exercises.cardio_metric = 'CADENCE_RPM';

update personal_record_snapshots snapshots
join exercises on exercises.id = snapshots.exercise_id
set snapshots.series_key = replace(snapshots.series_key, 'CARDIO_SPEED:', 'CARDIO_CADENCE:'),
    snapshots.metric = 'CARDIO_CADENCE'
where exercises.cardio_metric = 'CADENCE_RPM'
  and snapshots.metric = 'CARDIO_SPEED';
