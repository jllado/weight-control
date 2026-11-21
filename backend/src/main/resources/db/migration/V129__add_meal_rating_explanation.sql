ALTER TABLE meals ADD COLUMN rating_explanation TEXT NULL;

CREATE TABLE meal_rating_profile_history (
    user_id BIGINT NOT NULL,
    effective_epoch BIGINT NOT NULL,
    protein_grams DECIMAL(10,2),
    fiber_grams DECIMAL(10,2),
    weekly_maximum INT NOT NULL,
    monday INT NOT NULL,
    tuesday INT NOT NULL,
    wednesday INT NOT NULL,
    thursday INT NOT NULL,
    friday INT NOT NULL,
    saturday INT NOT NULL,
    sunday INT NOT NULL,
    PRIMARY KEY (user_id, effective_epoch),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

INSERT INTO meal_rating_profile_history
    (user_id, effective_epoch, weekly_maximum, protein_grams, fiber_grams, monday, tuesday, wednesday, thursday, friday, saturday, sunday)
SELECT id, UNIX_TIMESTAMP(updated_at), weekly_average_calorie_maximum, macro_protein_target_grams, nutrient_fiber_target_grams,
       typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday,
       typical_calories_friday, typical_calories_saturday, typical_calories_sunday
FROM users;
