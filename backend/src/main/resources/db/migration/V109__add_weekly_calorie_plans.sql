CREATE TABLE weekly_calorie_plans (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    week_start DATE NOT NULL,
    saturday_calories INT NOT NULL,
    sunday_calories INT NOT NULL,
    monday_calories INT NOT NULL,
    tuesday_calories INT NOT NULL,
    wednesday_calories INT NOT NULL,
    thursday_calories INT NOT NULL,
    friday_calories INT NOT NULL,
    revision BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_weekly_calorie_plans_user_week UNIQUE (user_id, week_start),
    CONSTRAINT fk_weekly_calorie_plans_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_weekly_calorie_plans_nonnegative CHECK (
        saturday_calories >= 0 AND sunday_calories >= 0 AND monday_calories >= 0 AND
        tuesday_calories >= 0 AND wednesday_calories >= 0 AND thursday_calories >= 0 AND friday_calories >= 0
    )
);
