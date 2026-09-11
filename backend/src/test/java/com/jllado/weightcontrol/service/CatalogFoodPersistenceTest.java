package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import com.jllado.weightcontrol.api.dto.DishRecipeDtos.RecipeRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.UserRepository;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {"app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example", "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"})
class CatalogFoodPersistenceTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean
        @ServiceConnection
        MariaDBContainer<?> database() {
            return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("foods");
        }
    }

    @Autowired CatalogFoodService service;
    @Autowired HealthDataContextService context;
    @Autowired MealService meals;
    @Autowired DishRecipeService recipes;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;

    @Test void coachCatalogsAreCurrentScopedAndNeverCountAsConsumption() throws Exception {
        var owner = user("coach-catalog-owner");
        var other = user("coach-catalog-other");
        var unknown = new MealDishRequest("Rice", 101, new BigDecimal("1.01"), null, BigDecimal.ZERO, new BigDecimal("100"), DishUnit.GRAM, null);
        recipes.create(owner, new RecipeRequest("Rice bowl", new BigDecimal("2.5"), List.of(unknown, food("Oil", 10, DishUnit.GRAM, 90))));
        recipes.create(owner, new RecipeRequest("Breakfast", BigDecimal.ONE, List.of(unknown)));
        service.create(owner, unknown);
        service.create(owner, food("Apple", 1, DishUnit.UNIT, 80));
        var deleted = service.create(owner, food("Deleted food", 1, DishUnit.UNIT, 20));
        service.delete(owner, deleted.id());
        service.create(other, food("Private food", 1, DishUnit.UNIT, 30));
        recipes.create(other, new RecipeRequest("Private recipe", BigDecimal.ONE, List.of(unknown)));
        var catalog = context.getCoachCatalog(owner).domains().stream().collect(java.util.stream.Collectors.toMap(
            com.jllado.weightcontrol.api.dto.CoachDtos.DomainAvailability::domain, java.util.function.Function.identity()));
        for (var domain : List.of(CoachDomain.DISHES, CoachDomain.FOODS)) {
            assertEquals(2, catalog.get(domain).recordCount());
            assertNull(catalog.get(domain).firstDate());
            assertNull(catalog.get(domain).lastDate());
        }
        assertEquals(0, catalog.get(CoachDomain.NUTRITION).recordCount());
        var date = LocalDate.of(2020, 1, 1);
        var response = context.getHealthContext(owner, date, date, java.util.Set.of(CoachDomain.DISHES, CoachDomain.FOODS));
        assertEquals(java.util.Set.of(CoachDomain.DISHES, CoachDomain.FOODS), response.data().keySet());
        var dishes = ((com.jllado.weightcontrol.api.dto.CoachDtos.DishesContext) response.data().get(CoachDomain.DISHES)).dishes();
        var foods = ((com.jllado.weightcontrol.api.dto.CoachDtos.FoodsContext) response.data().get(CoachDomain.FOODS)).foods();
        assertEquals(List.of("Breakfast", "Rice bowl"), dishes.stream().map(com.jllado.weightcontrol.api.dto.CoachDtos.SavedDishData::name).toList());
        assertEquals(List.of("Apple", "Rice"), foods.stream().map(com.jllado.weightcontrol.api.dto.CoachDtos.NutritionDishData::name).toList());
        assertEquals(new BigDecimal("2.500"), dishes.get(1).servings());
        assertEquals(List.of("Rice", "Oil"), dishes.get(1).ingredients().stream().map(MealDishRequest::name).toList());
        assertNull(foods.get(1).carbohydrateGrams());
        assertEquals(101, foods.get(1).reference().calories());
        String json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(response);
        for (String excluded : List.of("\"id\"", "userId", owner.getEmail(), "Private", "Deleted food")) assertFalse(json.contains(excluded));
        assertTrue(meals.findAll(owner).isEmpty());
    }

    @Test void coachReusesFractionalRecipePortionsOnlyAfterConfirmationAndPreservesSnapshots() {
        var owner = user("coach-recipe-meal");
        var reference = new DishReference(new BigDecimal("100"), 101, new BigDecimal("1.01"), null, BigDecimal.ZERO);
        var recipe = recipes.create(owner, new RecipeRequest("Rice bowl", new BigDecimal("2.5"), List.of(
            new MealDishRequest("Rice", 0, null, null, null, new BigDecimal("50"), DishUnit.GRAM, reference),
            new MealDishRequest("Oil", 0, null, null, null, new BigDecimal("50"), DishUnit.GRAM,
                new DishReference(new BigDecimal("100"), 901, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"))))));
        var date = LocalDate.of(2026, 8, 12);
        var response = context.getHealthContext(owner, date, date, java.util.Set.of(CoachDomain.DISHES));
        var saved = ((com.jllado.weightcontrol.api.dto.CoachDtos.DishesContext) response.data().get(CoachDomain.DISHES)).dishes().getFirst();
        var rice = saved.ingredients().getFirst();
        assertNull(rice.carbohydrateGrams());
        // 1.25 servings of a 2.5-serving recipe: 25 g, 25 kcal, 0.25 g protein.
        // Coach explicitly estimates the missing carbohydrate value before confirmation and resets the reference.
        var proposed = new CoachMealDishRequest(rice.name(), 25, new BigDecimal("0.25"), new BigDecimal("5.50"), BigDecimal.ZERO,
            new BigDecimal("25"), rice.unit(), null);
        var oil = saved.ingredients().get(1);
        var scaledOil = new CoachMealDishRequest(oil.name(), 225, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("25"),
            new BigDecimal("25"), oil.unit(), oil.reference());
        var rejected = new CoachMealRequest(date, MealType.LUNCH, 250, proposed.proteinGrams(), proposed.carbohydrateGrams(), scaledOil.fatGrams(),
            java.time.LocalTime.NOON, "1.25 servings of Rice bowl; carbohydrates estimated", MealSource.MANUAL, false, List.of(proposed, scaledOil), 20);
        assertThrows(BadRequestException.class, () -> meals.createConfirmed(owner, rejected));
        assertTrue(meals.findAll(owner).isEmpty());
        var confirmed = new CoachMealRequest(date, MealType.LUNCH, 250, proposed.proteinGrams(), proposed.carbohydrateGrams(), scaledOil.fatGrams(),
            java.time.LocalTime.NOON, rejected.notes(), MealSource.MANUAL, true, List.of(proposed, scaledOil), 20);
        var meal = meals.createConfirmed(owner, confirmed);
        recipes.update(owner, recipe.id(), new RecipeRequest("Changed recipe", BigDecimal.ONE, List.of(food("Oil", 10, DishUnit.GRAM, 90))));
        var foodId = service.create(owner, rice).id();
        service.update(owner, foodId, food("Changed food", 100, DishUnit.GRAM, 999));
        var nutrition = (com.jllado.weightcontrol.api.dto.CoachDtos.NutritionContext) context.getHealthContext(owner, date, date, java.util.Set.of(CoachDomain.NUTRITION)).data().get(CoachDomain.NUTRITION);
        assertEquals(250, nutrition.dailyTotals().getFirst().calories());
        assertEquals(225, nutrition.meals().getFirst().dishes().get(1).calories());
        assertEquals(901, nutrition.meals().getFirst().dishes().get(1).reference().calories());
        assertTrue(nutrition.dailyTotals().getFirst().macrosComplete());
        var stored = nutrition.meals().getFirst().dishes().getFirst();
        assertEquals("Rice", stored.name());
        assertEquals(new BigDecimal("25.000"), stored.quantity());
        assertEquals(new BigDecimal("0.25"), stored.proteinGrams());
        assertEquals(25, stored.reference().calories());
        assertEquals(meal.getId(), meals.findAll(owner).getFirst().getId());
    }

    @Test void correctsPortionsAndKeepsMealAndRecipeSnapshotsIndependent() {
        var user = user("snapshot");
        var oats = food("Oats, 60 g", 1, DishUnit.SERVING, 206);
        service.create(user, oats);
        var meal = meals.create(user, meal(oats));
        var recipe = recipes.create(user, new RecipeRequest("Breakfast", BigDecimal.ONE, List.of(oats)));
        var catalog = service.findAll(user).getFirst();
        var corrected = service.update(user, catalog.id(), food(oats.name(), 60, DishUnit.GRAM, 206));
        assertEquals(206, corrected.calories());
        assertEquals(new BigDecimal("60"), corrected.reference().quantity());
        var scaled = service.update(user, catalog.id(), new MealDishRequest(oats.name(), 206, null, null, null, new BigDecimal("120"), DishUnit.GRAM, corrected.reference()));
        assertEquals(412, scaled.calories());
        assertEquals(new BigDecimal("16.00"), scaled.proteinGrams());
        assertEquals(new BigDecimal("68.00"), scaled.carbohydrateGrams());
        assertEquals(new BigDecimal("8.00"), scaled.fatGrams());
        assertEquals("SERVING", jdbc.queryForObject("select unit from meal_dishes where meal_id = ?", String.class, meal.getId()));
        assertEquals(206, recipes.find(user, recipe.id()).ingredients().getFirst().calories());
        service.delete(user, catalog.id());
        assertTrue(service.findAll(user).isEmpty());
        assertEquals(206, meals.findAll(user).getFirst().getCalories());
        assertEquals(1, recipes.findAll(user).size());
    }

    @Test void preservesEditedAndDeletedNamesAcrossManualAndCoachWritesAndAllowsExplicitRestoration() {
        var user = user("registration");
        service.create(user, food("Oats", 1, DishUnit.SERVING, 100));
        var first = meals.create(user, meal(food(" Oats ", 1, DishUnit.SERVING, 100)));
        var id = service.findAll(user).getFirst().id();
        service.update(user, id, food("Oats", 60, DishUnit.GRAM, 206));
        meals.update(user, first.getId(), meal(food("OATS", 1, DishUnit.SERVING, 300)));
        assertEquals(206, service.findAll(user).getFirst().calories());
        service.update(user, id, food("Rolled oats", 60, DishUnit.GRAM, 206));
        var coach = coach(food("Oats", 1, DishUnit.SERVING, 400));
        var second = meals.createConfirmed(user, coach);
        assertEquals(List.of("Rolled oats"), service.findAll(user).stream().map(CatalogFoodResponse::name).toList());
        service.delete(user, id);
        meals.updateConfirmed(user, second.getId(), coach(food("Rolled oats", 1, DishUnit.SERVING, 500)));
        assertTrue(service.findAll(user).isEmpty());
        var restored = service.create(user, food("ROLLED OATS", 100, DishUnit.GRAM, 350));
        assertEquals(id, restored.id());
        assertEquals(350, restored.calories());
        service.create(user, food("Oats", 1, DishUnit.SERVING, 200));
        assertEquals(2, service.findAll(user).size());
        meals.updateConfirmed(user, second.getId(), coach(food("Milk", 60, DishUnit.MILLILITRE, 30)));
        assertEquals(3, service.findAll(user).size());
        meals.delete(user, first.getId());
        assertEquals(3, service.findAll(user).size());
    }

    @Test void enforcesOwnershipUniquenessAndRollback() {
        var owner = user("owner");
        var other = user("other");
        var request = food("Oats", 1, DishUnit.SERVING, 206);
        var saved = service.create(owner, request);
        assertThrows(BadRequestException.class, () -> service.create(owner, food(" OATS ", 1, DishUnit.SERVING, 1)));
        assertDoesNotThrow(() -> service.create(other, request));
        assertThrows(NotFoundException.class, () -> service.update(other, saved.id(), request));
        assertThrows(NotFoundException.class, () -> service.delete(other, saved.id()));
        var rice = service.create(owner, food("Rice", 1, DishUnit.SERVING, 100));
        assertThrows(BadRequestException.class, () -> service.update(owner, saved.id(), food("Rice", 1, DishUnit.SERVING, 1)));
        service.delete(owner, rice.id());
        service.update(owner, saved.id(), food("Rice", 60, DishUnit.GRAM, 206));
        assertEquals(List.of("Rice"), service.findAll(owner).stream().map(CatalogFoodResponse::name).toList());
        assertThrows(BadRequestException.class, () -> service.update(owner, saved.id(), new MealDishRequest("Invalid", 1, null, null, null)));
        assertEquals("Rice", service.findAll(owner).getFirst().name());
        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            meals.create(owner, meal(food("Rollback", 1, DishUnit.SERVING, 100)));
            throw new IllegalStateException("Rollback meal and registration together");
        }));
        assertEquals(1, service.findAll(owner).size());
        assertTrue(meals.findAll(owner).isEmpty());
        var unknown = service.create(owner, new MealDishRequest("Unknown macros", 10, null, null, null, BigDecimal.ONE, DishUnit.UNIT, null));
        assertNull(unknown.proteinGrams());
        assertNull(unknown.reference().fatGrams());
        assertThrows(BadRequestException.class, () -> meals.createConfirmed(owner, new CoachMealRequest(LocalDate.of(2026, 8, 12), MealType.SNACK, 10, null, null, null, null, null, MealSource.GPT_IMAGE_ESTIMATE, false, List.of(), null)));
    }
    @Test void registersOnlyAutomaticallySelectedNewCoachFoods() {
        var owner = user("selected-coach-foods");
        var oats = food("Oats", 100, DishUnit.GRAM, 350);
        var manual = meals.create(owner, meal(oats));
        meals.update(owner, manual.getId(), meal(food("Manual edit", 1, DishUnit.UNIT, 50)));
        assertTrue(service.findAll(owner).isEmpty());
        var uncertain = new CoachMealDishRequest("Possible oats variant", 100, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, DishUnit.SERVING, null);
        var selected = coach(oats).dishes().getFirst();
        var request = new CoachMealRequest(LocalDate.of(2026, 8, 12), MealType.SNACK, 450, null, null, null,
            java.time.LocalTime.NOON, null, MealSource.MANUAL, true, List.of(uncertain, selected), 10);
        var saved = meals.createConfirmed(owner, request);
        assertEquals(2, saved.getDishes().size());
        assertEquals(450, saved.getCalories());
        assertEquals(List.of("Oats"), service.findAll(owner).stream().map(CatalogFoodResponse::name).toList());
        meals.updateConfirmed(owner, saved.getId(), request);
        assertEquals(1, service.findAll(owner).size());
        var rejected = new CoachMealRequest(request.date(), request.mealType(), 450, null, null, null,
            request.mealTime(), null, request.source(), false, List.of(coach(food("New food", 1, DishUnit.UNIT, 20)).dishes().getFirst()), 10);
        assertThrows(BadRequestException.class, () -> meals.createConfirmed(owner, rejected));
        assertEquals(1, service.findAll(owner).size());
        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            meals.createConfirmed(owner, coach(food("Rolled back", 1, DishUnit.UNIT, 20)));
            throw new IllegalStateException("rollback");
        }));
        assertEquals(1, service.findAll(owner).size());
        var other = user("selected-other");
        meals.createConfirmed(other, coach(oats));
        assertEquals(1, service.findAll(other).size());
    }

    @Test void concurrentSelectedNamesKeepOneCatalogSnapshot() throws Exception {
        var owner = user("concurrent-registration");
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                start.await();
                transactions.executeWithoutResult(status -> {
                    var food = new MealDish();
                    food.setName(index == 0 ? "Oats" : " OATS ");
                    DishNutrition.apply(food, food("Oats", 100, DishUnit.GRAM, 350 + index));
                    service.register(owner, List.of(food));
                });
                return true;
            })).toList();
            start.countDown();
            for (var task : tasks) assertTrue(task.get(30, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1, service.findAll(owner).size());
        assertTrue(List.of(350, 351).contains(service.findAll(owner).getFirst().calories()));
    }

    private User user(String name) { var user = new User(); user.setEmail(name + "@example.com"); return users.save(user); }
    private MealDishRequest food(String name, int quantity, DishUnit unit, int calories) {
        return new MealDishRequest(name, calories, new BigDecimal("8"), new BigDecimal("34"), new BigDecimal("4"), BigDecimal.valueOf(quantity), unit, null);
    }
    private MealRequest meal(MealDishRequest food) { return new MealRequest(LocalDate.of(2026, 8, 12), MealType.SNACK, 0, null, null, null, null, null, List.of(food), null); }
    private CoachMealRequest coach(MealDishRequest food) { return new CoachMealRequest(LocalDate.of(2026, 8, 12), MealType.SNACK, food.calories(), food.proteinGrams(), food.carbohydrateGrams(), food.fatGrams(), null, null, MealSource.GPT_IMAGE_ESTIMATE, true, List.of(new CoachMealDishRequest(food.name(), food.calories(), food.proteinGrams(), food.carbohydrateGrams(), food.fatGrams(), food.quantity(), food.unit(), food.reference(), true)), 10); }
}
