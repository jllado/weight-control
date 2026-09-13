alter table meals
    add column rating int null after notes,
    add constraint chk_meals_rating check (rating between 1 and 5);
