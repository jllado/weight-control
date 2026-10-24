ALTER TABLE users
    ADD COLUMN nutrient_epa_dha_target_milligrams DECIMAL(10,2) NULL,
    ADD COLUMN nutrient_ala_target_milligrams DECIMAL(10,2) NULL,
    ADD COLUMN nutrient_fiber_target_grams DECIMAL(10,2) NULL,
    ADD COLUMN macro_protein_target_grams DECIMAL(10,2) NULL,
    ADD COLUMN macro_fat_target_percentage DECIMAL(5,2) NULL,
    ADD CONSTRAINT chk_epa_target CHECK (nutrient_epa_dha_target_milligrams IS NULL OR nutrient_epa_dha_target_milligrams > 0),
    ADD CONSTRAINT chk_ala_target CHECK (nutrient_ala_target_milligrams IS NULL OR nutrient_ala_target_milligrams > 0),
    ADD CONSTRAINT chk_fiber_target CHECK (nutrient_fiber_target_grams IS NULL OR nutrient_fiber_target_grams > 0),
    ADD CONSTRAINT chk_protein_target CHECK (macro_protein_target_grams IS NULL OR macro_protein_target_grams > 0),
    ADD CONSTRAINT chk_fat_percentage_target CHECK (macro_fat_target_percentage IS NULL OR
        (macro_fat_target_percentage > 0 AND macro_fat_target_percentage <= 100));
