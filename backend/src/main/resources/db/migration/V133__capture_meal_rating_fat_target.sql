ALTER TABLE meal_rating_profile_history ADD COLUMN fat_percentage DECIMAL(5,2);

-- The current allocation is known from the last saved profile revision.
INSERT INTO meal_rating_profile_history
    (user_id, effective_epoch, weekly_maximum, protein_grams, fiber_grams, salt_grams, fat_percentage,
     monday, tuesday, wednesday, thursday, friday, saturday, sunday)
SELECT id, UNIX_TIMESTAMP(updated_at), weekly_average_calorie_maximum, macro_protein_target_grams,
       nutrient_fiber_target_grams, nutrient_salt_target_grams, macro_fat_target_percentage,
       typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday,
       typical_calories_friday, typical_calories_saturday, typical_calories_sunday
FROM users WHERE macro_fat_target_percentage IS NOT NULL
ON DUPLICATE KEY UPDATE fat_percentage=VALUES(fat_percentage);
