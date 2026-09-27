alter table urge_pauses drop index uq_urge_pause_active;
alter table urge_pauses drop column active_user;
alter table urge_pauses add column paused_at timestamp(6);
alter table urge_pauses add column active_user bigint generated always as (case when status in ('ACTIVE', 'PAUSED') then user_id else null end) stored;
alter table urge_pauses add constraint uq_urge_pause_active unique (active_user);
