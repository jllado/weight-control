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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {"app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example", "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"})
class CatalogFoodPersistenceTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("foods");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }
    @Autowired CatalogFoodService service;
    @Autowired MealService meals;
    @Autowired DishRecipeService recipes;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;

    @Test void correctsPortionsAndKeepsMealAndRecipeSnapshotsIndependent() {
        var user = user("snapshot");
        var oats = food("Oats, 60 g", 1, DishUnit.SERVING, 206);
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
    private User user(String name) { var user = new User(); user.setEmail(name + "@example.com"); return users.save(user); }
    private MealDishRequest food(String name, int quantity, DishUnit unit, int calories) {
        return new MealDishRequest(name, calories, new BigDecimal("8"), new BigDecimal("34"), new BigDecimal("4"), BigDecimal.valueOf(quantity), unit, null);
    }
    private MealRequest meal(MealDishRequest food) { return new MealRequest(LocalDate.of(2026, 8, 12), MealType.SNACK, 0, null, null, null, null, null, List.of(food), null); }
    private CoachMealRequest coach(MealDishRequest food) { return new CoachMealRequest(LocalDate.of(2026, 8, 12), MealType.SNACK, food.calories(), food.proteinGrams(), food.carbohydrateGrams(), food.fatGrams(), null, null, MealSource.GPT_IMAGE_ESTIMATE, true, List.of(new CoachMealDishRequest(food.name(), food.calories(), food.proteinGrams(), food.carbohydrateGrams(), food.fatGrams(), food.quantity(), food.unit(), food.reference())), 10); }
}
