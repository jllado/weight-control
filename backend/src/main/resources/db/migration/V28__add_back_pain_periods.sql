alter table back_pain_episodes
    add column period varchar(16) null after episode_time;

update back_pain_episodes
set period = case
    when episode_time < '12:00:00' then 'MORNING'
    when episode_time < '18:00:00' then 'MIDDAY'
    else 'EVENING'
end
where episode_time is not null;

delete from back_pain_episodes
where id in (
    select id
    from (
        select id,
               row_number() over (partition by user_id, episode_date, period order by episode_time desc, id desc) as position
        from back_pain_episodes
        where period is not null
    ) ranked_episodes
    where position > 1
);

alter table back_pain_episodes
    add constraint uq_back_pain_episodes_user_date_period unique (user_id, episode_date, period);
