alter table moods
    add column period varchar(16) null after mood_date,
    drop index uq_moods_user_date;

update moods
set period = 'EVENING',
    updated_at = updated_at;

alter table moods
    modify column period varchar(16) not null,
    add constraint uq_moods_user_date_period unique (user_id, mood_date, period);

insert into moods (user_id, mood_date, period, value, note, created_at, updated_at)
select user_id, mood_date, 'MORNING', value, note, created_at, updated_at
from moods
where period = 'EVENING';

insert into moods (user_id, mood_date, period, value, note, created_at, updated_at)
select user_id, mood_date, 'MIDDAY', value, note, created_at, updated_at
from moods
where period = 'EVENING';
