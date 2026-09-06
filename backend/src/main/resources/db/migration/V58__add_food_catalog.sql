CREATE TABLE catalog_foods (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    normalized_name VARCHAR(765) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    name VARCHAR(255) NOT NULL,
    quantity DECIMAL(11,3) NOT NULL CHECK (quantity > 0),
    unit VARCHAR(20) NOT NULL,
    calories INT NOT NULL,
    protein_grams DECIMAL(10,2),
    carbohydrate_grams DECIMAL(10,2),
    fat_grams DECIMAL(10,2),
    reference_quantity DECIMAL(11,3) NOT NULL CHECK (reference_quantity > 0),
    reference_calories INT NOT NULL,
    reference_protein_grams DECIMAL(10,2),
    reference_carbohydrate_grams DECIMAL(10,2),
    reference_fat_grams DECIMAL(10,2),
    CONSTRAINT uk_catalog_food_user_name UNIQUE (user_id, normalized_name),
    CONSTRAINT fk_catalog_food_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

INSERT INTO catalog_foods (user_id, name, normalized_name, quantity, unit, calories, protein_grams,
    carbohydrate_grams, fat_grams, reference_quantity, reference_calories, reference_protein_grams,
    reference_carbohydrate_grams, reference_fat_grams)
SELECT user_id, TRIM(name), normalized_name, quantity, unit, calories, protein_grams,
    carbohydrate_grams, fat_grams, reference_quantity, reference_calories, reference_protein_grams,
    reference_carbohydrate_grams, reference_fat_grams
FROM (
    SELECT d.*, m.user_id, LOWER(TRIM(d.name)) COLLATE utf8mb4_bin AS normalized_name,
        ROW_NUMBER() OVER (PARTITION BY m.user_id, LOWER(TRIM(d.name)) COLLATE utf8mb4_bin
            ORDER BY m.meal_date DESC, m.id DESC, d.position DESC) AS food_rank
    FROM meal_dishes d JOIN meals m ON m.id = d.meal_id
) ranked WHERE food_rank = 1;
