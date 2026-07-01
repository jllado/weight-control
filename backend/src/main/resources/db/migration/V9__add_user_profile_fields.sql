alter table users
    add column birth_date date null,
    add column height_cm int null,
    add column sex varchar(32) null,
    add column fitness_level varchar(32) null,
    add column takes_medication bit(1) not null default b'0';
