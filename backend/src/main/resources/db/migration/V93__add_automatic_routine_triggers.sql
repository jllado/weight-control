alter table routines add column automatic_trigger varchar(40) not null default 'NONE';
alter table routine_checkins add column manual_completion boolean not null default true;
alter table catalog_foods add column fruit boolean not null default false;
alter table meal_dishes add column fruit boolean not null default false;
alter table recipe_ingredients add column fruit boolean not null default false;

create table routine_automatic_evidence (
    id bigint not null auto_increment primary key,
    routine_id bigint not null,
    source_kind varchar(16) not null,
    source_key varchar(255) not null,
    event_date date not null,
    constraint uq_routine_automatic_evidence unique (routine_id, source_kind, source_key),
    constraint fk_routine_automatic_evidence_routine foreign key (routine_id) references routines (id) on delete cascade
);
create index idx_routine_automatic_evidence_day on routine_automatic_evidence (routine_id, event_date);
