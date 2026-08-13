alter table back_pain_episodes
    add column severity varchar(16) null after side;

update back_pain_episodes
set severity = case
    when pain <= 3 then 'MILD'
    when pain <= 6 then 'MODERATE'
    when pain <= 9 then 'SEVERE'
    else 'EXTREME'
end;

alter table back_pain_episodes
    modify column severity varchar(16) not null,
    drop column pain;
