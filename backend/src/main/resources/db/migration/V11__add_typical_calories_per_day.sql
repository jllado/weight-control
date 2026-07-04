alter table users
    add column typical_calories_saturday int null,
    add column typical_calories_sunday int null,
    add column typical_calories_monday int null,
    add column typical_calories_tuesday int null,
    add column typical_calories_wednesday int null,
    add column typical_calories_thursday int null,
    add column typical_calories_friday int null;

update users
set typical_calories_saturday = 2983,
    typical_calories_sunday = 2983,
    typical_calories_monday = 1853,
    typical_calories_tuesday = 1853,
    typical_calories_wednesday = 1853,
    typical_calories_thursday = 1853,
    typical_calories_friday = 1122
where email = 'jllado@gmail.com';

update users
set typical_calories_saturday = coalesce(typical_calories_saturday, 2983),
    typical_calories_sunday = coalesce(typical_calories_sunday, 2983),
    typical_calories_monday = coalesce(typical_calories_monday, 1853),
    typical_calories_tuesday = coalesce(typical_calories_tuesday, 1853),
    typical_calories_wednesday = coalesce(typical_calories_wednesday, 1853),
    typical_calories_thursday = coalesce(typical_calories_thursday, 1853),
    typical_calories_friday = coalesce(typical_calories_friday, 1122);

alter table users
    modify column typical_calories_saturday int not null,
    modify column typical_calories_sunday int not null,
    modify column typical_calories_monday int not null,
    modify column typical_calories_tuesday int not null,
    modify column typical_calories_wednesday int not null,
    modify column typical_calories_thursday int not null,
    modify column typical_calories_friday int not null;
