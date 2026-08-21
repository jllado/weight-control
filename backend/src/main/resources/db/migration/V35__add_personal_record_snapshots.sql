create table personal_record_snapshots (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    series_key varchar(160) not null,
    domain varchar(16) not null,
    metric varchar(48) not null,
    direction varchar(16) not null,
    exercise_id bigint,
    load_kg decimal(7,2),
    value decimal(12,2) not null,
    record_date date not null,
    source_type varchar(16) not null,
    source_id bigint not null,
    line_position int,
    segment_position int,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uq_personal_record_snapshots_user_series unique (user_id, series_key),
    constraint fk_personal_record_snapshots_user foreign key (user_id) references users (id) on delete cascade,
    constraint fk_personal_record_snapshots_exercise foreign key (exercise_id) references exercises (id)
);

create index idx_personal_record_snapshots_user_domain on personal_record_snapshots(user_id, domain);
create index idx_personal_record_snapshots_user_metric on personal_record_snapshots(user_id, metric);
create index idx_personal_record_snapshots_user_exercise on personal_record_snapshots(user_id, exercise_id);

create temporary table personal_record_candidates (
    user_id bigint not null,
    series_key varchar(160) not null,
    domain varchar(16) not null,
    metric varchar(48) not null,
    direction varchar(16) not null,
    exercise_id bigint,
    load_kg decimal(7,2),
    value decimal(12,2) not null,
    record_date date not null,
    source_order datetime(3) not null,
    source_type varchar(16) not null,
    source_id bigint not null,
    line_position int,
    segment_position int
);

insert into personal_record_candidates
    (user_id, series_key, domain, metric, direction, exercise_id, load_kg, value, record_date, source_order, source_type, source_id, line_position, segment_position)
select user_id, 'BODY_WEIGHT', 'BODY', 'BODY_WEIGHT', 'MINIMUM', null, null, weight, date(measured_at), measured_at, 'WEIGHT', id, null, null from weights
union all
select user_id, 'BODY_FAT_MASS', 'BODY', 'BODY_FAT_MASS', 'MINIMUM', null, null, fat, date(measured_at), measured_at, 'WEIGHT', id, null, null from weights
union all
select user_id, 'BODY_FAT_PERCENTAGE', 'BODY', 'BODY_FAT_PERCENTAGE', 'MINIMUM', null, null, fat_percentage, date(measured_at), measured_at, 'WEIGHT', id, null, null from weights
union all
select user_id, 'BODY_MUSCLE_MASS', 'BODY', 'BODY_MUSCLE_MASS', 'MAXIMUM', null, null, muscle, date(measured_at), measured_at, 'WEIGHT', id, null, null from weights
union all
select user_id, 'BODY_MUSCLE_PERCENTAGE', 'BODY', 'BODY_MUSCLE_PERCENTAGE', 'MAXIMUM', null, null, muscle_percentage, date(measured_at), measured_at, 'WEIGHT', id, null, null from weights;

insert into personal_record_candidates
    (user_id, series_key, domain, metric, direction, exercise_id, load_kg, value, record_date, source_order, source_type, source_id, line_position, segment_position)
select w.user_id,
       concat('WORKOUT_HEAVIEST_LOAD:', wl.exercise_id),
       'WORKOUT', 'WORKOUT_HEAVIEST_LOAD', 'MAXIMUM', wl.exercise_id, null, coalesce(ws.weight, 0.00),
       w.workout_date, cast(w.workout_date as datetime), 'WORKOUT', w.id, wl.position, ws.position
from workout_segments ws
join workout_lines wl on wl.id = ws.workout_line_id
join workouts w on w.id = wl.workout_id
join exercises e on e.id = wl.exercise_id
where e.tracking_mode in ('REPS', 'SECONDS')
union all
select w.user_id,
       concat('WORKOUT_REPETITIONS:', wl.exercise_id, ':', cast(cast(coalesce(ws.weight, 0.00) as decimal(7,2)) as char)),
       'WORKOUT', 'WORKOUT_REPETITIONS', 'MAXIMUM', wl.exercise_id, coalesce(ws.weight, 0.00), ws.repetitions,
       w.workout_date, cast(w.workout_date as datetime), 'WORKOUT', w.id, wl.position, ws.position
from workout_segments ws
join workout_lines wl on wl.id = ws.workout_line_id
join workouts w on w.id = wl.workout_id
join exercises e on e.id = wl.exercise_id
where e.tracking_mode = 'REPS'
union all
select w.user_id,
       concat('WORKOUT_DURATION:', wl.exercise_id, ':', cast(cast(coalesce(ws.weight, 0.00) as decimal(7,2)) as char)),
       'WORKOUT', 'WORKOUT_DURATION', 'MAXIMUM', wl.exercise_id, coalesce(ws.weight, 0.00), ws.duration_seconds,
       w.workout_date, cast(w.workout_date as datetime), 'WORKOUT', w.id, wl.position, ws.position
from workout_segments ws
join workout_lines wl on wl.id = ws.workout_line_id
join workouts w on w.id = wl.workout_id
join exercises e on e.id = wl.exercise_id
where e.tracking_mode = 'SECONDS';

insert into personal_record_candidates
    (user_id, series_key, domain, metric, direction, exercise_id, load_kg, value, record_date, source_order, source_type, source_id, line_position, segment_position)
select w.user_id, concat('CARDIO_DURATION:', wl.exercise_id), 'WORKOUT', 'CARDIO_DURATION', 'MAXIMUM', wl.exercise_id, null, ws.duration_seconds, w.workout_date, cast(w.workout_date as datetime), 'WORKOUT', w.id, wl.position, ws.position
from workout_segments ws join workout_lines wl on wl.id = ws.workout_line_id join workouts w on w.id = wl.workout_id join exercises e on e.id = wl.exercise_id where e.tracking_mode = 'CARDIO'
union all
select w.user_id, concat('CARDIO_SPEED:', wl.exercise_id), 'WORKOUT', 'CARDIO_SPEED', 'MAXIMUM', wl.exercise_id, null, ws.speed_kph, w.workout_date, cast(w.workout_date as datetime), 'WORKOUT', w.id, wl.position, ws.position
from workout_segments ws join workout_lines wl on wl.id = ws.workout_line_id join workouts w on w.id = wl.workout_id join exercises e on e.id = wl.exercise_id where e.tracking_mode = 'CARDIO' and ws.speed_kph is not null
union all
select w.user_id, concat('CARDIO_DISTANCE:', wl.exercise_id), 'WORKOUT', 'CARDIO_DISTANCE', 'MAXIMUM', wl.exercise_id, null, ws.distance_km, w.workout_date, cast(w.workout_date as datetime), 'WORKOUT', w.id, wl.position, ws.position
from workout_segments ws join workout_lines wl on wl.id = ws.workout_line_id join workouts w on w.id = wl.workout_id join exercises e on e.id = wl.exercise_id where e.tracking_mode = 'CARDIO' and ws.distance_km is not null
union all
select w.user_id, concat('CARDIO_INCLINE:', wl.exercise_id), 'WORKOUT', 'CARDIO_INCLINE', 'MAXIMUM', wl.exercise_id, null, ws.incline_percent, w.workout_date, cast(w.workout_date as datetime), 'WORKOUT', w.id, wl.position, ws.position
from workout_segments ws join workout_lines wl on wl.id = ws.workout_line_id join workouts w on w.id = wl.workout_id join exercises e on e.id = wl.exercise_id where e.tracking_mode = 'CARDIO' and ws.incline_percent is not null
union all
select w.user_id, concat('CARDIO_RESISTANCE:', wl.exercise_id), 'WORKOUT', 'CARDIO_RESISTANCE', 'MAXIMUM', wl.exercise_id, null, ws.resistance_level, w.workout_date, cast(w.workout_date as datetime), 'WORKOUT', w.id, wl.position, ws.position
from workout_segments ws join workout_lines wl on wl.id = ws.workout_line_id join workouts w on w.id = wl.workout_id join exercises e on e.id = wl.exercise_id where e.tracking_mode = 'CARDIO' and ws.resistance_level is not null;

insert into personal_record_snapshots
    (user_id, series_key, domain, metric, direction, exercise_id, load_kg, value, record_date, source_type, source_id, line_position, segment_position)
select user_id, series_key, domain, metric, direction, exercise_id, load_kg, value, record_date, source_type, source_id, line_position, segment_position
from (
    select candidates.*,
           row_number() over (
               partition by user_id, series_key
               order by case when direction = 'MINIMUM' then value else -value end,
                        source_order, source_id, coalesce(line_position, -1), coalesce(segment_position, -1)
           ) as record_rank
    from personal_record_candidates candidates
) ranked
where record_rank = 1;

drop temporary table personal_record_candidates;
