alter table routines add column automatic_completion_enabled boolean not null default false;
update routines set automatic_completion_enabled = true, updated_at = updated_at where automatic_trigger <> 'NONE';
