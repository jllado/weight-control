package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private com.jllado.weightcontrol.repository.MealRatingProfileHistory profiles;

    @Autowired private MealRatingService ratings;
    @Autowired private MealRatingPreferenceService ratingPreferences;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

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
    void appAndCoachPersistIdenticalScoresAndExplanationsAndReplaceEarlierRatingsOnlyWhenEdited() {
        LocalDate date = LocalDate.of(2026, 8, 12);
        var appOwner = ratingOwner("app");
        var coachOwner = ratingOwner("coach");
        var request = coachRequest(date, 1);
        var app = mealService.create(appOwner, request.meal());
        var coach = mealService.createConfirmed(coachOwner, request);
        var expected = mealRepository.findById(app.getId()).orElseThrow();

        assertEquals(8, expected.getRating());
        assertEquals(2, expected.getRatingVersion());
        assertTrue(expected.getRatingExplanation().contains("Protein"));
        assertTrue(expected.getRatingExplanation().contains("Fiber"));
        var stored = mealRepository.findById(coach.getId()).orElseThrow();
        assertEquals(expected.getRating(), stored.getRating());
        assertEquals(expected.getRatingExplanation(), stored.getRatingExplanation());

        jdbc.update("update meals set rating = null, rating_explanation = null where id = ?", app.getId());
        jdbc.update("update meals set rating = 3, rating_explanation = null where id = ?", coach.getId());
        mealService.update(appOwner, app.getId(), request.meal());
        assertEquals(3, mealRepository.findById(coach.getId()).orElseThrow().getRating());
        assertNull(mealRepository.findById(coach.getId()).orElseThrow().getRatingExplanation());
        mealService.updateConfirmed(coachOwner, coach.getId(), coachRequest(date, 10));

        for (var id : List.of(app.getId(), coach.getId())) {
            var edited = mealRepository.findById(id).orElseThrow();
            assertEquals(expected.getRating(), edited.getRating());
            assertEquals(expected.getRatingExplanation(), edited.getRatingExplanation());
        }
    }

    @Test
    void historicalEditUsesOnlyThisMealsPortionAndEarlierTimingWithTheDatedPlan() {
        var user = ratingOwner("cutoff");
        LocalDate wednesday = LocalDate.of(2026, 8, 12);
        LocalDate monday = wednesday.minusDays(2);
        jdbc.update("insert into planning_week_changes (user_id, effective_date) values (?, ?)", user.getId(), monday);
        jdbc.update("insert into weekly_calorie_plans (user_id, week_start, wednesday_calories, revision) values (?, ?, ?, 0)",
            user.getId(), monday.minusDays(2), 500);
        user = userRepository.findById(user.getId()).orElseThrow();
        mealService.create(user, timedSnack(monday.minusDays(1), 9000, 1));
        mealService.create(user, timedSnack(monday, 1600, 1));
        mealService.create(user, timedSnack(wednesday.plusDays(1), 9000, 1));
        var later = mealService.create(user, timedSnack(wednesday, 9000, 20));
        var equalEarlier = mealService.create(user, timedSnack(wednesday, 30, 12));
        var meal = mealService.create(user, new MealRequest(wednesday, MealType.LUNCH, 450, null, null, null, LocalTime.NOON, null, 5));
        mealService.create(user, timedSnack(wednesday, 9000, 12));
        var earlier = mealService.create(user, timedSnack(wednesday, 10, 8));

        var saved = mealService.update(user, meal.getId(), new MealRequest(wednesday, MealType.LUNCH, 400, null, null, null, LocalTime.NOON, null, 5));
        var stored = mealRepository.findById(saved.getId()).orElseThrow();
        assertEquals(5, stored.getRating());
        assertTrue(stored.getRatingExplanation().contains("400 / 500 kcal (80%"));
        assertTrue(stored.getRatingExplanation().contains("Previous meal end to start -5 min"));
        assertTrue(stored.getRatingExplanation().contains("Deduction 1/1"));
        for (var untouched : List.of(later, equalEarlier, earlier)) {
            assertEquals(untouched.getRatingExplanation(), mealRepository.findById(untouched.getId()).orElseThrow().getRatingExplanation());
        }
    }

    private MealRequest timedSnack(LocalDate date, int calories, int hour) {
        return new MealRequest(date, MealType.SNACK, calories, BigDecimal.TEN, null, null, LocalTime.of(hour, 0), null, 5);
    }

    private User ratingOwner(String suffix) {
        var user = new User();
        user.setEmail("meal-rating-" + suffix + "@example.com");
        user.setTypicalCaloriesWednesday(2000);
        user.setMacroProteinTargetGrams(BigDecimal.valueOf(100));
        user.setNutrientFiberTargetGrams(BigDecimal.valueOf(20));
        user.setWeeklyAverageCalorieMaximum(1500);
        user = userRepository.save(user);
        profiles.captureChange(user, LocalDate.of(2026, 8, 1).atStartOfDay(DateTimes.USER_ZONE).toInstant());
        return user;
    }

    @Test
    void laterProfileChangesNeverSupplyEarlierMissingTargetsAndPreserveDatedZero() {
        var owner = ratingOwner("profile-history");
        var unknownDate = LocalDate.of(2026, 7, 29);
        var unknown = mealService.create(owner, timedSnack(unknownDate, 400, 12));
        assertNull(unknown.getRating());
        assertEquals(2, unknown.getRatingVersion());
        assertTrue(unknown.getRatingExplanation().contains("goal unavailable"));

        var zeroDate = LocalDate.of(2026, 8, 5);
        owner.setTypicalCaloriesWednesday(0);
        owner.setWeeklyAverageCalorieMaximum(0);
        owner.setMacroProteinTargetGrams(BigDecimal.TEN);
        profiles.captureChange(owner, zeroDate.atStartOfDay(DateTimes.USER_ZONE).toInstant());
        owner.setTypicalCaloriesWednesday(2500);
        owner.setWeeklyAverageCalorieMaximum(2100);
        owner.setMacroProteinTargetGrams(new BigDecimal("1000"));
        profiles.captureChange(owner, zeroDate.plusDays(1).atStartOfDay(DateTimes.USER_ZONE).toInstant());
        userRepository.save(owner);

        var zero = mealService.create(owner, timedSnack(zeroDate, 400, 12));
        assertNull(zero.getRating());
        assertTrue(zero.getRatingExplanation().contains("Protein: skipped (10 g, goal unavailable"));
        var edited = mealService.update(owner, unknown.getId(), timedSnack(unknownDate, 400, 12));
        assertEquals(unknown.getRatingExplanation(), edited.getRatingExplanation());
    }

    @Test
    void reratingTargetsOnlyLegacyAutomaticScoresUnderTheOwnerLockAndReplaysAsANoop() {
        LocalDate date = LocalDate.of(2026, 8, 12);
        var owner = ratingOwner("rerate");
        var other = ratingOwner("rerate-other");
        var automatic = mealService.create(owner, timedSnack(date, 200, 10));
        var manual = mealService.create(owner, timedSnack(date, 100, 12));
        var unrated = mealService.create(owner, timedSnack(date, 100, 14));
        var otherMeal = mealService.create(other, timedSnack(date, 200, 10));
        var legacy = "Base 5. Protein: legacy (+1). Fiber: unavailable (0). Day through meal: 200 / 2000 kcal (+2). Week from 2026-08-08: legacy (+1).";
        for (var id : List.of(automatic.getId(), otherMeal.getId())) {
            jdbc.update("update meals set rating=9, rating_version=null, rating_explanation=?, updated_at='2026-08-13 03:00:00' where id=?", legacy, id);
        }
        jdbc.update("update meals set rating=4, rating_version=null, rating_explanation=null where id=?", manual.getId());
        jdbc.update("update meals set rating=null, rating_version=null, rating_explanation=null where id=?", unrated.getId());
        var originalTimestamp = mealRepository.findById(automatic.getId()).orElseThrow().getUpdatedAt();
        var alreadyVersioned = mealService.create(owner, timedSnack(date, 100, 17));
        jdbc.update("update meals set rating=7, rating_explanation=? where id=?", legacy, alreadyVersioned.getId());
        var preview = ratings.rerate(owner, false);
        assertEquals(1, preview.eligible());
        assertEquals(1, preview.changed());
        org.junit.jupiter.api.Assertions.assertFalse(preview.applied());
        assertEquals(legacy, mealRepository.findById(automatic.getId()).orElseThrow().getRatingExplanation());
        var applied = ratings.rerate(owner, true);
        assertEquals(1, applied.changed());
        assertTrue(applied.applied());
        var migrated = mealRepository.findById(automatic.getId()).orElseThrow();
        assertEquals(automatic.getRatingExplanation(), migrated.getRatingExplanation());
        assertEquals(originalTimestamp, migrated.getUpdatedAt());
        assertEquals(2, migrated.getRatingVersion());
        assertEquals(legacy, mealRepository.findById(alreadyVersioned.getId()).orElseThrow().getRatingExplanation());
        assertEquals(0, ratings.rerate(owner, true).changed());
        assertEquals(legacy, mealRepository.findById(otherMeal.getId()).orElseThrow().getRatingExplanation());
        assertEquals(4, mealRepository.findById(manual.getId()).orElseThrow().getRating());
        assertNull(mealRepository.findById(manual.getId()).orElseThrow().getRatingVersion());
        assertNull(mealRepository.findById(unrated.getId()).orElseThrow().getRating());
    }

    @Test
    void preferenceRevisionsOnlyApplyToTheirOwnerFromTheirEffectiveDate() {
        var owner = ratingOwner("prefs");
        var other = ratingOwner("prefs-other");
        var today = LocalDate.now(DateTimes.USER_ZONE);
        var defaults = com.jllado.weightcontrol.api.dto.MealRatingDtos.Preferences.defaults();
        var custom = new com.jllado.weightcontrol.api.dto.MealRatingDtos.Preferences(java.util.Set.of(MealType.BREAKFAST), 55, 100, 75, 9);
        assertEquals(defaults, ratingPreferences.forDate(owner, today));
        ratingPreferences.update(owner, custom);
        assertEquals(custom, ratingPreferences.forDate(owner, today));
        assertEquals(defaults, ratingPreferences.forDate(owner, today.minusDays(1)));
        assertEquals(defaults, ratingPreferences.forDate(other, today));
        var none = new com.jllado.weightcontrol.api.dto.MealRatingDtos.Preferences(java.util.Set.of(), 60, 0, 0, 12);
        ratingPreferences.update(owner, none);
        assertEquals(none, ratingPreferences.forDate(owner, today));
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
            changedFood(complete, "fiberGrams", 4), changedFood(complete, "saltGrams", null),
            changedFood(complete, "reference.saltGrams", null), changedFood(complete, "saltSource", ""),
            changedFood(complete, "saltEstimated", null), changedFood(complete, "saltGrams", 0.5),
            changedFood(complete, "saturatedFatGrams", null), changedFood(complete, "reference.saturatedFatGrams", null),
            changedFood(complete, "saturatedFatSource", ""), changedFood(complete, "saturatedFatEstimated", null),
            changedFood(complete, "saturatedFatGrams", 0.1))) {
            org.junit.jupiter.api.Assertions.assertThrows(BadRequestException.class,
                () -> mealService.createConfirmed(owner, sourcedMeal(date, List.of(rejected), "draft", 4)));
        }
        assertEquals(0, mealRepository.findByUserOrderByMealDateDescIdAsc(user).size());

        var saved = mealService.createConfirmed(user, sourcedMeal(date, List.of(complete), "saved", 4));
        var original = com.jllado.weightcontrol.api.dto.MealDtos.MealResponse.from(mealService.findForUser(user, saved.getId()));
        org.junit.jupiter.api.Assertions.assertThrows(BadRequestException.class,
            () -> mealService.updateConfirmed(owner, saved.getId(), sourcedMeal(date, List.of(incomplete), "changed", 4)));
        assertEquals(original, com.jllado.weightcontrol.api.dto.MealDtos.MealResponse.from(mealService.findForUser(user, saved.getId())));
    }

    @Test
    void coachAwareMealReplacementPersistsEstimatedSubtypesAfterRejectingMissingMarker() {
        var user = new User();
        user.setEmail("coach-meal-marker@example.com");
        user = userRepository.save(user);
        var date = LocalDate.now(DateTimes.USER_ZONE).minusDays(1);
        var food = coachFood("Oats", true);
        var saved = mealService.createConfirmed(user, sourcedMeal(date, List.of(food), "before", 4));

        User owner = user;
        org.junit.jupiter.api.Assertions.assertThrows(BadRequestException.class,
            () -> mealService.updateConfirmed(owner, saved.getId(), sourcedMeal(date, List.of(food), "after", null)));
        assertEquals("before", mealService.findForUser(user, saved.getId()).getNotes());

        mealService.updateConfirmed(user, saved.getId(), sourcedMeal(date, List.of(food), "after", 4));
        var readback = mealService.findForUser(user, saved.getId());
        assertEquals("after", readback.getNotes());
        assertEquals(new BigDecimal("1.00"), readback.getDishes().getFirst().getEpaDhaMilligrams());
        assertEquals(new BigDecimal("2.00"), readback.getDishes().getFirst().getAlaMilligrams());
        assertEquals(new BigDecimal("3.00"), readback.getDishes().getFirst().getFiberGrams());
        assertEquals(new BigDecimal("0.25"), readback.getDishes().getFirst().getSaltGrams());
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

        mealService.updateConfirmed(user, original.getId(), sourcedMeal(date, List.of(complete, legacy), "metadata only", 4));
        assertEquals("metadata only", mealService.findForUser(user, original.getId()).getNotes());
        User owner = user;
        org.junit.jupiter.api.Assertions.assertThrows(BadRequestException.class,
            () -> mealService.updateConfirmed(owner, original.getId(), sourcedMeal(date, List.of(legacy, complete), "reordered", 4)));
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
            complete ? new BigDecimal("2.00") : null, new BigDecimal("3.00"), complete ? new BigDecimal("0.25") : null, complete ? BigDecimal.ZERO : null);
        return new CoachMealDishRequest(name, 100, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("100"), com.jllado.weightcontrol.domain.DishUnit.GRAM, reference, false, false,
            BigDecimal.ZERO, complete ? BigDecimal.ZERO : null, BigDecimal.ZERO, "USDA comparable oats composition", true,
            complete ? new BigDecimal("1.00") : null, complete ? "USDA comparable oats EPA+DHA estimate" : null,
            complete ? true : null, complete ? new BigDecimal("2.00") : null,
            complete ? "USDA comparable oats ALA estimate" : null, complete ? true : null,
            new BigDecimal("3.00"), "USDA comparable oats Fiber estimate", true,
            complete ? new BigDecimal("0.25") : null, complete ? "Comparable oats salt estimate" : null, complete ? true : null,
            complete ? BigDecimal.ZERO : null, complete ? "Matching saturated fat evidence" : null, complete ? false : null);
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
            List.of(coachFood("Rated dish", true)), 30, rating, 4);
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
