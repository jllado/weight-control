package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.CatalogFood;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.FoodPortion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CatalogFoodRepository extends JpaRepository<CatalogFood, Long> {
    long countByUserAndDeletedFalse(User user);
    List<CatalogFood> findByUserAndDeletedFalseOrderByNameAsc(User user);
    Optional<CatalogFood> findByUserAndNormalizedName(User user, String normalizedName);

    @Modifying
    @Query(value = """
        INSERT INTO catalog_foods (user_id, name, normalized_name, deleted, fruit, quantity, unit,
            calories, protein_grams, carbohydrate_grams, fat_grams, reference_quantity,
            reference_calories, reference_protein_grams, reference_carbohydrate_grams, reference_fat_grams, vitamin_d_micrograms, omega3_milligrams, magnesium_milligrams, reference_vitamin_d_micrograms, reference_omega3_milligrams, reference_magnesium_milligrams, nutrient_source, nutrients_estimated)
        VALUES (:userId, :name, :normalizedName, false, :#{#food.fruit}, :#{#food.quantity}, :#{#food.unit.name()},
            :#{#food.calories}, :#{#food.proteinGrams}, :#{#food.carbohydrateGrams}, :#{#food.fatGrams},
            :#{#food.referenceQuantity}, :#{#food.referenceCalories}, :#{#food.referenceProteinGrams},
            :#{#food.referenceCarbohydrateGrams}, :#{#food.referenceFatGrams}, :#{#food.vitaminDMicrograms}, :#{#food.omega3Milligrams}, :#{#food.magnesiumMilligrams}, :#{#food.referenceVitaminDMicrograms}, :#{#food.referenceOmega3Milligrams}, :#{#food.referenceMagnesiumMilligrams}, :#{#food.nutrientSource}, :#{#food.nutrientsEstimated})
        ON DUPLICATE KEY UPDATE id = id
        """, nativeQuery = true)
    void insertIfAbsent(@Param("userId") Long userId, @Param("name") String name,
        @Param("normalizedName") String normalizedName, @Param("food") FoodPortion food);
}
