package com.jllado.weightcontrol.api;

import static org.junit.jupiter.api.Assertions.*;

import com.jllado.weightcontrol.api.dto.CoachDtos;
import com.jllado.weightcontrol.api.dto.MealDtos;
import com.jllado.weightcontrol.domain.DishUnit;
import com.jllado.weightcontrol.domain.MealDish;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CoachNutritionDishDataTest {
    @Test void coachFoodReadsCarryIndependentValuesProvenanceAndReferences() {
        var dish = new MealDish();
        dish.setName("Salmon");
        dish.setCalories(200);
        dish.setQuantity(new BigDecimal("100"));
        dish.setUnit(DishUnit.GRAM);
        dish.setReferenceQuantity(new BigDecimal("100"));
        dish.setReferenceCalories(200);
        dish.setEpaDhaMilligrams(new BigDecimal("1200.00"));
        dish.setReferenceEpaDhaMilligrams(new BigDecimal("1200.00"));
        dish.setEpaDhaSource("Fish composition");
        dish.setEpaDhaEstimated(true);
        dish.setAlaMilligrams(new BigDecimal("20.00"));
        dish.setReferenceAlaMilligrams(new BigDecimal("20.00"));
        dish.setAlaSource("Fish composition");
        dish.setAlaEstimated(false);
        dish.setFiberGrams(BigDecimal.ZERO);
        dish.setReferenceFiberGrams(BigDecimal.ZERO);
        dish.setFiberSource("Fish composition");
        dish.setFiberEstimated(false);

        var coach = CoachDtos.NutritionDishData.from(dish);
        assertEquals(new BigDecimal("1200.00"), coach.epaDhaMilligrams());
        assertEquals("Fish composition", coach.epaDhaSource());
        assertTrue(coach.epaDhaEstimated());
        assertEquals(new BigDecimal("20.00"), coach.alaMilligrams());
        assertFalse(coach.alaEstimated());
        assertEquals(BigDecimal.ZERO, coach.fiberGrams());
        assertEquals(new BigDecimal("1200.00"), coach.reference().epaDhaMilligrams());
        assertEquals(new BigDecimal("20.00"), coach.reference().alaMilligrams());
        assertEquals(BigDecimal.ZERO, coach.reference().fiberGrams());

        var catalog = new MealDtos.CatalogFoodResponse(1L, coach.name(), coach.calories(), coach.proteinGrams(),
            coach.carbohydrateGrams(), coach.fatGrams(), coach.quantity(), coach.fruit(), coach.unit(), coach.reference(),
            coach.vitaminDMicrograms(), coach.omega3Milligrams(), coach.magnesiumMilligrams(), coach.nutrientSource(),
            coach.nutrientsEstimated(), coach.epaDhaMilligrams(), coach.epaDhaSource(), coach.epaDhaEstimated(),
            coach.alaMilligrams(), coach.alaSource(), coach.alaEstimated(), coach.fiberGrams(), coach.fiberSource(), coach.fiberEstimated());
        assertEquals(coach, CoachDtos.NutritionDishData.from(catalog));
    }
}
