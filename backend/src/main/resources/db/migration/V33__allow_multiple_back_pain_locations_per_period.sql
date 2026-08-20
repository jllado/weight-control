alter table back_pain_episodes
    drop index uq_back_pain_episodes_user_date_period,
    add constraint uq_back_pain_episodes_user_date_period_location unique (user_id, episode_date, period, region, side);
