ALTER TABLE meals ADD COLUMN rating_version INT NULL;

CREATE TABLE meal_rating_preferences (
    user_id BIGINT NOT NULL,
    effective_date DATE NOT NULL,
    main_meals VARCHAR(40) NOT NULL,
    main_meal_limit_percent INT NOT NULL,
    minimum_meal_gap_minutes INT NOT NULL,
    minimum_bedtime_gap_minutes INT NOT NULL,
    saturated_fat_energy_percent INT NOT NULL,
    PRIMARY KEY (user_id, effective_date),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

ALTER TABLE meal_rating_profile_history ADD COLUMN salt_grams DECIMAL(10,2);

-- A current personal limit is only known to apply from the saved profile revision.
INSERT INTO meal_rating_profile_history
    (user_id, effective_epoch, weekly_maximum, protein_grams, fiber_grams, salt_grams,
     monday, tuesday, wednesday, thursday, friday, saturday, sunday)
SELECT id, UNIX_TIMESTAMP(updated_at), weekly_average_calorie_maximum, macro_protein_target_grams,
       nutrient_fiber_target_grams, nutrient_salt_target_grams, typical_calories_monday,
       typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday,
       typical_calories_friday, typical_calories_saturday, typical_calories_sunday
FROM users WHERE nutrient_salt_target_grams IS NOT NULL
ON DUPLICATE KEY UPDATE salt_grams=VALUES(salt_grams);
