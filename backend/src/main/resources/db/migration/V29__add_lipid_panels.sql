create table lipid_panels (
    id bigint not null auto_increment primary key,
    user_id bigint not null,
    panel_date date not null,
    total_cholesterol int not null,
    hdl_cholesterol int not null,
    ldl_cholesterol int not null,
    triglycerides int not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uq_lipid_panels_user_date unique (user_id, panel_date),
    constraint fk_lipid_panels_user foreign key (user_id) references users (id)
);

insert into lipid_panels (user_id, panel_date, total_cholesterol, hdl_cholesterol, ldl_cholesterol, triglycerides)
select users.id, history.panel_date, history.total_cholesterol, history.hdl_cholesterol, history.ldl_cholesterol, history.triglycerides
from users
cross join (
    select date '2021-09-04' panel_date, 234 total_cholesterol, 45 hdl_cholesterol, 168 ldl_cholesterol, 107 triglycerides
    union all select date '2021-12-04', 228, 45, 165, 90
    union all select date '2022-05-21', 246, 43, 171, 160
    union all select date '2022-12-17', 187, 36, 118, 164
    union all select date '2023-04-01', 231, 54, 154, 116
    union all select date '2023-09-26', 257, 44, 197, 79
    union all select date '2024-05-04', 231, 50, 165, 78
    union all select date '2025-02-25', 174, 39, 122, 65
    union all select date '2025-07-29', 192, 63, 114, 77
    union all select date '2025-09-15', 210, 60, 138, 65
    union all select date '2026-02-02', 211, 63, 133, 77
) history
where users.email = 'jllado@gmail.com';
