create table users (
    id bigint not null auto_increment primary key,
    email varchar(255) not null unique,
    google_sub varchar(255) unique,
    display_name varchar(255),
    dashboard_anchor_date date,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp
);

create table weights (
    id bigint not null auto_increment primary key,
    legacy_firebase_id varchar(64) unique,
    user_id bigint not null,
    measured_at datetime(3) not null,
    weight decimal(6,2) not null,
    fat_percentage decimal(6,2) not null,
    fat decimal(6,2) not null,
    muscle decimal(6,2) not null,
    muscle_percentage decimal(6,2) not null,
    lost_weight decimal(6,2) not null,
    lost_fat decimal(6,2) not null,
    lost_muscle decimal(6,2) not null,
    photo_front_path varchar(500),
    photo_left_path varchar(500),
    photo_right_path varchar(500),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_weights_user foreign key (user_id) references users (id)
);

create index idx_weights_user_measured_at on weights(user_id, measured_at);

create table blood_pressures (
    id bigint not null auto_increment primary key,
    legacy_firebase_id varchar(64) unique,
    user_id bigint not null,
    measured_at datetime(3) not null,
    upper int not null,
    lower int not null,
    lost_upper int not null,
    lost_lower int not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_blood_pressures_user foreign key (user_id) references users (id)
);

create index idx_blood_pressures_user_measured_at on blood_pressures(user_id, measured_at);

create table habits (
    id bigint not null auto_increment primary key,
    legacy_firebase_id varchar(64) unique,
    user_id bigint not null,
    start_date datetime(3) not null,
    duration int not null,
    last_time_date datetime(3),
    name varchar(255) not null,
    times int not null,
    current_strike int not null,
    best_strike int not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_habits_user foreign key (user_id) references users (id)
);

create table routines (
    id bigint not null auto_increment primary key,
    legacy_firebase_id varchar(64) unique,
    user_id bigint not null,
    start_date datetime(3) not null,
    last_time_date datetime(3),
    name varchar(255) not null,
    current_strike int not null,
    best_strike int not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_routines_user foreign key (user_id) references users (id)
);

create table routine_types (
    routine_id bigint not null,
    type varchar(32) not null,
    constraint fk_routine_types_routine foreign key (routine_id) references routines (id) on delete cascade
);

create table routine_checkins (
    id bigint not null auto_increment primary key,
    routine_id bigint not null,
    checked_at datetime(3) not null,
    created_at timestamp not null default current_timestamp,
    constraint uq_routine_checkins unique (routine_id, checked_at),
    constraint fk_routine_checkins_routine foreign key (routine_id) references routines (id) on delete cascade
);

create index idx_routine_checkins_checked_at on routine_checkins(routine_id, checked_at);

create table daily_statuses (
    id bigint not null auto_increment primary key,
    legacy_firebase_id varchar(64) unique,
    user_id bigint not null,
    status_date date not null,
    weight_id bigint,
    blood_pressure_id bigint,
    total_routines int not null,
    total_weight_routines int not null,
    total_blood_pressure_routines int not null,
    total_flexibility_routines int not null,
    total_mind_routines int not null,
    routines_done int not null,
    weight_done int not null,
    blood_pressure_done int not null,
    flexibility_done int not null,
    mind_done int not null,
    routines_percentage decimal(6,2) not null,
    weight_percentage decimal(6,2) not null,
    blood_pressure_percentage decimal(6,2) not null,
    flexibility_percentage decimal(6,2) not null,
    mind_percentage decimal(6,2) not null,
    routines_score decimal(6,2) not null,
    weight_score decimal(6,2) not null,
    blood_pressure_score decimal(6,2) not null,
    flexibility_score decimal(6,2) not null,
    mind_score decimal(6,2) not null,
    routines_status decimal(6,2) not null,
    weight_status decimal(6,2) not null,
    blood_pressure_status decimal(6,2) not null,
    flexibility_status decimal(6,2) not null,
    mind_status decimal(6,2) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uq_daily_statuses unique (user_id, status_date),
    constraint fk_daily_statuses_user foreign key (user_id) references users (id),
    constraint fk_daily_statuses_weight foreign key (weight_id) references weights (id),
    constraint fk_daily_statuses_blood_pressure foreign key (blood_pressure_id) references blood_pressures (id)
);
