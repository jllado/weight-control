ALTER TABLE meal_dishes
    ADD COLUMN saturated_fat_grams DECIMAL(10,2) NULL,
    ADD COLUMN reference_saturated_fat_grams DECIMAL(10,2) NULL,
    ADD COLUMN saturated_fat_source VARCHAR(500) NULL,
    ADD COLUMN saturated_fat_estimated BOOLEAN NULL;

ALTER TABLE catalog_foods
    ADD COLUMN saturated_fat_grams DECIMAL(10,2) NULL,
    ADD COLUMN reference_saturated_fat_grams DECIMAL(10,2) NULL,
    ADD COLUMN saturated_fat_source VARCHAR(500) NULL,
    ADD COLUMN saturated_fat_estimated BOOLEAN NULL;

ALTER TABLE recipe_ingredients
    ADD COLUMN saturated_fat_grams DECIMAL(10,2) NULL,
    ADD COLUMN reference_saturated_fat_grams DECIMAL(10,2) NULL,
    ADD COLUMN saturated_fat_source VARCHAR(500) NULL,
    ADD COLUMN saturated_fat_estimated BOOLEAN NULL;

ALTER TABLE meals
    ADD COLUMN estimated_saturated_fat_grams DECIMAL(10,2) NULL,
    ADD COLUMN saturated_fat_estimate_source VARCHAR(500) NULL;
