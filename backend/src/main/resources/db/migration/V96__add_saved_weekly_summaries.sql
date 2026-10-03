CREATE TABLE weekly_summaries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    friday_date DATE NOT NULL,
    snapshot_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_weekly_summaries_user_friday UNIQUE (user_id, friday_date),
    CONSTRAINT fk_weekly_summaries_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE weekly_reflections (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    weekly_summary_id BIGINT NOT NULL,
    generated_at TIMESTAMP NOT NULL,
    model VARCHAR(100) NOT NULL,
    title VARCHAR(80) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    body_composition_summary VARCHAR(400) NOT NULL,
    body_composition_next_action VARCHAR(200) NOT NULL,
    blood_pressure_summary VARCHAR(400) NOT NULL,
    blood_pressure_next_action VARCHAR(200) NOT NULL,
    routines_summary VARCHAR(400) NOT NULL,
    routines_next_action VARCHAR(200) NOT NULL,
    nutrition_summary VARCHAR(400) NOT NULL,
    nutrition_next_action VARCHAR(200) NOT NULL,
    training_recovery_summary VARCHAR(400) NOT NULL,
    training_recovery_next_action VARCHAR(200) NOT NULL,
    goal_progress_summary VARCHAR(400) NOT NULL,
    goal_progress_next_action VARCHAR(200) NOT NULL,
    next_week_actions_json TEXT NOT NULL,
    CONSTRAINT uq_weekly_reflections_summary UNIQUE (weekly_summary_id),
    CONSTRAINT fk_weekly_reflections_summary FOREIGN KEY (weekly_summary_id) REFERENCES weekly_summaries (id)
);
