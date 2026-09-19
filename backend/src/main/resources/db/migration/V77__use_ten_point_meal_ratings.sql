alter table meals drop constraint chk_meals_rating;

update meals set rating = rating * 2 where rating is not null;

alter table meals add constraint chk_meals_rating check (rating between 1 and 10);
