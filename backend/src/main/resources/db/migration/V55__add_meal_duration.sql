alter table meals
    add column duration_minutes int null after meal_time;

update meals set duration_minutes = 30, updated_at = updated_at;

alter table meals
    add constraint chk_meals_duration_positive check (duration_minutes > 0),
    add constraint chk_meals_timed_duration check (meal_time is null or duration_minutes is not null);

-- Stored fasting timestamps use UTC. Remove short intervals before shifting starts.
delete from fasting_periods
where source = 'AUTOMATIC'
  and timestampdiff(second, start_time, coalesce(end_time, utc_timestamp())) < 30600;

update fasting_periods
set start_time = date_add(start_time, interval 30 minute), updated_at = updated_at
where source = 'AUTOMATIC';
