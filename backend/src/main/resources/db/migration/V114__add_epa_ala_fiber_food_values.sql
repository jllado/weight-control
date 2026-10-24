ALTER TABLE meal_dishes
    ADD COLUMN epa_dha_milligrams DECIMAL(10,2) NULL,
    ADD COLUMN reference_epa_dha_milligrams DECIMAL(10,2) NULL,
    ADD COLUMN epa_dha_source VARCHAR(500) NULL,
    ADD COLUMN epa_dha_estimated BOOLEAN NULL,
    ADD COLUMN ala_milligrams DECIMAL(10,2) NULL,
    ADD COLUMN reference_ala_milligrams DECIMAL(10,2) NULL,
    ADD COLUMN ala_source VARCHAR(500) NULL,
    ADD COLUMN ala_estimated BOOLEAN NULL,
    ADD COLUMN fiber_grams DECIMAL(10,2) NULL,
    ADD COLUMN reference_fiber_grams DECIMAL(10,2) NULL,
    ADD COLUMN fiber_source VARCHAR(500) NULL,
    ADD COLUMN fiber_estimated BOOLEAN NULL;

ALTER TABLE catalog_foods
    ADD COLUMN epa_dha_milligrams DECIMAL(10,2) NULL,
    ADD COLUMN reference_epa_dha_milligrams DECIMAL(10,2) NULL,
    ADD COLUMN epa_dha_source VARCHAR(500) NULL,
    ADD COLUMN epa_dha_estimated BOOLEAN NULL,
    ADD COLUMN ala_milligrams DECIMAL(10,2) NULL,
    ADD COLUMN reference_ala_milligrams DECIMAL(10,2) NULL,
    ADD COLUMN ala_source VARCHAR(500) NULL,
    ADD COLUMN ala_estimated BOOLEAN NULL,
    ADD COLUMN fiber_grams DECIMAL(10,2) NULL,
    ADD COLUMN reference_fiber_grams DECIMAL(10,2) NULL,
    ADD COLUMN fiber_source VARCHAR(500) NULL,
    ADD COLUMN fiber_estimated BOOLEAN NULL;

ALTER TABLE recipe_ingredients
    ADD COLUMN epa_dha_milligrams DECIMAL(10,2) NULL,
    ADD COLUMN reference_epa_dha_milligrams DECIMAL(10,2) NULL,
    ADD COLUMN epa_dha_source VARCHAR(500) NULL,
    ADD COLUMN epa_dha_estimated BOOLEAN NULL,
    ADD COLUMN ala_milligrams DECIMAL(10,2) NULL,
    ADD COLUMN reference_ala_milligrams DECIMAL(10,2) NULL,
    ADD COLUMN ala_source VARCHAR(500) NULL,
    ADD COLUMN ala_estimated BOOLEAN NULL,
    ADD COLUMN fiber_grams DECIMAL(10,2) NULL,
    ADD COLUMN reference_fiber_grams DECIMAL(10,2) NULL,
    ADD COLUMN fiber_source VARCHAR(500) NULL,
    ADD COLUMN fiber_estimated BOOLEAN NULL;
