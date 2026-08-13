rename table calories to meals;

create index idx_meals_user_date on meals(user_id, calorie_date);

alter table meals
    drop index uq_calories_user_date,
    drop index idx_calories_user_date,
    change column calorie_date meal_date date not null,
    add column meal_type varchar(16) null after meal_date,
    add column meal_sequence int null after meal_type,
    add column protein_grams decimal(10, 2) null after calories,
    add column carbohydrate_grams decimal(10, 2) null after protein_grams,
    add column fat_grams decimal(10, 2) null after carbohydrate_grams,
    add column migration_updated_at timestamp null after updated_at;

update meals
set migration_updated_at = updated_at;

insert into meals (user_id, meal_date, meal_type, meal_sequence, calories, created_at, updated_at, migration_updated_at)
select user_id, meal_date, 'DINNER', 1, calories - floor(calories / 2), created_at, migration_updated_at, migration_updated_at
from meals;

update meals
set calories = floor(calories / 2), meal_type = 'LUNCH', meal_sequence = 1
where meal_type is null;

update meals
set updated_at = migration_updated_at;

alter table meals
    drop column migration_updated_at,
    modify column meal_type varchar(16) not null,
    modify column meal_sequence int not null,
    add constraint uq_meals_user_date_type_sequence unique (user_id, meal_date, meal_type, meal_sequence);
