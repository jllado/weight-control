-- Caller supplies owner-scoped nutrient_backfill mappings with kind, id, user_id,
-- old_name, old_quantity, old_unit, old_reference_quantity, old_calories, old_reference_calories,
-- reference_vitamin_d_micrograms, reference_omega3_milligrams, reference_magnesium_milligrams,
-- nutrient_source and nutrients_estimated. Use reviewed values; no network requests.
-- Execute in one MariaDB batch session without --force. An assertion failure rolls back on disconnect.
START TRANSACTION;
CREATE TEMPORARY TABLE nutrient_assertion (valid BOOLEAN NOT NULL CHECK (valid = TRUE));
INSERT INTO nutrient_assertion SELECT COUNT(*) > 0 FROM nutrient_backfill;
INSERT INTO nutrient_assertion SELECT COUNT(*) = COUNT(DISTINCT kind, id) FROM nutrient_backfill;
INSERT INTO nutrient_assertion SELECT COUNT(*) = 0 FROM nutrient_backfill
WHERE kind NOT IN ('catalog_foods', 'meal_dishes', 'recipe_ingredients')
    OR reference_vitamin_d_micrograms IS NULL OR reference_vitamin_d_micrograms < 0
    OR reference_omega3_milligrams IS NULL OR reference_omega3_milligrams < 0
    OR reference_magnesium_milligrams IS NULL OR reference_magnesium_milligrams < 0
    OR nutrient_source IS NULL OR TRIM(nutrient_source) = '' OR nutrients_estimated IS NULL;
SELECT f.id FROM catalog_foods f JOIN nutrient_backfill b ON b.kind = 'catalog_foods' AND b.id = f.id AND b.user_id = f.user_id FOR UPDATE;
INSERT INTO nutrient_assertion
SELECT COUNT(*) = (SELECT COUNT(*) FROM nutrient_backfill WHERE kind = 'catalog_foods')
FROM catalog_foods f JOIN nutrient_backfill b ON b.kind = 'catalog_foods' AND b.id = f.id AND b.user_id = f.user_id
WHERE BINARY f.name = BINARY b.old_name AND f.quantity = b.old_quantity AND f.unit = b.old_unit
    AND f.reference_quantity = b.old_reference_quantity AND f.calories = b.old_calories AND f.reference_calories = b.old_reference_calories
    AND f.vitamin_d_micrograms IS NULL AND f.omega3_milligrams IS NULL AND f.magnesium_milligrams IS NULL
    AND f.reference_vitamin_d_micrograms IS NULL AND f.reference_omega3_milligrams IS NULL AND f.reference_magnesium_milligrams IS NULL
    AND f.nutrient_source IS NULL AND f.nutrients_estimated IS NULL;
UPDATE catalog_foods f JOIN nutrient_backfill b ON b.kind = 'catalog_foods' AND b.id = f.id AND b.user_id = f.user_id
SET f.reference_vitamin_d_micrograms = b.reference_vitamin_d_micrograms,
    f.reference_omega3_milligrams = b.reference_omega3_milligrams,
    f.reference_magnesium_milligrams = b.reference_magnesium_milligrams,
    f.vitamin_d_micrograms = ROUND(b.reference_vitamin_d_micrograms * f.quantity / f.reference_quantity, 2),
    f.omega3_milligrams = ROUND(b.reference_omega3_milligrams * f.quantity / f.reference_quantity, 2),
    f.magnesium_milligrams = ROUND(b.reference_magnesium_milligrams * f.quantity / f.reference_quantity, 2),
    f.nutrient_source = b.nutrient_source, f.nutrients_estimated = b.nutrients_estimated;
SELECT f.id FROM meal_dishes f JOIN meals p ON p.id = f.meal_id JOIN nutrient_backfill b ON b.kind = 'meal_dishes' AND b.id = f.id AND b.user_id = p.user_id FOR UPDATE;
INSERT INTO nutrient_assertion
SELECT COUNT(*) = (SELECT COUNT(*) FROM nutrient_backfill WHERE kind = 'meal_dishes')
FROM meal_dishes f JOIN meals p ON p.id = f.meal_id JOIN nutrient_backfill b ON b.kind = 'meal_dishes' AND b.id = f.id AND b.user_id = p.user_id
WHERE BINARY f.name = BINARY b.old_name AND f.quantity = b.old_quantity AND f.unit = b.old_unit
    AND f.reference_quantity = b.old_reference_quantity AND f.calories = b.old_calories AND f.reference_calories = b.old_reference_calories
    AND f.vitamin_d_micrograms IS NULL AND f.omega3_milligrams IS NULL AND f.magnesium_milligrams IS NULL
    AND f.reference_vitamin_d_micrograms IS NULL AND f.reference_omega3_milligrams IS NULL AND f.reference_magnesium_milligrams IS NULL
    AND f.nutrient_source IS NULL AND f.nutrients_estimated IS NULL;
UPDATE meal_dishes f JOIN meals p ON p.id = f.meal_id JOIN nutrient_backfill b ON b.kind = 'meal_dishes' AND b.id = f.id AND b.user_id = p.user_id
SET f.reference_vitamin_d_micrograms = b.reference_vitamin_d_micrograms,
    f.reference_omega3_milligrams = b.reference_omega3_milligrams,
    f.reference_magnesium_milligrams = b.reference_magnesium_milligrams,
    f.vitamin_d_micrograms = ROUND(b.reference_vitamin_d_micrograms * f.quantity / f.reference_quantity, 2),
    f.omega3_milligrams = ROUND(b.reference_omega3_milligrams * f.quantity / f.reference_quantity, 2),
    f.magnesium_milligrams = ROUND(b.reference_magnesium_milligrams * f.quantity / f.reference_quantity, 2),
    f.nutrient_source = b.nutrient_source, f.nutrients_estimated = b.nutrients_estimated;
SELECT f.id FROM recipe_ingredients f JOIN dish_recipes p ON p.id = f.recipe_id JOIN nutrient_backfill b ON b.kind = 'recipe_ingredients' AND b.id = f.id AND b.user_id = p.user_id FOR UPDATE;
INSERT INTO nutrient_assertion
SELECT COUNT(*) = (SELECT COUNT(*) FROM nutrient_backfill WHERE kind = 'recipe_ingredients')
FROM recipe_ingredients f JOIN dish_recipes p ON p.id = f.recipe_id JOIN nutrient_backfill b ON b.kind = 'recipe_ingredients' AND b.id = f.id AND b.user_id = p.user_id
WHERE BINARY f.name = BINARY b.old_name AND f.quantity = b.old_quantity AND f.unit = b.old_unit
    AND f.reference_quantity = b.old_reference_quantity AND f.calories = b.old_calories AND f.reference_calories = b.old_reference_calories
    AND f.vitamin_d_micrograms IS NULL AND f.omega3_milligrams IS NULL AND f.magnesium_milligrams IS NULL
    AND f.reference_vitamin_d_micrograms IS NULL AND f.reference_omega3_milligrams IS NULL AND f.reference_magnesium_milligrams IS NULL
    AND f.nutrient_source IS NULL AND f.nutrients_estimated IS NULL;
UPDATE recipe_ingredients f JOIN dish_recipes p ON p.id = f.recipe_id JOIN nutrient_backfill b ON b.kind = 'recipe_ingredients' AND b.id = f.id AND b.user_id = p.user_id
SET f.reference_vitamin_d_micrograms = b.reference_vitamin_d_micrograms,
    f.reference_omega3_milligrams = b.reference_omega3_milligrams,
    f.reference_magnesium_milligrams = b.reference_magnesium_milligrams,
    f.vitamin_d_micrograms = ROUND(b.reference_vitamin_d_micrograms * f.quantity / f.reference_quantity, 2),
    f.omega3_milligrams = ROUND(b.reference_omega3_milligrams * f.quantity / f.reference_quantity, 2),
    f.magnesium_milligrams = ROUND(b.reference_magnesium_milligrams * f.quantity / f.reference_quantity, 2),
    f.nutrient_source = b.nutrient_source, f.nutrients_estimated = b.nutrients_estimated;
COMMIT;
