alter table meal_dishes
    add column quantity decimal(11,3) not null default 1,
    add column unit varchar(20) not null default 'SERVING',
    add column reference_quantity decimal(11,3) not null default 1,
    add column reference_calories integer,
    add column reference_protein_grams decimal(10,2),
    add column reference_carbohydrate_grams decimal(10,2),
    add column reference_fat_grams decimal(10,2);

update meal_dishes set reference_calories = calories, reference_protein_grams = protein_grams,
    reference_carbohydrate_grams = carbohydrate_grams, reference_fat_grams = fat_grams;

alter table meal_dishes
    modify reference_calories integer not null,
    add constraint chk_dish_quantity check (quantity > 0 and reference_quantity > 0);
