package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jllado.weightcontrol.api.dto.MealDtos.CoachMealDishRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.CoachMealRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.MealDishRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.MealRequest;
import com.jllado.weightcontrol.domain.MealSource;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MealRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {
    "app.auth.google-client-id=test-client-id",
    "app.chat-gpt-actions.public-base-url=https://test.example",
    "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"
})
@ExtendWith(OutputCaptureExtension.class)
class MealServicePersistenceTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean
        @ServiceConnection
        MariaDBContainer<?> database() {
            return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("weight_control_meal_service");
        }
    }

    @Autowired
    private MealService mealService;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FastingPeriodService fastingPeriodService;

    @Autowired
    private PersonalRecordMutationService personalRecordMutationService;

    @Autowired
    private GptActionNotificationService actionNotifications;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void loadsHistoricalDishesInBatches() {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User user = new User();
            user.setEmail("meal-batch@example.com");
            user = userRepository.save(user);
            for (int index = 0; index < 51; index++) {
                mealService.create(user, request(LocalDate.of(2026, 1, 1).plusDays(index), "Dish " + index));
            }
            entityManager.flush();
            entityManager.clear();
            var statistics = entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
            statistics.setStatisticsEnabled(true);
            statistics.clear();
            try {
                var meals = mealRepository.findByUserOrderByMealDateDescIdAsc(user);
                assertEquals(51, meals.size());
                assertEquals(51, meals.stream().mapToInt(meal -> meal.getDishes().size()).sum());
                org.junit.jupiter.api.Assertions.assertTrue(statistics.getPrepareStatementCount() <= 4,
                    "History should load meals and dish batches without a query per meal");
            } finally {
                statistics.setStatisticsEnabled(false);
            }
            status.setRollbackOnly();
        });
    }

    @Test
    void replacesDishesAtExistingPositions() {
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        User user = new User();
        user.setEmail("meal-service@example.com");
        user = userRepository.save(user);

        var meal = mealService.create(user, request(date, "Old dish"));
        mealService.update(user, meal.getId(), request(date, "Replacement dish"));

        var stored = mealRepository.findById(meal.getId()).orElseThrow();
        assertEquals(30, stored.getDurationMinutes());
        assertEquals("Replacement dish", stored.getDishes().getFirst().getName());
        assertEquals(1, stored.getDishes().getFirst().getPosition());
    }

    @Test
    void persistsMealRatingIndependentlyFromMealEdits() {
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        User user = new User();
        user.setEmail("meal-rating@example.com");
        user = userRepository.save(user);
        var meal = mealService.create(user, request(date, "Rated dish"));

        mealService.rateConfirmed(user, meal.getId(), new com.jllado.weightcontrol.api.dto.MealDtos.CoachMealRatingRequest(8, true));
        var rated = mealRepository.findById(meal.getId()).orElseThrow();
        assertEquals(8, rated.getRating());
        assertEquals(500, rated.getCalories());
        assertEquals(30, rated.getDurationMinutes());
        assertEquals("Rated dish", rated.getDishes().getFirst().getName());
        mealService.update(user, meal.getId(), request(date, "Edited rated dish"));

        assertEquals(8, mealRepository.findById(meal.getId()).orElseThrow().getRating());
    }

    @Test
    void coachMealCreateSavesItsRatingAndUpdatesPreserveOrReplaceIt() {
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE).minusDays(2);
        User user = new User();
        user.setEmail("coach-meal-rating@example.com");
        user = userRepository.save(user);

        var lowest = mealService.createConfirmed(user, coachRequest(date, 1));
        var highest = mealService.createConfirmed(user, coachRequest(date.plusDays(1), 10));

        assertEquals(1, mealRepository.findById(lowest.getId()).orElseThrow().getRating());
        assertEquals(10, mealRepository.findById(highest.getId()).orElseThrow().getRating());

        mealService.updateConfirmed(user, lowest.getId(), coachRequest(date, null));
        assertEquals(1, mealRepository.findById(lowest.getId()).orElseThrow().getRating());

        mealService.updateConfirmed(user, lowest.getId(), coachRequest(date, 10));
        assertEquals(10, mealRepository.findById(lowest.getId()).orElseThrow().getRating());
    }

    @Test
    void coachMealRequiresCompleteEstimatedNutrientsBeforeCreatingOrReplacingFoods() {
        var user = new User();
        user.setEmail("coach-complete-food@example.com");
        user = userRepository.save(user);
        var date = LocalDate.now(DateTimes.USER_ZONE).minusDays(1);
        var complete = coachFood("Oats", true);
        var incomplete = coachFood("Oats", false);

        User owner = user;
        for (var rejected : List.of(incomplete, changedFood(complete, "proteinGrams", null),
            changedFood(complete, "carbohydrateGrams", null), changedFood(complete, "fatGrams", null),
            changedFood(complete, "fiberGrams", null),
            changedFood(complete, "reference.fiberGrams", null), changedFood(complete, "fiberSource", ""),
            changedFood(complete, "epaDhaSource", null), changedFood(complete, "alaMilligrams", null),
            changedFood(complete, "fiberGrams", 4))) {
            org.junit.jupiter.api.Assertions.assertThrows(BadRequestException.class,
                () -> mealService.createConfirmed(owner, sourcedMeal(date, List.of(rejected), "draft", 2)));
        }
        assertEquals(0, mealRepository.findByUserOrderByMealDateDescIdAsc(user).size());

        var saved = mealService.createConfirmed(user, sourcedMeal(date, List.of(complete), "saved", 2));
        var original = com.jllado.weightcontrol.api.dto.MealDtos.MealResponse.from(mealService.findForUser(user, saved.getId()));
        org.junit.jupiter.api.Assertions.assertThrows(BadRequestException.class,
            () -> mealService.updateConfirmed(owner, saved.getId(), sourcedMeal(date, List.of(incomplete), "changed", 2)));
        assertEquals(original, com.jllado.weightcontrol.api.dto.MealDtos.MealResponse.from(mealService.findForUser(user, saved.getId())));
    }

    @Test
    void coachAwareMealReplacementPersistsEstimatedSubtypesAfterRejectingMissingMarker() {
        var user = new User();
        user.setEmail("coach-meal-marker@example.com");
        user = userRepository.save(user);
        var date = LocalDate.now(DateTimes.USER_ZONE).minusDays(1);
        var food = coachFood("Oats", true);
        var saved = mealService.createConfirmed(user, sourcedMeal(date, List.of(food), "before", 2));

        User owner = user;
        org.junit.jupiter.api.Assertions.assertThrows(BadRequestException.class,
            () -> mealService.updateConfirmed(owner, saved.getId(), sourcedMeal(date, List.of(food), "after", null)));
        assertEquals("before", mealService.findForUser(user, saved.getId()).getNotes());

        mealService.updateConfirmed(user, saved.getId(), sourcedMeal(date, List.of(food), "after", 2));
        var readback = mealService.findForUser(user, saved.getId());
        assertEquals("after", readback.getNotes());
        assertEquals(new BigDecimal("1.00"), readback.getDishes().getFirst().getEpaDhaMilligrams());
        assertEquals(new BigDecimal("2.00"), readback.getDishes().getFirst().getAlaMilligrams());
        assertEquals(new BigDecimal("3.00"), readback.getDishes().getFirst().getFiberGrams());
    }

    @Test
    void coachMetadataEditKeepsExactOrderedLegacyFoodsButReorderedRepeatedNamesNeedCompleteEvidence() {
        var user = new User();
        user.setEmail("coach-legacy-food@example.com");
        user = userRepository.save(user);
        var date = LocalDate.now(DateTimes.USER_ZONE).minusDays(1);
        var complete = coachFood("Same name", true);
        var legacy = coachFood("Same name", false);
        var original = mealService.create(user, new MealRequest(date, MealType.LUNCH, 200, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, LocalTime.NOON, "before", List.of(complete.meal(), legacy.meal()), 30, 2));

        mealService.updateConfirmed(user, original.getId(), sourcedMeal(date, List.of(complete, legacy), "metadata only", 2));
        assertEquals("metadata only", mealService.findForUser(user, original.getId()).getNotes());
        User owner = user;
        org.junit.jupiter.api.Assertions.assertThrows(BadRequestException.class,
            () -> mealService.updateConfirmed(owner, original.getId(), sourcedMeal(date, List.of(legacy, complete), "reordered", 2)));
        assertEquals("metadata only", mealService.findForUser(user, original.getId()).getNotes());
    }

    private CoachMealRequest sourcedMeal(LocalDate date, List<CoachMealDishRequest> dishes, String notes, Integer marker) {
        return new CoachMealRequest(date, MealType.LUNCH, 100 * dishes.size(), BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, LocalTime.NOON, notes, MealSource.GPT_IMAGE_ESTIMATE, true, dishes, 30, null, marker);
    }

    private CoachMealDishRequest coachFood(String name, boolean complete) {
        var reference = new com.jllado.weightcontrol.api.dto.MealDtos.DishReference(new BigDecimal("100"), 100,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            complete ? BigDecimal.ZERO : null, BigDecimal.ZERO, complete ? new BigDecimal("1.00") : null,
            complete ? new BigDecimal("2.00") : null, new BigDecimal("3.00"));
        return new CoachMealDishRequest(name, 100, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("100"), com.jllado.weightcontrol.domain.DishUnit.GRAM, reference, false, false,
            BigDecimal.ZERO, complete ? BigDecimal.ZERO : null, BigDecimal.ZERO, "USDA comparable oats composition", true,
            complete ? new BigDecimal("1.00") : null, complete ? "USDA comparable oats EPA+DHA estimate" : null,
            complete ? true : null, complete ? new BigDecimal("2.00") : null,
            complete ? "USDA comparable oats ALA estimate" : null, complete ? true : null,
            new BigDecimal("3.00"), "USDA comparable oats Fiber estimate", true);
    }

    private CoachMealDishRequest changedFood(CoachMealDishRequest food, String field, Object value) {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(food);
        var target = node;
        var name = field;
        if (field.startsWith("reference.")) {
            target = (com.fasterxml.jackson.databind.node.ObjectNode) node.get("reference");
            name = field.substring("reference.".length());
        }
        if (value == null) target.putNull(name);
        else if (value instanceof Number number) target.put(name, number.doubleValue());
        else target.put(name, value.toString());
        return mapper.convertValue(node, CoachMealDishRequest.class);
    }

    @Test
    void confirmedCoachMealLogsCorrelatedSynchronousStagesWithoutMealData(CapturedOutput output) {
        User user = new User();
        user.setEmail("coach-meal-timing@example.com");
        user = userRepository.save(user);
        var dish = coachFood("Timing fixture", true);
        var request = sourcedMeal(LocalDate.now(DateTimes.USER_ZONE), List.of(dish), null, 2);
        User savedUser = user;

        var saved = CoachActionTiming.time("createMeal", "request", () -> actionNotifications.execute(savedUser, "Lunch saved", "/calories",
            () -> personalRecordMutationService.createConfirmedMeal(savedUser, request)));

        assertEquals(100, saved.getCalories());
        var requestIdMatcher = Pattern.compile("action=createMeal stage=request request_id=([0-9a-f-]+)").matcher(output.getOut());
        org.junit.jupiter.api.Assertions.assertTrue(requestIdMatcher.find());
        String requestId = requestIdMatcher.group(1);
        for (String stage : List.of("coachWrite stage=mutation", "mealSave stage=mealPersistence", "mealSave stage=fastingRecalculation",
            "mealSave stage=foodCatalogRegistration", "mealSave stage=personalRecordRebuild", "coachWrite stage=notificationRecord",
            "coachWrite stage=eventPublish", "coachWrite stage=pushDelivery")) {
            org.junit.jupiter.api.Assertions.assertTrue(output.getOut().contains("action=" + stage + " request_id=" + requestId), stage);
        }
        org.junit.jupiter.api.Assertions.assertFalse(output.getOut().contains("Timing fixture"));
    }

    @Test
    void mealMutationsRecalculateCompletedFastsFromPersistedDuration() {
        LocalDate date = LocalDate.of(2026, 8, 10);
        User user = new User();
        user.setEmail("meal-duration@example.com");
        user = userRepository.save(user);
        var dinner = mealService.create(user, new MealRequest(date, MealType.DINNER, 500, null, null, null,
            java.time.LocalTime.of(20, 0), null, 30));
        mealService.create(user, new MealRequest(date.plusDays(1), MealType.BREAKFAST, 500, null, null, null,
            java.time.LocalTime.of(8, 0), null, 30));
        var completed = fastingPeriodService.findAll(user).stream().filter(period -> period.getEndTime() != null).findFirst().orElseThrow();
        assertEquals(java.time.Duration.ofMinutes(690), java.time.Duration.between(completed.getStartTime(), completed.getEndTime()));
        mealService.update(user, dinner.getId(), new MealRequest(date, MealType.DINNER, 500, null, null, null,
            java.time.LocalTime.of(20, 0), null, 60));
        completed = fastingPeriodService.findAll(user).stream().filter(period -> period.getEndTime() != null).findFirst().orElseThrow();
        assertEquals(java.time.Duration.ofHours(11), java.time.Duration.between(completed.getStartTime(), completed.getEndTime()));
        mealService.delete(user, dinner.getId());
        assertEquals(0, fastingPeriodService.findAll(user).stream().filter(period -> period.getEndTime() != null).count());
    }

    private MealRequest request(LocalDate date, String dishName) {
        return new MealRequest(date, MealType.LUNCH, 0, null, null, null, null, null, List.of(
            new MealDishRequest(dishName, 500, null, null, null, null, null, null, false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false)
        ), 30);
    }

    private CoachMealRequest coachRequest(LocalDate date, Integer rating) {
        return new CoachMealRequest(date, MealType.LUNCH, 100, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            LocalTime.of(12, 0), null, MealSource.GPT_IMAGE_ESTIMATE, true,
            List.of(coachFood("Rated dish", true)), 30, rating, 2);
    }

    @Test
    void persistsQuantityReferenceAcrossEditsAndIndependentReuse() {
        var user = new User();
        user.setEmail("dish-quantities@example.com");
        user = userRepository.save(user);
        var date = LocalDate.of(2026, 8, 12);
        var reference = new com.jllado.weightcontrol.api.dto.MealDtos.DishReference(new java.math.BigDecimal("100"), 101, new java.math.BigDecimal("1.01"), null, null);
        var dish = new MealDishRequest("Rice", 51, new java.math.BigDecimal("0.51"), null, null, new java.math.BigDecimal("50"), com.jllado.weightcontrol.domain.DishUnit.GRAM, reference, false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false);
        var request = new MealRequest(date, MealType.SNACK, 51, null, null, null, null, null, List.of(dish), null);
        var first = mealService.create(user, request);
        var copy = mealService.create(user, request);
        var stored = mealRepository.findById(first.getId()).orElseThrow().getDishes().getFirst();
        var restored = new MealDishRequest("Rice", 101, new java.math.BigDecimal("1.01"), null, null, new java.math.BigDecimal("100"), stored.getUnit(), com.jllado.weightcontrol.api.dto.MealDtos.DishReference.from(stored), false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false);
        mealService.update(user, first.getId(), new MealRequest(date, MealType.SNACK, 101, null, null, null, null, null, List.of(restored), null));
        assertEquals(101, mealRepository.findById(first.getId()).orElseThrow().getCalories());
        assertEquals(51, mealRepository.findById(copy.getId()).orElseThrow().getCalories());
    }
}
