CREATE TABLE workout_plans (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    review_date DATE NOT NULL,
    notes VARCHAR(500),
    days_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    archived_at DATETIME(6),
    update_token VARCHAR(36) NOT NULL,
    current_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN archived_at IS NULL THEN user_id ELSE NULL END) VIRTUAL,
    CONSTRAINT fk_workout_plan_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_current_workout_plan UNIQUE (current_user_id),
    CONSTRAINT ck_workout_plan_dates CHECK (review_date >= start_date),
    INDEX idx_workout_plan_archive (user_id, archived_at, id)
);
