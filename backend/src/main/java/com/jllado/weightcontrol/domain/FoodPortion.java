package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter
public abstract class FoodPortion {
    @Column(nullable = false, precision = 11, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DishUnit unit;

    @Column(name = "reference_quantity", nullable = false, precision = 11, scale = 3)
    private BigDecimal referenceQuantity;

    @Column(name = "reference_calories", nullable = false)
    private Integer referenceCalories;

    @Column(name = "reference_protein_grams", precision = 10, scale = 2)
    private BigDecimal referenceProteinGrams;

    @Column(name = "reference_carbohydrate_grams", precision = 10, scale = 2)
    private BigDecimal referenceCarbohydrateGrams;

    @Column(name = "reference_fat_grams", precision = 10, scale = 2)
    private BigDecimal referenceFatGrams;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private Integer calories;

    @Column(name = "protein_grams", precision = 10, scale = 2)
    private BigDecimal proteinGrams;

    @Column(name = "carbohydrate_grams", precision = 10, scale = 2)
    private BigDecimal carbohydrateGrams;

    @Column(name = "fat_grams", precision = 10, scale = 2)
    private BigDecimal fatGrams;
}
