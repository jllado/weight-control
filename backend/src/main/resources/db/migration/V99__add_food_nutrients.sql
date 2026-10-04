-- Values are populated by the reviewed owner-scoped nutrient migration. Unknown is not zero.
ALTER TABLE catalog_foods
    ADD COLUMN vitamin_d_micrograms DECIMAL(10,2) CHECK (vitamin_d_micrograms >= 0),
    ADD COLUMN omega3_milligrams DECIMAL(10,2) CHECK (omega3_milligrams >= 0),
    ADD COLUMN magnesium_milligrams DECIMAL(10,2) CHECK (magnesium_milligrams >= 0),
    ADD COLUMN reference_vitamin_d_micrograms DECIMAL(10,2) CHECK (reference_vitamin_d_micrograms >= 0),
    ADD COLUMN reference_omega3_milligrams DECIMAL(10,2) CHECK (reference_omega3_milligrams >= 0),
    ADD COLUMN reference_magnesium_milligrams DECIMAL(10,2) CHECK (reference_magnesium_milligrams >= 0),
    ADD COLUMN nutrient_source VARCHAR(500),
    ADD COLUMN nutrients_estimated BOOLEAN;

ALTER TABLE recipe_ingredients
    ADD COLUMN vitamin_d_micrograms DECIMAL(10,2) CHECK (vitamin_d_micrograms >= 0),
    ADD COLUMN omega3_milligrams DECIMAL(10,2) CHECK (omega3_milligrams >= 0),
    ADD COLUMN magnesium_milligrams DECIMAL(10,2) CHECK (magnesium_milligrams >= 0),
    ADD COLUMN reference_vitamin_d_micrograms DECIMAL(10,2) CHECK (reference_vitamin_d_micrograms >= 0),
    ADD COLUMN reference_omega3_milligrams DECIMAL(10,2) CHECK (reference_omega3_milligrams >= 0),
    ADD COLUMN reference_magnesium_milligrams DECIMAL(10,2) CHECK (reference_magnesium_milligrams >= 0),
    ADD COLUMN nutrient_source VARCHAR(500),
    ADD COLUMN nutrients_estimated BOOLEAN;

ALTER TABLE meal_dishes
    ADD COLUMN vitamin_d_micrograms DECIMAL(10,2) CHECK (vitamin_d_micrograms >= 0),
    ADD COLUMN omega3_milligrams DECIMAL(10,2) CHECK (omega3_milligrams >= 0),
    ADD COLUMN magnesium_milligrams DECIMAL(10,2) CHECK (magnesium_milligrams >= 0),
    ADD COLUMN reference_vitamin_d_micrograms DECIMAL(10,2) CHECK (reference_vitamin_d_micrograms >= 0),
    ADD COLUMN reference_omega3_milligrams DECIMAL(10,2) CHECK (reference_omega3_milligrams >= 0),
    ADD COLUMN reference_magnesium_milligrams DECIMAL(10,2) CHECK (reference_magnesium_milligrams >= 0),
    ADD COLUMN nutrient_source VARCHAR(500),
    ADD COLUMN nutrients_estimated BOOLEAN;
