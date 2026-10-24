package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter
public abstract class FoodPortion {
    @Column(nullable = false)
    private boolean fruit;
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

    @Column(name = "vitamin_d_micrograms", precision = 10, scale = 2)
    private BigDecimal vitaminDMicrograms;

    @Column(name = "omega3_milligrams", precision = 10, scale = 2)
    private BigDecimal omega3Milligrams;

    @Column(name = "magnesium_milligrams", precision = 10, scale = 2)
    private BigDecimal magnesiumMilligrams;

    @Column(name = "reference_vitamin_d_micrograms", precision = 10, scale = 2)
    private BigDecimal referenceVitaminDMicrograms;

    @Column(name = "reference_omega3_milligrams", precision = 10, scale = 2)
    private BigDecimal referenceOmega3Milligrams;

    @Column(name = "reference_magnesium_milligrams", precision = 10, scale = 2)
    private BigDecimal referenceMagnesiumMilligrams;

    @Column(length = 500)
    private String nutrientSource;

    private Boolean nutrientsEstimated;

    @Column(name = "epa_dha_milligrams", precision = 10, scale = 2)
    private BigDecimal epaDhaMilligrams;

    @Column(name = "reference_epa_dha_milligrams", precision = 10, scale = 2)
    private BigDecimal referenceEpaDhaMilligrams;

    @Column(name = "epa_dha_source", length = 500)
    private String epaDhaSource;

    @Column(name = "epa_dha_estimated")
    private Boolean epaDhaEstimated;

    @Column(name = "ala_milligrams", precision = 10, scale = 2)
    private BigDecimal alaMilligrams;

    @Column(name = "reference_ala_milligrams", precision = 10, scale = 2)
    private BigDecimal referenceAlaMilligrams;

    @Column(name = "ala_source", length = 500)
    private String alaSource;

    @Column(name = "ala_estimated")
    private Boolean alaEstimated;

    @Column(name = "fiber_grams", precision = 10, scale = 2)
    private BigDecimal fiberGrams;

    @Column(name = "reference_fiber_grams", precision = 10, scale = 2)
    private BigDecimal referenceFiberGrams;

    @Column(name = "fiber_source", length = 500)
    private String fiberSource;

    @Column(name = "fiber_estimated")
    private Boolean fiberEstimated;
}
