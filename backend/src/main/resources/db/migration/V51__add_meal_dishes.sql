create table meal_dishes (
    id bigint not null auto_increment primary key,
    meal_id bigint not null,
    position integer not null,
    name varchar(255) not null,
    calories integer not null,
    protein_grams decimal(10, 2),
    carbohydrate_grams decimal(10, 2),
    fat_grams decimal(10, 2),
    constraint uq_meal_dishes_position unique (meal_id, position),
    constraint fk_meal_dishes_meal foreign key (meal_id) references meals (id) on delete cascade
);
