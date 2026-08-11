create table back_pain_episodes (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    episode_date date not null,
    episode_time time,
    region varchar(16) not null,
    side varchar(16),
    pain int not null,
    note varchar(500),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_back_pain_episodes_user foreign key (user_id) references users (id)
);

create index idx_back_pain_episodes_user_date_time on back_pain_episodes(user_id, episode_date, episode_time);

insert into back_pain_episodes (user_id, episode_date, region, pain, note, created_at, updated_at)
select user_id, status_date, 'LOWER', lower_pain, note, created_at, updated_at
from back_statuses
where lower_pain > 0
  and lower_pain = greatest(lower_pain, middle_pain, upper_pain);

insert into back_pain_episodes (user_id, episode_date, region, pain, note, created_at, updated_at)
select user_id, status_date, 'MIDDLE', middle_pain, note, created_at, updated_at
from back_statuses
where middle_pain > 0
  and middle_pain = greatest(lower_pain, middle_pain, upper_pain);

insert into back_pain_episodes (user_id, episode_date, region, pain, note, created_at, updated_at)
select user_id, status_date, 'UPPER', upper_pain, note, created_at, updated_at
from back_statuses
where upper_pain > 0
  and upper_pain = greatest(lower_pain, middle_pain, upper_pain);

drop table back_statuses;
