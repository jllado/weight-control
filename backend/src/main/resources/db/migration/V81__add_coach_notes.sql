create table coach_notes (
    id bigint not null auto_increment,
    user_id bigint not null,
    note_date date not null,
    content text not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    constraint fk_coach_notes_user foreign key (user_id) references users (id)
);
create index idx_coach_notes_user_date on coach_notes (user_id, note_date, id);
