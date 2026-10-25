package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import com.jllado.weightcontrol.api.dto.DishRecipeDtos.RecipeRequest;
import com.jllado.weightcontrol.api.dto.DishRecipeDtos.CoachRecipeRequest;
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
class DishRecipePersistenceTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean
        @ServiceConnection
        MariaDBContainer<?> database() {
            return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("recipes");
        }
    }

    @Autowired DishRecipeService service;
    @Autowired MealService meals;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired Validator validator;

    @Test void storesOrderedIndependentFoodsAndPreservesMealsAfterRecipeChangesAndDeletion() {
        var user = user("recipe-roundtrip");
        var fruit = new MealDishRequest("Blueberries", 30, new BigDecimal("0.20"), null, new BigDecimal("0.10"),
            new BigDecimal("25"), DishUnit.GRAM,
            new DishReference(new BigDecimal("100"), 120, new BigDecimal("0.80"), null,
                new BigDecimal("0.40"), new BigDecimal("0.20"), new BigDecimal("4.00"), new BigDecimal("8.00")),
            true, new BigDecimal("0.05"), new BigDecimal("1.00"), new BigDecimal("2.00"), "Known fruit", false);
        var ingredients = List.of(food("Rice", "50"), fruit, food("Chicken", "100"));
        var meal = meals.create(user, new MealRequest(LocalDate.of(2026, 8, 12), MealType.SNACK, 0, null, null, null, null, null, ingredients, null));
        var originalMeal = MealResponse.from(meals.findForUser(user, meal.getId()));
        assertTrue(originalMeal.dishes().get(1).fruit());
        var recipe = service.create(user, new RecipeRequest(" Chicken and rice ", new BigDecimal("2.5"), ingredients));
        assertEquals("Chicken and rice", recipe.name());
        assertEquals(List.of("Rice", "Blueberries", "Chicken"), recipe.ingredients().stream().map(MealDishRequest::name).toList());
        assertEquals(51, recipe.ingredients().getFirst().calories());
        assertNull(recipe.ingredients().getFirst().carbohydrateGrams());
        assertEquals(101, recipe.ingredients().getFirst().reference().calories());
        assertEquals(1, service.findAll(user).size());
        service.update(user, recipe.id(), new RecipeRequest("Changed", BigDecimal.ONE, List.of(food("Oil", "200"))));
        assertEquals("Oil", service.find(user, recipe.id()).ingredients().getFirst().name());
        assertEquals(1, jdbc.queryForObject("select count(*) from recipe_ingredients where recipe_id = ?", Integer.class, recipe.id()));
        assertEquals(originalMeal, MealResponse.from(meals.findForUser(user, meal.getId())));
        service.delete(user, recipe.id());
        assertEquals(0, jdbc.queryForObject("select count(*) from recipe_ingredients where recipe_id = ?", Integer.class, recipe.id()));
        assertEquals(originalMeal, MealResponse.from(meals.findForUser(user, meal.getId())));
    }

    @Test void enforcesOwnershipAndUniqueNamesAndRollsBackFailedReplacement() {
        var owner = user("recipe-owner");
        var other = user("recipe-other");
        var request = new RecipeRequest("Rice", BigDecimal.ONE, List.of(food("Rice", "100")));
        var recipe = service.create(owner, request);
        assertThrows(BadRequestException.class, () -> service.create(owner, new RecipeRequest(" RICE ", BigDecimal.ONE, request.ingredients())));
        assertDoesNotThrow(() -> service.create(other, request));
        assertThrows(NotFoundException.class, () -> service.find(other, recipe.id()));
        assertThrows(NotFoundException.class, () -> service.update(other, recipe.id(), request));
        assertThrows(NotFoundException.class, () -> service.delete(other, recipe.id()));
        var invalid = new MealDishRequest("Missing quantity", 1, null, null, null, null, null, null, false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false);
        assertThrows(BadRequestException.class, () -> service.update(owner, recipe.id(), new RecipeRequest("Broken", BigDecimal.ONE, List.of(invalid))));
        var stored = service.find(owner, recipe.id());
        assertEquals("Rice", stored.name());
        assertEquals(101, stored.ingredients().getFirst().calories());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("insert into dish_recipes(user_id, name, normalized_name, servings) values (?, 'Rice', 'rice', 1)", owner.getId()));
    }

    @Test void validatesRecipeAndIngredientContracts() {
        assertTrue(validator.validate(new RecipeRequest("Rice", new BigDecimal("0.5"), List.of(food("Rice", "50")))).isEmpty());
        assertFalse(validator.validate(new RecipeRequest(" ", BigDecimal.ONE, List.of(food("Rice", "50")))).isEmpty());
        assertFalse(validator.validate(new RecipeRequest("Rice", BigDecimal.ZERO, List.of(food("Rice", "50")))).isEmpty());
        assertFalse(validator.validate(new RecipeRequest("Rice", new BigDecimal("1.0001"), List.of(food("Rice", "50")))).isEmpty());
        assertFalse(validator.validate(new RecipeRequest("Rice", BigDecimal.ONE, List.of())).isEmpty());
        assertFalse(validator.validate(new RecipeRequest("Rice", BigDecimal.ONE, List.of(food("Rice", "0")))).isEmpty());
    }

    @Test void coachRecipeRequiresCompleteNewIngredientsAndKeepsOnlyExactOrderedLegacySnapshots() {
        var owner = user("coach-complete-recipe");
        var complete = coachIngredient("Same name", true);
        var legacy = coachIngredient("Same name", false);
        assertThrows(BadRequestException.class, () -> service.createCoach(owner,
            new CoachRecipeRequest(new RecipeRequest("Incomplete", BigDecimal.ONE, List.of(legacy), 2), true)));
        assertEquals(0, service.findAll(owner).size());

        var created = service.createCoach(owner,
            new CoachRecipeRequest(new RecipeRequest("Complete", BigDecimal.ONE, List.of(complete), 2), true));
        assertEquals(new BigDecimal("1.00"), service.find(owner, created.id()).ingredients().getFirst().epaDhaMilligrams());
        assertEquals(new BigDecimal("2.00"), service.find(owner, created.id()).ingredients().getFirst().alaMilligrams());

        var previous = service.create(owner, new RecipeRequest("Legacy", BigDecimal.ONE, List.of(complete, legacy), 2));
        service.updateCoach(owner, previous.id(),
            new CoachRecipeRequest(new RecipeRequest("Renamed", BigDecimal.ONE, List.of(complete, legacy), 2), true));
        assertEquals("Renamed", service.find(owner, previous.id()).name());
        assertThrows(BadRequestException.class, () -> service.updateCoach(owner, previous.id(),
            new CoachRecipeRequest(new RecipeRequest("Reordered", BigDecimal.ONE, List.of(legacy, complete), 2), true)));
        assertEquals("Renamed", service.find(owner, previous.id()).name());
    }

    private MealDishRequest coachIngredient(String name, boolean complete) {
        var reference = new DishReference(new BigDecimal("100"), 100, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, complete ? BigDecimal.ZERO : null, BigDecimal.ZERO,
            complete ? new BigDecimal("1.00") : null, complete ? new BigDecimal("2.00") : null,
            new BigDecimal("3.00"));
        return new MealDishRequest(name, 100, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("100"), DishUnit.GRAM, reference, false, BigDecimal.ZERO,
            complete ? BigDecimal.ZERO : null, BigDecimal.ZERO, "USDA comparable composition", true,
            complete ? new BigDecimal("1.00") : null, complete ? "USDA comparable EPA+DHA estimate" : null,
            complete ? true : null, complete ? new BigDecimal("2.00") : null,
            complete ? "USDA comparable ALA estimate" : null, complete ? true : null,
            new BigDecimal("3.00"), "USDA comparable Fiber estimate", true, 2);
    }
    private User user(String name) { var user = new User(); user.setEmail(name + "@example.com"); return users.save(user); }
    private MealDishRequest food(String name, String quantity) {
        return new MealDishRequest(name, 0, null, null, null, new BigDecimal(quantity), DishUnit.GRAM, new DishReference(new BigDecimal("100"), 101, new BigDecimal("1.01"), null, BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN), false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false);
    }
}
