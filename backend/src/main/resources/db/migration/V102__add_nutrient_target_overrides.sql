ALTER TABLE users
    ADD COLUMN nutrient_vitamin_d_target_micrograms DECIMAL(10, 2) NULL,
    ADD COLUMN nutrient_omega3_target_milligrams DECIMAL(10, 2) NULL,
    ADD COLUMN nutrient_magnesium_target_milligrams DECIMAL(10, 2) NULL;
