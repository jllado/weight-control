ALTER TABLE meal_dishes
    ADD COLUMN salt_grams DECIMAL(10,2) NULL,
    ADD COLUMN reference_salt_grams DECIMAL(10,2) NULL,
    ADD COLUMN salt_source VARCHAR(500) NULL,
    ADD COLUMN salt_estimated BOOLEAN NULL;

ALTER TABLE catalog_foods
    ADD COLUMN salt_grams DECIMAL(10,2) NULL,
    ADD COLUMN reference_salt_grams DECIMAL(10,2) NULL,
    ADD COLUMN salt_source VARCHAR(500) NULL,
    ADD COLUMN salt_estimated BOOLEAN NULL;

ALTER TABLE recipe_ingredients
    ADD COLUMN salt_grams DECIMAL(10,2) NULL,
    ADD COLUMN reference_salt_grams DECIMAL(10,2) NULL,
    ADD COLUMN salt_source VARCHAR(500) NULL,
    ADD COLUMN salt_estimated BOOLEAN NULL;

ALTER TABLE users ADD COLUMN nutrient_salt_target_grams DECIMAL(10,2) NULL;
