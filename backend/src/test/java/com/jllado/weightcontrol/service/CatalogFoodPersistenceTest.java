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

    @Test void coachFoodLookupBoundsMatchesAndKeepsUserOwnership() {
        var owner = user("fiber-lookup");
        var other = user("fiber-lookup-other");
        for (int i = 0; i < 23; i++) service.create(owner, food("Oats " + i, 100, DishUnit.GRAM, 380));
        service.create(owner, food("Blueberries", 100, DishUnit.GRAM, 57));
        service.create(other, food("Oats private", 100, DishUnit.GRAM, 380));
        var deleted = service.create(owner, food("Oats deleted", 100, DishUnit.GRAM, 380));
        service.delete(owner, deleted.id());
        var date = LocalDate.of(2026, 8, 12);
        var result = context.getHealthContext(owner, date, date, java.util.Set.of(CoachDomain.FOODS), 0, 25, " OATS ");
        var foods = (com.jllado.weightcontrol.api.dto.CoachDtos.FoodsContext) result.data().get(CoachDomain.FOODS);
        assertEquals(20, foods.foods().size());
        assertEquals(23, foods.matchedFoods());
        assertTrue(foods.foods().stream().allMatch(food -> food.name().startsWith("Oats ") && !food.name().equals("Oats private") && !food.name().equals("Oats deleted")));
        var noMatch = context.getHealthContext(owner, date, date, java.util.Set.of(CoachDomain.FOODS), 0, 25, "Missing");
        assertTrue(((com.jllado.weightcontrol.api.dto.CoachDtos.FoodsContext) noMatch.data().get(CoachDomain.FOODS)).foods().isEmpty());
    }

    @Test void fiberReferencesScaleAcrossSaveReloadAndRejectLossAtomically() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var owner = user("fiber-portions");
        var json = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree("""
            {"name":"Oats","quantity":50,"unit":"GRAM","calories":190,
             "proteinGrams":6,"carbohydrateGrams":30,"fatGrams":4,
             "vitaminDMicrograms":0,"magnesiumMilligrams":50,"omega3Milligrams":null,
             "nutrientSource":"Label","nutrientsEstimated":false,"nutrientSchemaVersion":2,
             "fiberGrams":4,"fiberSource":"Label: 8g/100g","fiberEstimated":false,
             "reference":{"quantity":100,"calories":380,"proteinGrams":12,"carbohydrateGrams":60,
                "fatGrams":8,"vitaminDMicrograms":0,"magnesiumMilligrams":100,"fiberGrams":8}}
            """);
        var food = mapper.treeToValue(json, MealDishRequest.class);
        var catalog = service.create(owner, food);
        var saved = meals.create(owner, meal(food));
        assertEquals(new BigDecimal("4.00"), meals.findAll(owner).getFirst().getDishes().getFirst().getFiberGrams());
        json.put("quantity", 75).put("fiberGrams", 6);
        var edited = mapper.treeToValue(json, MealDishRequest.class);
        meals.update(owner, saved.getId(), new MealRequest(LocalDate.of(2026, 8, 12), MealType.SNACK, 0, null, null, null, null, null, List.of(edited), null, 2));
        var reloaded = meals.findAll(owner).getFirst().getDishes().getFirst();
        assertEquals(new BigDecimal("6.00"), reloaded.getFiberGrams());
        assertEquals(new BigDecimal("8.00"), reloaded.getReferenceFiberGrams());
        assertEquals("Label: 8g/100g", reloaded.getFiberSource());
        assertFalse(reloaded.getFiberEstimated());
        ((com.fasterxml.jackson.databind.node.ObjectNode) json.get("reference")).putNull("fiberGrams");
        var invalid = mapper.treeToValue(json, MealDishRequest.class);
        assertThrows(BadRequestException.class, () -> meals.update(owner, saved.getId(), new MealRequest(LocalDate.of(2026, 8, 12), MealType.SNACK, 0, null, null, null, null, null, List.of(invalid), null, 2)));
        assertEquals(new BigDecimal("6.00"), meals.findAll(owner).getFirst().getDishes().getFirst().getFiberGrams());
        assertEquals(new BigDecimal("4.00"), service.findAll(owner).getFirst().fiberGrams());
        assertEquals(catalog.id(), service.findAll(owner).getFirst().id());
    }

    @Test void newNutrientsSurviveEverySnapshotAndUnawareReplacementsAreRejected() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        var owner = user("epa-snapshots");
        var food = mapper.readValue("""
            {"name":"EPA fish","calories":100,"proteinGrams":10,"carbohydrateGrams":1,"fatGrams":1,
             "quantity":50,"unit":"GRAM","vitaminDMicrograms":1,"omega3Milligrams":900,
             "magnesiumMilligrams":10,"nutrientSource":"Legacy source","nutrientsEstimated":false,
             "epaDhaMilligrams":250,"epaDhaSource":"EPA+DHA evidence","epaDhaEstimated":true,
             "alaMilligrams":80,"alaSource":"ALA evidence","alaEstimated":false,
             "fiberGrams":2,"fiberSource":"Fiber evidence","fiberEstimated":false,
             "nutrientSchemaVersion":2}
            """, MealDishRequest.class);
        var catalog = service.create(owner, food);
        var recipe = recipes.create(owner, new RecipeRequest("EPA recipe", BigDecimal.ONE, List.of(food)));
        assertEquals(250, mapper.valueToTree(recipes.find(owner, recipe.id())).path("ingredients").get(0).path("epaDhaMilligrams").asInt());
        assertEquals(80, mapper.valueToTree(recipes.find(owner, recipe.id())).path("ingredients").get(0).path("alaMilligrams").asInt());
        assertEquals(2, mapper.valueToTree(recipes.find(owner, recipe.id())).path("ingredients").get(0).path("fiberGrams").asInt());
        var saved = meals.create(owner, meal(food));
        var coachJson = mapper.valueToTree(coach(food));
        ((com.fasterxml.jackson.databind.node.ObjectNode) coachJson).put("nutrientSchemaVersion", 2);
        ((com.fasterxml.jackson.databind.node.ObjectNode) coachJson).remove("rating");
        var coachFood = (com.fasterxml.jackson.databind.node.ObjectNode) coachJson.path("dishes").get(0);
        coachFood.put("name", "Coach EPA fish");
        coachFood.put("epaDhaMilligrams", 250).put("epaDhaSource", "EPA+DHA evidence").put("epaDhaEstimated", true);
        coachFood.put("alaMilligrams", 80).put("alaSource", "ALA evidence").put("alaEstimated", false);
        coachFood.put("fiberGrams", 2).put("fiberSource", "Fiber evidence").put("fiberEstimated", false);
        meals.updateConfirmed(owner, saved.getId(), mapper.treeToValue(coachJson, CoachMealRequest.class));
        var registered = service.findAll(owner).getFirst();
        assertEquals(250, mapper.valueToTree(registered).path("epaDhaMilligrams").asInt());
        assertEquals("Coach EPA fish", registered.name());
        assertEquals("ALA evidence", registered.alaSource());
        assertEquals(new BigDecimal("2.00"), registered.reference().fiberGrams());
        assertEquals(new BigDecimal("250.00"), meals.findAll(owner).getFirst().getDishes().getFirst().getEpaDhaMilligrams());
        assertEquals(new BigDecimal("80.00"), meals.findAll(owner).getFirst().getDishes().getFirst().getAlaMilligrams());
        assertEquals(new BigDecimal("2.00"), meals.findAll(owner).getFirst().getDishes().getFirst().getFiberGrams());
        var legacy = food("Legacy edit", 1, DishUnit.SERVING, 100);
        assertThrows(BadRequestException.class, () -> service.update(owner, catalog.id(), legacy));
        assertThrows(BadRequestException.class, () -> recipes.update(owner, recipe.id(), new RecipeRequest("Changed", BigDecimal.ONE, List.of(legacy))));
        assertThrows(BadRequestException.class, () -> recipes.update(owner, recipe.id(), new RecipeRequest("Changed", BigDecimal.ONE, List.of())));
        assertThrows(BadRequestException.class, () -> meals.updateConfirmed(owner, saved.getId(), coach(legacy)));
        assertEquals("EPA recipe", recipes.find(owner, recipe.id()).name());
        assertTrue(service.findAll(owner).stream().anyMatch(item -> item.name().equals("EPA fish")));

        var renamedJson = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(food);
        renamedJson.put("name", "Renamed EPA fish");
        service.update(owner, catalog.id(), mapper.treeToValue(renamedJson, MealDishRequest.class));
        assertEquals("EPA+DHA evidence", jdbc.queryForObject("SELECT epa_dha_source FROM catalog_foods WHERE user_id=? AND deleted=true", String.class, owner.getId()));
        assertEquals("ALA evidence", jdbc.queryForObject("SELECT ala_source FROM catalog_foods WHERE user_id=? AND deleted=true", String.class, owner.getId()));
        assertThrows(BadRequestException.class, () -> service.create(owner, food("EPA fish", 1, DishUnit.SERVING, 100)));
        assertEquals("EPA+DHA evidence", jdbc.queryForObject("SELECT epa_dha_source FROM catalog_foods WHERE user_id=? AND deleted=true", String.class, owner.getId()));
        var renameSource = service.create(owner, food("Rename source", 1, DishUnit.SERVING, 100));
        assertThrows(BadRequestException.class, () -> service.update(owner, renameSource.id(), food("EPA fish", 1, DishUnit.SERVING, 101)));
        assertTrue(service.findAll(owner).stream().anyMatch(item -> item.name().equals("Rename source")));
        assertEquals("EPA+DHA evidence", jdbc.queryForObject("SELECT epa_dha_source FROM catalog_foods WHERE user_id=? AND normalized_name='epa fish' AND deleted=true", String.class, owner.getId()));
        var unknownArchived = service.create(owner, food("Unknown archived", 1, DishUnit.SERVING, 100));
        service.delete(owner, unknownArchived.id());
        assertEquals(unknownArchived.id(), service.create(owner, food("Unknown archived", 1, DishUnit.SERVING, 101)).id());
        var clear = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(meal(legacy));
        clear.put("nutrientSchemaVersion", 2);
        meals.update(owner, saved.getId(), mapper.treeToValue(clear, MealRequest.class));
        assertNull(meals.findAll(owner).getFirst().getDishes().getFirst().getEpaDhaMilligrams());
        assertNull(meals.findAll(owner).getFirst().getDishes().getFirst().getAlaMilligrams());
        assertNull(meals.findAll(owner).getFirst().getDishes().getFirst().getFiberGrams());
        assertEquals(250, mapper.valueToTree(recipes.find(owner, recipe.id())).path("ingredients").get(0).path("epaDhaMilligrams").asInt());
        recipes.update(owner, recipe.id(), new RecipeRequest("Cleared EPA recipe", BigDecimal.ONE, List.of(legacy), 2));
        var clearedIngredient = recipes.find(owner, recipe.id()).ingredients().getFirst();
        assertNull(clearedIngredient.epaDhaMilligrams());
        assertNull(clearedIngredient.alaMilligrams());
        assertNull(clearedIngredient.fiberGrams());
    }

    @Test void nutrientSnapshotsPersistAcrossCatalogRecipeCoachAndMealUpdates() {
        var owner = user("nutrient-snapshots");
        LocalDate date = LocalDate.of(2026, 8, 12);
        var reference = new DishReference(new BigDecimal("100"), 200, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
            new BigDecimal("2.01"), new BigDecimal("100.01"), new BigDecimal("20.01"));
        var food = new MealDishRequest("Fish", 100, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
            new BigDecimal("50"), DishUnit.GRAM, reference, false,
            BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, "Published composition; estimated portion", true);
        var catalog = service.create(owner, food);
        var recipe = recipes.create(owner, new RecipeRequest("Fish dish", BigDecimal.ONE, List.of(food)));
        var meal = meals.create(owner, new MealRequest(date, MealType.SNACK, 100, null, null, null, null, null, List.of(food), null));
        assertEquals(new BigDecimal("1.01"), service.findAll(owner).getFirst().vitaminDMicrograms());
        assertEquals(new BigDecimal("50.01"), recipes.find(owner, recipe.id()).ingredients().getFirst().omega3Milligrams());
        var snapshot = MealResponse.from(meals.findAll(owner).getFirst());
        assertEquals(new BigDecimal("10.01"), snapshot.nutrients().magnesiumMilligrams());
        assertEquals(1, snapshot.nutrients().estimatedFoods());
        var coach = new CoachMealDishRequest("New fish", 100, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
            new BigDecimal("50"), DishUnit.GRAM, reference, true, false,
            BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, "Published composition; estimated portion", true);
        meals.updateConfirmed(owner, meal.getId(), new CoachMealRequest(date, MealType.SNACK, 100, null, null, null,
            null, null, MealSource.MANUAL, true, List.of(coach), 5));
        var registered = service.findAll(owner).stream().filter(item -> item.name().equals("New fish")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("2.01"), registered.reference().vitaminDMicrograms());
        assertEquals("Published composition; estimated portion", registered.nutrientSource());
        assertTrue(registered.nutrientsEstimated());
        service.update(owner, catalog.id(), new MealDishRequest("Fish", 200, null, null, null, BigDecimal.ONE,
            DishUnit.SERVING, null, false, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, "New label", false));
        assertEquals(new BigDecimal("1.01"), recipes.find(owner, recipe.id()).ingredients().getFirst().vitaminDMicrograms());
        assertEquals(new BigDecimal("1.01"), meals.findAll(owner).getFirst().getDishes().getFirst().getVitaminDMicrograms());
        var evidence = context.getHealthContext(owner, date, date, java.util.Set.of(CoachDomain.NUTRITION));
        var daily = ((com.jllado.weightcontrol.api.dto.CoachDtos.NutritionContext) evidence.data().get(CoachDomain.NUTRITION)).dailyTotals().getFirst();
        assertEquals(new BigDecimal("50.01"), daily.nutrients().omega3Milligrams());
    }

    @Test void fruitClassificationPersistsAcrossCatalogRecipeAndMealSnapshotsAndCoachOmission() {
        var owner = user("fruit-classification");
        LocalDate date = LocalDate.of(2026, 8, 12);
        var apple = new MealDishRequest("Apple", 80, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO,
            BigDecimal.ONE, DishUnit.UNIT, new DishReference(BigDecimal.ONE, 80, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN), true, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false);
        var catalogFood = service.create(owner, apple);
        assertTrue(catalogFood.fruit());
        var recipe = recipes.create(owner, new RecipeRequest("Apple bowl", BigDecimal.ONE, List.of(apple)));
        assertTrue(recipes.find(owner, recipe.id()).ingredients().getFirst().fruit());
        var coachContext = context.getHealthContext(owner, date, date, java.util.Set.of(CoachDomain.FOODS, CoachDomain.DISHES));
        assertTrue(((com.jllado.weightcontrol.api.dto.CoachDtos.FoodsContext) coachContext.data().get(CoachDomain.FOODS)).foods().getFirst().fruit());
        assertTrue(((com.jllado.weightcontrol.api.dto.CoachDtos.DishesContext) coachContext.data().get(CoachDomain.DISHES)).dishes().getFirst().ingredients().getFirst().fruit());

        var meal = meals.create(owner, new MealRequest(date, MealType.SNACK, 80, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO,
            null, null, List.of(apple), null));
        assertTrue(meal.getDishes().getFirst().isFruit());
        var coachDish = new CoachMealDishRequest("Apple", 80, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO,
            BigDecimal.ONE, DishUnit.UNIT, apple.reference(), true, null, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false);
        var coachUpdate = new CoachMealRequest(date, MealType.SNACK, 80, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO,
            null, null, MealSource.GPT_IMAGE_ESTIMATE, true, List.of(coachDish), 5);
        assertTrue(meals.updateConfirmed(owner, meal.getId(), coachUpdate).getDishes().getFirst().isFruit());

        var renamed = new CoachMealDishRequest("Green apple", 80, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO,
            BigDecimal.ONE, DishUnit.UNIT, apple.reference(), false, null, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false);
        var oldCoachUpdate = new CoachMealRequest(date, MealType.SNACK, 80, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO,
            null, null, MealSource.GPT_IMAGE_ESTIMATE, true, List.of(renamed), 5);
        assertTrue(meals.updateConfirmed(owner, meal.getId(), oldCoachUpdate).getDishes().getFirst().isFruit());

        var notFruit = new MealDishRequest("Apple", 80, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO,
            BigDecimal.ONE, DishUnit.UNIT, apple.reference(), false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false);
        assertFalse(service.update(owner, catalogFood.id(), notFruit).fruit());
        assertFalse(recipes.update(owner, recipe.id(), new RecipeRequest("Apple bowl", BigDecimal.ONE, List.of(notFruit))).ingredients().getFirst().fruit());
        assertTrue(meals.findAll(owner).getFirst().getDishes().getFirst().isFruit());
        service.delete(owner, catalogFood.id());
        recipes.delete(owner, recipe.id());
        assertTrue(service.findAll(owner).isEmpty());
        assertTrue(recipes.findAll(owner).isEmpty());
        assertTrue(meals.findAll(owner).getFirst().getDishes().getFirst().isFruit());

        var explicitFalse = new CoachMealDishRequest("Green apple", 80, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO,
            BigDecimal.ONE, DishUnit.UNIT, apple.reference(), false, false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false);
        var explicitUpdate = new CoachMealRequest(date, MealType.SNACK, 80, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO,
            null, null, MealSource.GPT_IMAGE_ESTIMATE, true, List.of(explicitFalse), 5);
        assertFalse(meals.updateConfirmed(owner, meal.getId(), explicitUpdate).getDishes().getFirst().isFruit());
        var explicitTrue = new CoachMealDishRequest("Fresh apple", 80, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO,
            BigDecimal.ONE, DishUnit.UNIT, apple.reference(), true, true, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false);
        var coachCreate = new CoachMealRequest(date.plusDays(1), MealType.SNACK, 80, BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO,
            null, null, MealSource.GPT_IMAGE_ESTIMATE, true, List.of(explicitTrue), 5);
        assertTrue(meals.createConfirmed(owner, coachCreate).getDishes().getFirst().isFruit());
        assertTrue(service.findAll(owner).getFirst().fruit());
    }

    @Test void coachCatalogsAreCurrentScopedAndNeverCountAsConsumption() throws Exception {
        var owner = user("coach-catalog-owner");
        var other = user("coach-catalog-other");
        var unknown = new MealDishRequest("Rice", 101, new BigDecimal("1.01"), null, BigDecimal.ZERO, new BigDecimal("100"), DishUnit.GRAM, null, false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false);
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
        var reference = new DishReference(new BigDecimal("100"), 101, new BigDecimal("1.01"), null, BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN);
        var recipe = recipes.create(owner, new RecipeRequest("Rice bowl", new BigDecimal("2.5"), List.of(
            new MealDishRequest("Rice", 0, null, null, null, new BigDecimal("50"), DishUnit.GRAM, reference, false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false),
            new MealDishRequest("Oil", 0, null, null, null, new BigDecimal("50"), DishUnit.GRAM,
                new DishReference(new BigDecimal("100"), 901, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"), java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN), false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false))));
        var date = LocalDate.of(2026, 8, 12);
        var response = context.getHealthContext(owner, date, date, java.util.Set.of(CoachDomain.DISHES));
        var saved = ((com.jllado.weightcontrol.api.dto.CoachDtos.DishesContext) response.data().get(CoachDomain.DISHES)).dishes().getFirst();
        var rice = saved.ingredients().getFirst();
        assertNull(rice.carbohydrateGrams());
        // 1.25 servings of a 2.5-serving recipe: 25 g, 25 kcal, 0.25 g protein.
        // Coach explicitly estimates the missing carbohydrate value before confirmation and resets the reference.
        var proposed = new CoachMealDishRequest(rice.name(), 25, new BigDecimal("0.25"), new BigDecimal("5.50"), BigDecimal.ZERO,
            new BigDecimal("25"), rice.unit(), null, false, null, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false);
        var oil = saved.ingredients().get(1);
        var scaledOil = new CoachMealDishRequest(oil.name(), 225, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("25"),
            new BigDecimal("25"), oil.unit(), oil.reference(), false, null, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false);
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
        assertTrue(nutrition.dailyTotals().getFirst().macrosComplete());
        var stored = meals.findAll(owner).getFirst().getDishes().getFirst();
        assertEquals("Rice", stored.getName());
        assertEquals(new BigDecimal("25.000"), stored.getQuantity());
        assertEquals(new BigDecimal("0.25"), stored.getProteinGrams());
        assertEquals(25, stored.getReferenceCalories());
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
        var scaled = service.update(user, catalog.id(), new MealDishRequest(oats.name(), 206, null, null, null, new BigDecimal("120"), DishUnit.GRAM, corrected.reference(), false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false));
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
        assertThrows(BadRequestException.class, () -> service.update(owner, saved.id(), new MealDishRequest("Invalid", 1, null, null, null, null, null, null, false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false)));
        assertEquals("Rice", service.findAll(owner).getFirst().name());
        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            meals.create(owner, meal(food("Rollback", 1, DishUnit.SERVING, 100)));
            throw new IllegalStateException("Rollback meal and registration together");
        }));
        assertEquals(1, service.findAll(owner).size());
        assertTrue(meals.findAll(owner).isEmpty());
        var unknown = service.create(owner, new MealDishRequest("Unknown macros", 10, null, null, null, BigDecimal.ONE, DishUnit.UNIT, null, false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false));
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
        var uncertain = new CoachMealDishRequest("Possible oats variant", 100, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, DishUnit.SERVING, null, false, null, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false);
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
        return new MealDishRequest(name, calories, new BigDecimal("8"), new BigDecimal("34"), new BigDecimal("4"), BigDecimal.valueOf(quantity), unit, null, false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false);
    }
    private MealRequest meal(MealDishRequest food) { return new MealRequest(LocalDate.of(2026, 8, 12), MealType.SNACK, 0, null, null, null, null, null, List.of(food), null); }
    private CoachMealRequest coach(MealDishRequest food) { return new CoachMealRequest(LocalDate.of(2026, 8, 12), MealType.SNACK, food.calories(), food.proteinGrams(), food.carbohydrateGrams(), food.fatGrams(), null, null, MealSource.GPT_IMAGE_ESTIMATE, true, List.of(new CoachMealDishRequest(food.name(), food.calories(), food.proteinGrams(), food.carbohydrateGrams(), food.fatGrams(), food.quantity(), food.unit(), food.reference(), true, null, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false)), 10); }
}
