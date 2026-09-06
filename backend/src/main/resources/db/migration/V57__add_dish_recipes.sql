CREATE TABLE dish_recipes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(765) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    servings DECIMAL(11,3) NOT NULL CHECK (servings > 0),
    CONSTRAINT uk_recipe_user_name UNIQUE (user_id, normalized_name),
    CONSTRAINT fk_recipe_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE recipe_ingredients (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    position INT NOT NULL,
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
    CONSTRAINT uk_recipe_position UNIQUE (recipe_id, position),
    CONSTRAINT fk_ingredient_recipe FOREIGN KEY (recipe_id) REFERENCES dish_recipes(id) ON DELETE CASCADE
);
