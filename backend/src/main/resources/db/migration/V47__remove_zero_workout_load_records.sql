delete from personal_record_snapshots
where metric in ('WORKOUT_HEAVIEST_LOAD', 'WORKOUT_HEAVIEST_LOAD_MINIMUM')
  and value = 0;
