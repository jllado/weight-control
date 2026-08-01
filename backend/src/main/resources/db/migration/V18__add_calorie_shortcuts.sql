alter table users
    add column calorie_shortcut_on_plan int not null default 1850,
    add column calorie_shortcut_flexible int not null default 3000,
    add column calorie_shortcut_off_plan int not null default 4000,
    add column calorie_shortcut_binge int not null default 5000;
