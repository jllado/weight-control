alter table personal_record_snapshots modify source_id bigint null;

create temporary table direct_health_record_candidates (
    user_id bigint not null,
    series_key varchar(160) not null,
    domain varchar(16) not null,
    metric varchar(48) not null,
    direction varchar(16) not null,
    value decimal(12,2) not null,
    record_date date not null,
    source_order datetime(3) not null,
    source_type varchar(16) not null,
    source_id bigint
);

insert into direct_health_record_candidates
select user_id, 'BLOOD_PRESSURE_SYSTOLIC_MINIMUM', 'VITALS', 'BLOOD_PRESSURE_SYSTOLIC_MINIMUM', 'MINIMUM', upper, date(measured_at), measured_at, 'BLOOD_PRESSURE', id from blood_pressures
union all select user_id, 'BLOOD_PRESSURE_SYSTOLIC_MAXIMUM', 'VITALS', 'BLOOD_PRESSURE_SYSTOLIC_MAXIMUM', 'MAXIMUM', upper, date(measured_at), measured_at, 'BLOOD_PRESSURE', id from blood_pressures
union all select user_id, 'BLOOD_PRESSURE_DIASTOLIC_MINIMUM', 'VITALS', 'BLOOD_PRESSURE_DIASTOLIC_MINIMUM', 'MINIMUM', lower, date(measured_at), measured_at, 'BLOOD_PRESSURE', id from blood_pressures
union all select user_id, 'BLOOD_PRESSURE_DIASTOLIC_MAXIMUM', 'VITALS', 'BLOOD_PRESSURE_DIASTOLIC_MAXIMUM', 'MAXIMUM', lower, date(measured_at), measured_at, 'BLOOD_PRESSURE', id from blood_pressures;

insert into direct_health_record_candidates
select user_id, 'LIPID_TOTAL_CHOLESTEROL_MINIMUM', 'VITALS', 'LIPID_TOTAL_CHOLESTEROL_MINIMUM', 'MINIMUM', total_cholesterol, panel_date, cast(panel_date as datetime), 'LIPID_PANEL', id from lipid_panels
union all select user_id, 'LIPID_HDL_MAXIMUM', 'VITALS', 'LIPID_HDL_MAXIMUM', 'MAXIMUM', hdl_cholesterol, panel_date, cast(panel_date as datetime), 'LIPID_PANEL', id from lipid_panels
union all select user_id, 'LIPID_LDL_MINIMUM', 'VITALS', 'LIPID_LDL_MINIMUM', 'MINIMUM', ldl_cholesterol, panel_date, cast(panel_date as datetime), 'LIPID_PANEL', id from lipid_panels
union all select user_id, 'LIPID_TRIGLYCERIDES_MINIMUM', 'VITALS', 'LIPID_TRIGLYCERIDES_MINIMUM', 'MINIMUM', triglycerides, panel_date, cast(panel_date as datetime), 'LIPID_PANEL', id from lipid_panels;

insert into direct_health_record_candidates
select user_id, 'MOOD_MAXIMUM', 'RECOVERY', 'MOOD_MAXIMUM', 'MAXIMUM', value, mood_date, timestamp(mood_date, case period when 'MORNING' then '08:00:00' when 'MIDDAY' then '12:00:00' else '20:00:00' end), 'MOOD', id from moods;

insert into direct_health_record_candidates
select user_id, 'SLEEP_TOTAL_DURATION_MAXIMUM', 'RECOVERY', 'SLEEP_TOTAL_DURATION_MAXIMUM', 'MAXIMUM', total_sleep_duration, sleep_date, cast(sleep_date as datetime), 'SLEEP', id from sleeps where total_sleep_duration is not null
union all select user_id, 'SLEEP_DEEP_DURATION_MAXIMUM', 'RECOVERY', 'SLEEP_DEEP_DURATION_MAXIMUM', 'MAXIMUM', deep_sleep_duration, sleep_date, cast(sleep_date as datetime), 'SLEEP', id from sleeps where deep_sleep_duration is not null
union all select user_id, 'SLEEP_REM_DURATION_MAXIMUM', 'RECOVERY', 'SLEEP_REM_DURATION_MAXIMUM', 'MAXIMUM', rem_sleep_duration, sleep_date, cast(sleep_date as datetime), 'SLEEP', id from sleeps where rem_sleep_duration is not null
union all select user_id, 'SLEEP_LIGHT_DURATION_MAXIMUM', 'RECOVERY', 'SLEEP_LIGHT_DURATION_MAXIMUM', 'MAXIMUM', light_sleep_duration, sleep_date, cast(sleep_date as datetime), 'SLEEP', id from sleeps where light_sleep_duration is not null
union all select user_id, 'SLEEP_AWAKE_TIME_MINIMUM', 'RECOVERY', 'SLEEP_AWAKE_TIME_MINIMUM', 'MINIMUM', awake_time, sleep_date, cast(sleep_date as datetime), 'SLEEP', id from sleeps where awake_time is not null
union all select user_id, 'SLEEP_AVERAGE_HEART_RATE_MINIMUM', 'RECOVERY', 'SLEEP_AVERAGE_HEART_RATE_MINIMUM', 'MINIMUM', average_heart_rate, sleep_date, cast(sleep_date as datetime), 'SLEEP', id from sleeps where average_heart_rate is not null
union all select user_id, 'SLEEP_AVERAGE_HRV_MAXIMUM', 'RECOVERY', 'SLEEP_AVERAGE_HRV_MAXIMUM', 'MAXIMUM', average_hrv, sleep_date, cast(sleep_date as datetime), 'SLEEP', id from sleeps where average_hrv is not null;

insert into direct_health_record_candidates
select user_id, 'MEAL_CALORIES_MINIMUM', 'NUTRITION', 'MEAL_CALORIES_MINIMUM', 'MINIMUM', calories, meal_date, timestamp(meal_date, coalesce(meal_time, '00:00:00')), 'MEAL', id from meals
union all select user_id, 'MEAL_CALORIES_MAXIMUM', 'NUTRITION', 'MEAL_CALORIES_MAXIMUM', 'MAXIMUM', calories, meal_date, timestamp(meal_date, coalesce(meal_time, '00:00:00')), 'MEAL', id from meals
union all select user_id, 'MEAL_PROTEIN_MINIMUM', 'NUTRITION', 'MEAL_PROTEIN_MINIMUM', 'MINIMUM', protein_grams, meal_date, timestamp(meal_date, coalesce(meal_time, '00:00:00')), 'MEAL', id from meals where protein_grams is not null
union all select user_id, 'MEAL_PROTEIN_MAXIMUM', 'NUTRITION', 'MEAL_PROTEIN_MAXIMUM', 'MAXIMUM', protein_grams, meal_date, timestamp(meal_date, coalesce(meal_time, '00:00:00')), 'MEAL', id from meals where protein_grams is not null
union all select user_id, 'MEAL_CARBOHYDRATES_MINIMUM', 'NUTRITION', 'MEAL_CARBOHYDRATES_MINIMUM', 'MINIMUM', carbohydrate_grams, meal_date, timestamp(meal_date, coalesce(meal_time, '00:00:00')), 'MEAL', id from meals where carbohydrate_grams is not null
union all select user_id, 'MEAL_CARBOHYDRATES_MAXIMUM', 'NUTRITION', 'MEAL_CARBOHYDRATES_MAXIMUM', 'MAXIMUM', carbohydrate_grams, meal_date, timestamp(meal_date, coalesce(meal_time, '00:00:00')), 'MEAL', id from meals where carbohydrate_grams is not null
union all select user_id, 'MEAL_FAT_MINIMUM', 'NUTRITION', 'MEAL_FAT_MINIMUM', 'MINIMUM', fat_grams, meal_date, timestamp(meal_date, coalesce(meal_time, '00:00:00')), 'MEAL', id from meals where fat_grams is not null
union all select user_id, 'MEAL_FAT_MAXIMUM', 'NUTRITION', 'MEAL_FAT_MAXIMUM', 'MAXIMUM', fat_grams, meal_date, timestamp(meal_date, coalesce(meal_time, '00:00:00')), 'MEAL', id from meals where fat_grams is not null;

create temporary table daily_nutrition_totals as
select user_id, meal_date, sum(calories) calories,
       case when count(*) = count(protein_grams) then sum(protein_grams) end protein,
       case when count(*) = count(carbohydrate_grams) then sum(carbohydrate_grams) end carbohydrates,
       case when count(*) = count(fat_grams) then sum(fat_grams) end fat
from meals group by user_id, meal_date;

insert into direct_health_record_candidates
select user_id, 'DAILY_CALORIES_MINIMUM', 'NUTRITION', 'DAILY_CALORIES_MINIMUM', 'MINIMUM', calories, meal_date, cast(meal_date as datetime), 'NUTRITION_DAY', null from daily_nutrition_totals
union all select user_id, 'DAILY_CALORIES_MAXIMUM', 'NUTRITION', 'DAILY_CALORIES_MAXIMUM', 'MAXIMUM', calories, meal_date, cast(meal_date as datetime), 'NUTRITION_DAY', null from daily_nutrition_totals
union all select user_id, 'DAILY_PROTEIN_MINIMUM', 'NUTRITION', 'DAILY_PROTEIN_MINIMUM', 'MINIMUM', protein, meal_date, cast(meal_date as datetime), 'NUTRITION_DAY', null from daily_nutrition_totals where protein is not null
union all select user_id, 'DAILY_PROTEIN_MAXIMUM', 'NUTRITION', 'DAILY_PROTEIN_MAXIMUM', 'MAXIMUM', protein, meal_date, cast(meal_date as datetime), 'NUTRITION_DAY', null from daily_nutrition_totals where protein is not null
union all select user_id, 'DAILY_CARBOHYDRATES_MINIMUM', 'NUTRITION', 'DAILY_CARBOHYDRATES_MINIMUM', 'MINIMUM', carbohydrates, meal_date, cast(meal_date as datetime), 'NUTRITION_DAY', null from daily_nutrition_totals where carbohydrates is not null
union all select user_id, 'DAILY_CARBOHYDRATES_MAXIMUM', 'NUTRITION', 'DAILY_CARBOHYDRATES_MAXIMUM', 'MAXIMUM', carbohydrates, meal_date, cast(meal_date as datetime), 'NUTRITION_DAY', null from daily_nutrition_totals where carbohydrates is not null
union all select user_id, 'DAILY_FAT_MINIMUM', 'NUTRITION', 'DAILY_FAT_MINIMUM', 'MINIMUM', fat, meal_date, cast(meal_date as datetime), 'NUTRITION_DAY', null from daily_nutrition_totals where fat is not null
union all select user_id, 'DAILY_FAT_MAXIMUM', 'NUTRITION', 'DAILY_FAT_MAXIMUM', 'MAXIMUM', fat, meal_date, cast(meal_date as datetime), 'NUTRITION_DAY', null from daily_nutrition_totals where fat is not null;

insert into personal_record_snapshots
    (user_id, series_key, domain, metric, direction, value, record_date, source_type, source_id)
select user_id, series_key, domain, metric, direction, value, record_date, source_type, source_id
from (
    select candidates.*,
           row_number() over (partition by user_id, series_key order by case when direction = 'MINIMUM' then value else -value end, source_order, coalesce(source_id, 0)) record_rank
    from direct_health_record_candidates candidates
) ranked where record_rank = 1;

drop temporary table daily_nutrition_totals;
drop temporary table direct_health_record_candidates;
