alter table routines
    add column personal_records_enabled boolean not null default true;

update routines r
join personal_record_settings s on s.user_id = r.user_id and s.metric = 'ROUTINE_BEST_STREAK'
set r.personal_records_enabled = s.mode <> 'DISABLED';

delete from personal_record_settings
where metric = 'ROUTINE_BEST_STREAK';
