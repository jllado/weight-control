CREATE TABLE workout_plan_versions (
    plan_id BIGINT NOT NULL,
    version_index INT NOT NULL,
    start_date DATE NOT NULL,
    review_date DATE NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    days_json LONGTEXT NOT NULL,
    PRIMARY KEY (plan_id, version_index),
    CONSTRAINT fk_workout_plan_version_plan FOREIGN KEY (plan_id) REFERENCES workout_plans(id) ON DELETE CASCADE
);
