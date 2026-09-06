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
class DishRecipePersistenceTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("recipes");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }
    @Autowired DishRecipeService service;
    @Autowired MealService meals;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired Validator validator;

    @Test void storesOrderedIndependentFoodsAndPreservesMealsAfterRecipeChangesAndDeletion() {
        var user = user("recipe-roundtrip");
        var ingredients = List.of(food("Rice", "50"), food("Chicken", "100"));
        var meal = meals.create(user, new MealRequest(LocalDate.of(2026, 8, 12), MealType.SNACK, 0, null, null, null, null, null, ingredients, null));
        var recipe = service.create(user, new RecipeRequest(" Chicken and rice ", new BigDecimal("2.5"), ingredients));
        assertEquals("Chicken and rice", recipe.name());
        assertEquals(List.of("Rice", "Chicken"), recipe.ingredients().stream().map(MealDishRequest::name).toList());
        assertEquals(51, recipe.ingredients().getFirst().calories());
        assertNull(recipe.ingredients().getFirst().carbohydrateGrams());
        assertEquals(101, recipe.ingredients().getFirst().reference().calories());
        assertEquals(1, service.findAll(user).size());
        service.update(user, recipe.id(), new RecipeRequest("Changed", BigDecimal.ONE, List.of(food("Oil", "200"))));
        assertEquals("Oil", service.find(user, recipe.id()).ingredients().getFirst().name());
        assertEquals(1, jdbc.queryForObject("select count(*) from recipe_ingredients where recipe_id = ?", Integer.class, recipe.id()));
        service.delete(user, recipe.id());
        assertEquals(0, jdbc.queryForObject("select count(*) from recipe_ingredients where recipe_id = ?", Integer.class, recipe.id()));
        assertEquals(152, meals.findAll(user).stream().filter(item -> item.getId().equals(meal.getId())).findFirst().orElseThrow().getCalories());
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
        var invalid = new MealDishRequest("Missing quantity", 1, null, null, null);
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
    private User user(String name) { var user = new User(); user.setEmail(name + "@example.com"); return users.save(user); }
    private MealDishRequest food(String name, String quantity) {
        return new MealDishRequest(name, 0, null, null, null, new BigDecimal(quantity), DishUnit.GRAM, new DishReference(new BigDecimal("100"), 101, new BigDecimal("1.01"), null, BigDecimal.ZERO));
    }
}
