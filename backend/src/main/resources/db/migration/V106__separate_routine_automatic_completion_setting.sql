alter table routines add column automatic_completion_enabled boolean not null default false;
update routines set automatic_completion_enabled = true where automatic_trigger <> 'NONE';
