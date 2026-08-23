alter table in_app_notifications
    add column action_url varchar(500) null after message;

delete from personal_record_settings
where metric in ('ROUTINE_COMPLETION_TOTAL', 'ROUTINE_CURRENT_STREAK');

delete from personal_record_snapshots
where subject_type = 'ROUTINE';
