-- Caller supplies a temporary food_cleanup table with owner-scoped, reviewed mappings:
-- id, user_id, old_name, new_name, old_quantity, new_quantity, old_unit, new_unit,
-- old_reference_quantity, new_reference_quantity, remove_food.
-- Execute in batch mode without --force: a failed assertion disconnects and rolls back.
START TRANSACTION;
SELECT f.id FROM catalog_foods f JOIN food_cleanup c ON c.id = f.id AND c.user_id = f.user_id FOR UPDATE;
CREATE TEMPORARY TABLE food_cleanup_assertion (valid BOOLEAN NOT NULL CHECK (valid = TRUE));
INSERT INTO food_cleanup_assertion
SELECT COUNT(*) = (SELECT COUNT(*) FROM food_cleanup)
FROM catalog_foods f JOIN food_cleanup c ON c.id = f.id AND c.user_id = f.user_id
WHERE BINARY f.name = BINARY c.old_name AND f.deleted = FALSE
    AND f.quantity = c.old_quantity AND f.unit = c.old_unit AND f.reference_quantity = c.old_reference_quantity;
CREATE TEMPORARY TABLE food_cleanup_original AS
SELECT f.* FROM catalog_foods f JOIN food_cleanup c ON c.id = f.id AND c.user_id = f.user_id;
UPDATE catalog_foods f JOIN food_cleanup c ON c.id = f.id AND c.user_id = f.user_id
SET f.name = c.new_name, f.normalized_name = LOWER(TRIM(c.new_name)), f.deleted = c.remove_food,
    f.quantity = c.new_quantity, f.unit = c.new_unit, f.reference_quantity = c.new_reference_quantity;
-- Preserve retired names as tombstones, just like normal catalog renames.
INSERT INTO catalog_foods (user_id, name, normalized_name, deleted, quantity, unit, calories,
    protein_grams, carbohydrate_grams, fat_grams, reference_quantity, reference_calories,
    reference_protein_grams, reference_carbohydrate_grams, reference_fat_grams)
SELECT o.user_id, o.name, o.normalized_name, TRUE, o.quantity, o.unit, o.calories,
    o.protein_grams, o.carbohydrate_grams, o.fat_grams, o.reference_quantity, o.reference_calories,
    o.reference_protein_grams, o.reference_carbohydrate_grams, o.reference_fat_grams
FROM food_cleanup_original o JOIN food_cleanup c ON c.id = o.id
WHERE BINARY o.normalized_name <> BINARY LOWER(TRIM(c.new_name));
COMMIT;
