delete notifications
from in_app_notifications notifications
join personal_record_events events on notifications.deduplication_key = concat('PERSONAL_RECORD:', events.event_key)
left join personal_record_settings settings on settings.user_id = events.user_id
    and settings.metric = regexp_replace(events.metric, '_(MINIMUM|MAXIMUM)$', '')
where notifications.type = 'PERSONAL_RECORD'
  and events.metric not in (
      'BODY_WEIGHT', 'BODY_FAT_MASS', 'BODY_FAT_PERCENTAGE', 'BODY_MUSCLE_MASS',
      'BODY_MUSCLE_PERCENTAGE', 'BODY_BMI', 'WORKOUT_HEAVIEST_LOAD', 'ROUTINE_BEST_STREAK'
  )
  and settings.id is null;

delete events
from personal_record_events events
left join personal_record_settings settings on settings.user_id = events.user_id
    and settings.metric = regexp_replace(events.metric, '_(MINIMUM|MAXIMUM)$', '')
where events.metric not in (
      'BODY_WEIGHT', 'BODY_FAT_MASS', 'BODY_FAT_PERCENTAGE', 'BODY_MUSCLE_MASS',
      'BODY_MUSCLE_PERCENTAGE', 'BODY_BMI', 'WORKOUT_HEAVIEST_LOAD', 'ROUTINE_BEST_STREAK'
  )
  and settings.id is null;

delete snapshots
from personal_record_snapshots snapshots
left join personal_record_settings settings on settings.user_id = snapshots.user_id
    and settings.metric = regexp_replace(snapshots.metric, '_(MINIMUM|MAXIMUM)$', '')
where snapshots.metric not in (
      'BODY_WEIGHT', 'BODY_FAT_MASS', 'BODY_FAT_PERCENTAGE', 'BODY_MUSCLE_MASS',
      'BODY_MUSCLE_PERCENTAGE', 'BODY_BMI', 'WORKOUT_HEAVIEST_LOAD'
  )
  and settings.id is null;
