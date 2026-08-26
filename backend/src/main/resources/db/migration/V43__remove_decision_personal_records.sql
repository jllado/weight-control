delete from personal_record_settings
where metric in ('DECISION_TOTAL', 'DECISION_WIN_RATE', 'DECISION_WIN_STREAK');

delete from personal_record_snapshots
where metric in (
    'DECISION_TOTAL_MINIMUM', 'DECISION_TOTAL_MAXIMUM',
    'DECISION_WIN_RATE_MINIMUM', 'DECISION_WIN_RATE_MAXIMUM',
    'DECISION_WIN_STREAK_MINIMUM', 'DECISION_WIN_STREAK_MAXIMUM'
)
or subject_label in ('30-day WIN-rate change');

delete from in_app_notifications
where type = 'PERSONAL_RECORD'
  and (
    message like 'Decisions:%'
    or message like '% decisions:%'
    or message like '30-day WIN-rate change:%'
  );
