delete from personal_record_events where metric like 'HABIT_%' or source_type in ('HABIT_BASELINE', 'HABIT_CHECKIN');
delete from personal_record_snapshots where metric like 'HABIT_%';
delete from personal_record_settings where metric like 'HABIT_%';
drop table if exists habit_checkins;
drop table if exists habit_baselines;
drop table if exists habits;
