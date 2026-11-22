package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.MealDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.MealRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {"app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example", "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class MealDayCopyPersistenceTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean @ServiceConnection
        MariaDBContainer<?> database() { return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("meal_day_copy"); }
    }

    @Autowired MealService meals;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PersonalRecordMutationService mutations;
    @Autowired MealRepository repository;
    @Autowired UserRepository users;
    @Autowired FastingPeriodService fasts;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.jllado.weightcontrol.security.JwtSessionService sessions;
    @Autowired RoutineService routines;
    @Autowired com.jllado.weightcontrol.repository.RoutineRepository routineRepository;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired PersonalRecordService records;
    @Autowired com.jllado.weightcontrol.api.ChatGptCoachActionController coachController;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private static final LocalDate DAY = LocalDate.of(2026, 8, 12);

    @Test void storesIndependentExactSnapshotsWithoutScalingSummingOrFillingUnknownEvidence() throws Exception {
        User owner = user("exact");
        var original = meals.copyDay(owner, request(DAY.minusDays(1), List.of(snapshot(MealType.LUNCH, 1)))).getFirst();
        jdbc.update("update meals set rating = 9, rating_explanation = null where id = ?", original.getId());
        var source = MealResponse.from(meals.findForUser(owner, original.getId()));
        var edited = snapshot(MealType.LUNCH, 1);
        var result = mutations.copyMealDay(owner, request(DAY, List.of(edited, totals(MealType.SNACK, 2), totals(MealType.SNACK, 5))));
        assertEquals(3, result.result().size());
        Meal copied = meals.findForUser(owner, result.result().getFirst().getId());
        assertNotEquals(original.getId(), copied.getId());
        assertNotEquals(original.getDishes().getFirst().getId(), copied.getDishes().getFirst().getId());
        assertEquals(MealSource.MANUAL, copied.getSource());
        assertNotNull(copied.getRating());
        assertNotNull(copied.getRatingExplanation());
        assertEquals(777, copied.getCalories());
        assertEquals(new BigDecimal("12.34"), copied.getProteinGrams());
        var dish = copied.getDishes().getFirst();
        assertEquals(43, dish.getCalories());
        assertEquals(new BigDecimal("0.77"), dish.getProteinGrams());
        assertEquals(new BigDecimal("33.333"), dish.getQuantity());
        assertEquals(new BigDecimal("100.000"), dish.getReferenceQuantity());
        assertEquals(101, dish.getReferenceCalories());
        assertEquals(new BigDecimal("1.01"), dish.getReferenceProteinGrams());
        assertTrue(dish.isFruit());
        var readback = mapper.valueToTree(MealDishResponse.from(dish));
        var expected = mapper.valueToTree(edited.dishes().getFirst());
        ((com.fasterxml.jackson.databind.node.ObjectNode) readback).remove(List.of("id", "position"));
        assertEquals(expected, readback);
        assertNull(dish.getVitaminDMicrograms());
        assertEquals(new BigDecimal("0.00"), dish.getOmega3Milligrams());
        assertNull(dish.getNutrientsEstimated());
        assertNull(dish.getEpaDhaSource());
        assertEquals(Boolean.FALSE, dish.getAlaEstimated());
        assertEquals(List.of(2, 5), result.result().stream().filter(m -> m.getMealType() == MealType.SNACK).map(Meal::getMealSequence).toList());
        assertNull(result.result().get(1).getMealTime());
        assertTrue(result.result().get(1).getDishes().isEmpty());
        assertEquals(source, MealResponse.from(meals.findForUser(owner, original.getId())));
        var other = user("other");
        assertThrows(NotFoundException.class, () -> meals.findForUser(other, copied.getId()));
        assertEquals(1, meals.copyDay(other, request(DAY, List.of(totals(MealType.LUNCH, 1)))).size());
    }

    @Test void unitemizedSaturatedFatEstimateKeepsItsSourceWithoutCountingAsFoodAndExpiresWhenEdited() {
        var owner = user("estimated-unitemized");
        var snapshot = new MealSnapshotRequest(MealType.DINNER, 1, 420, null, null, new BigDecimal("20"),
            null, null, "Unitemized supper", List.of(), new BigDecimal("6.50"), "Estimated from saved supper description");
        var saved = meals.copyDay(owner, request(DAY, List.of(snapshot))).getFirst();
        var stored = MealResponse.from(meals.findForUser(owner, saved.getId()));
        assertTrue(stored.dishes().isEmpty());
        assertEquals("Estimated from saved supper description", stored.saturatedFatEstimateSource());
        assertEquals(new BigDecimal("6.50"), stored.nutrients().saturatedFatGrams());
        assertEquals(0, stored.nutrients().saturatedFatCoverage().foodsWithValues());
        assertEquals(1, stored.nutrients().mealsWithoutFoods());
        assertEquals(MealType.DINNER, stored.nutrients().unitemizedSaturatedFatEstimates().getFirst().mealType());

        var timed = new MealRequest(DAY, MealType.DINNER, 420, null, null, new BigDecimal("20"),
            LocalTime.NOON, snapshot.notes(), List.of(), 30, 4);
        assertEquals(new BigDecimal("6.50"), meals.update(owner, saved.getId(), timed).getEstimatedSaturatedFatGrams());
        var previous = new MealRequest(DAY, MealType.DINNER, 500, null, null, new BigDecimal("22"),
            LocalTime.NOON, snapshot.notes(), List.of(), 30, 3);
        assertThrows(BadRequestException.class, () -> meals.update(owner, saved.getId(), previous));
        var revised = new MealRequest(DAY, MealType.DINNER, 500, null, null, new BigDecimal("22"),
            LocalTime.NOON, snapshot.notes(), List.of(), 30, 4);
        var result = MealResponse.from(meals.update(owner, saved.getId(), revised));
        assertNull(result.estimatedSaturatedFatGrams());
        assertNull(result.saturatedFatEstimateSource());
        assertNull(result.nutrients().saturatedFatGrams());
        assertTrue(result.nutrients().unitemizedSaturatedFatEstimates().isEmpty());
    }

    @Test void ordinaryMealPreloadAcceptsTheCopiedUnitemizedEstimateWithinItsOriginalTotalFat() throws Exception {
        var owner = user("preloaded-estimate");
        var cookie = new jakarta.servlet.http.Cookie("wc_session", sessions.createToken(
            new com.jllado.weightcontrol.security.AuthenticatedUser(owner.getId(), owner.getEmail())));
        var body = mapper.createObjectNode().put("date", DAY.toString()).put("mealType", "SNACK")
            .put("calories", 42).put("fatGrams", 0).put("estimatedSaturatedFatGrams", 0.4)
            .put("saturatedFatEstimateSource", "Comparable snack portion").put("nutrientSchemaVersion", 4);
        var post = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/meals").cookie(cookie).contentType("application/json");
        mvc.perform(post.content(body.toString())).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        body.put("fatGrams", 1).put("nutrientSchemaVersion", 3);
        mvc.perform(post.content(body.toString())).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        body.put("nutrientSchemaVersion", 4);
        mvc.perform(post.content(body.toString())).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        var stored = MealResponse.from(meals.findAll(owner).getFirst());
        assertEquals(new BigDecimal("0.40"), stored.nutrients().saturatedFatGrams());
        assertEquals("Comparable snack portion", stored.saturatedFatEstimateSource());
    }

    @Test void copiedDayTimingSeesEarlierMealsEvenWhenInsertedLaterAndSkipsUntimedMeals() {
        User owner = user("copy-rating-order");
        var batch = meals.copyDay(owner, request(DAY, List.of(
            timed(MealType.DINNER, LocalTime.of(20, 0), 15), totals(MealType.SNACK, 1),
            timed(MealType.BREAKFAST, LocalTime.of(8, 0), 15), totals(MealType.SNACK, 2))));
        var explanations = batch.stream().map(meal -> repository.findById(meal.getId()).orElseThrow().getRatingExplanation()).toList();
        assertTrue(explanations.get(0).contains("Previous meal end to start 705 min"));
        assertTrue(explanations.get(2).contains("Previous meal gap unavailable"));
        assertTrue(explanations.get(1).contains("Timing unavailable"));
        assertTrue(explanations.get(3).contains("Timing unavailable"));
    }

    @Test void mealPreferencesRequireAuthenticationAndRejectFractionalOrMissingTargets() throws Exception {
        var owner = user("preferences-http");
        var cookie = new jakarta.servlet.http.Cookie("wc_session", sessions.createToken(
            new com.jllado.weightcontrol.security.AuthenticatedUser(owner.getId(), owner.getEmail())));
        var get = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/meals/rating-preferences");
        mvc.perform(get).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(get.cookie(cookie)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.mainMealLimitPercent").value(65));
        var prefs = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(com.jllado.weightcontrol.api.dto.MealRatingDtos.Preferences.defaults());
        prefs.put("mainMealLimitPercent", 50);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/meals/rating-preferences")
            .cookie(cookie).contentType("application/json").content(mapper.writeValueAsString(prefs)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        prefs.put("minimumMealGapMinutes", 0.5);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/meals/rating-preferences")
            .cookie(cookie).contentType("application/json").content(mapper.writeValueAsString(prefs)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        prefs.remove("minimumMealGapMinutes");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/meals/rating-preferences")
            .cookie(cookie).contentType("application/json").content(mapper.writeValueAsString(prefs)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(get).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.mainMealLimitPercent").value(50));
    }

    @Test void httpBoundaryAuthenticatesValidatesAndReturnsTheWholeSavedDay() throws Exception {
        User owner = user("http");
        var body = mapper.valueToTree(request(DAY, List.of(totals(MealType.LUNCH, 1), totals(MealType.SNACK, 3))));
        var cookie = new jakarta.servlet.http.Cookie("wc_session", sessions.createToken(new com.jllado.weightcontrol.security.AuthenticatedUser(owner.getId(), owner.getEmail())));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/meals/copy-day")
            .contentType("application/json").content(mapper.writeValueAsString(body)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        var invalid = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(request(DAY, List.of(totals(MealType.LUNCH, 1), totals(MealType.SNACK, 3))));
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.withArray("meals").get(1)).put("calories", -1);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/meals/copy-day").cookie(cookie)
            .contentType("application/json").content(mapper.writeValueAsString(invalid)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        assertEquals(0, repository.countByUser(owner));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/meals/copy-day").cookie(cookie)
            .contentType("application/json").content(mapper.writeValueAsString(body)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result.length()").value(2))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result[1].mealSequence").value(3))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result[0].source").value("MANUAL"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.recordAchievements").isArray());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/meals/copy-day").cookie(cookie)
            .contentType("application/json").content(mapper.writeValueAsString(body)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
            .andExpect(result -> assertEquals("Target date must be empty before copying meals", result.getResolvedException().getMessage()));
        assertEquals(2, repository.countByUser(owner));
    }

    @Test void httpRejectsTimedSnapshotsWithoutDurationBeforeWritingAnyMeals() throws Exception {
        User owner = user("http-missing-duration");
        var invalid = request(DAY, List.of(totals(MealType.LUNCH, 1), timed(MealType.DINNER, LocalTime.NOON, null)));
        var cookie = new jakarta.servlet.http.Cookie("wc_session", sessions.createToken(new com.jllado.weightcontrol.security.AuthenticatedUser(owner.getId(), owner.getEmail())));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/meals/copy-day").cookie(cookie)
            .contentType("application/json").content(mapper.writeValueAsString(invalid)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        assertEquals(0, repository.countByUser(owner));
        var legacy = request(DAY, List.of(timed(MealType.DINNER, null, null)));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/meals/copy-day").cookie(cookie)
            .contentType("application/json").content(mapper.writeValueAsString(legacy)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result[0].mealTime").isEmpty())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result[0].durationMinutes").isEmpty());
        assertEquals(1, repository.countByUser(owner));
    }

    @Test void publishesDailyPersonalRecordsOnlyForTheFinalBatchTotals() {
        User owner = user("records");
        records.replaceSettings(owner, new com.jllado.weightcontrol.api.dto.PersonalRecordDtos.SettingsRequest(List.of(
            new com.jllado.weightcontrol.api.dto.PersonalRecordDtos.SettingOverrideRequest(PersonalRecordCatalogMetric.DAILY_CALORIES, PersonalRecordMode.BOTH))));
        var result = mutations.copyMealDay(owner, request(DAY, List.of(totals(MealType.LUNCH, 1), totals(MealType.SNACK, 1))));
        assertEquals(2, result.achievements().size());
        assertTrue(result.achievements().stream().allMatch(a -> a.value().compareTo(new BigDecimal("176")) == 0));
        assertEquals(2, jdbc.queryForObject("select count(*) from personal_record_events where user_id = ? and source_type = 'NUTRITION_DAY' and record_value = 176", Integer.class, owner.getId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from personal_record_events where user_id = ? and source_type = 'NUTRITION_DAY' and record_value = 88", Integer.class, owner.getId()));
        assertEquals(2, jdbc.queryForObject("select count(*) from in_app_notifications where user_id = ? and type = 'PERSONAL_RECORD'", Integer.class, owner.getId()));
        assertThrows(BadRequestException.class, () -> mutations.copyMealDay(owner, request(DAY, List.of(totals(MealType.LUNCH, 1)))));
        assertEquals(2, jdbc.queryForObject("select count(*) from in_app_notifications where user_id = ? and type = 'PERSONAL_RECORD'", Integer.class, owner.getId()));
    }

    @Test void reconcilesFruitRoutineFromEveryCopiedMealAndPreservesManualCompletion() throws Exception {
        User owner = user("routine");
        var routine = routines.create(owner, new com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineRequest("Fruit", new java.util.LinkedHashSet<>(java.util.Set.of(RoutineType.MIND)), List.of(LocalTime.of(8, 0)), true));
        routine.setStartDate(com.jllado.weightcontrol.util.DateTimes.startOfDay(DAY));
        routine.setAutomaticTrigger(RoutineAutomaticTrigger.FRUIT_MEAL);
        routine.setAutomaticCompletionEnabled(true);
        routineRepository.saveAndFlush(routine);
        routines.checkin(owner, routine.getId(), com.jllado.weightcontrol.util.DateTimes.startOfDay(DAY));
        var result = mutations.copyMealDay(owner, request(DAY, List.of(snapshot(MealType.LUNCH, 1), snapshot(MealType.SNACK, 4))));
        assertEquals(2, jdbc.queryForObject("select count(*) from routine_automatic_evidence where routine_id = ?", Integer.class, routine.getId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from routine_checkins where routine_id = ?", Integer.class, routine.getId()));
        assertTrue(jdbc.queryForObject("select manual_completion from routine_checkins where routine_id = ?", Boolean.class, routine.getId()));
        meals.delete(owner, result.result().getFirst().getId());
        assertEquals(1, jdbc.queryForObject("select count(*) from routine_automatic_evidence where routine_id = ?", Integer.class, routine.getId()));
        meals.delete(owner, result.result().getLast().getId());
        assertEquals(1, jdbc.queryForObject("select count(*) from routine_checkins where routine_id = ?", Integer.class, routine.getId()));
    }

    @Test void rejectsNonemptyFutureAndDuplicateTargetsWithoutPartialWrites() {
        User owner = user("invalid");
        assertThrows(BadRequestException.class, () -> meals.copyDay(owner, request(DAY, List.of(totals(MealType.LUNCH, 1), totals(MealType.LUNCH, 1)))));
        assertThrows(BadRequestException.class, () -> meals.copyDay(owner, request(DAY, List.of(totals(MealType.SNACK, 2), totals(MealType.SNACK, 2)))));
        assertThrows(BadRequestException.class, () -> meals.copyDay(owner, request(LocalDate.now(com.jllado.weightcontrol.util.DateTimes.USER_ZONE).plusDays(1), List.of(totals(MealType.LUNCH, 1)))));
        assertEquals(0, repository.countByUser(owner));
        var request = request(DAY, List.of(totals(MealType.LUNCH, 1)));
        meals.copyDay(owner, request);
        assertThrows(BadRequestException.class, () -> mutations.copyMealDay(owner, request));
        assertEquals(1, repository.countByUser(owner));
    }

    @Test void calculatesFastsFromTheCompleteBatchUsingSavedTimesAndDurations() {
        User owner = user("fasts");
        meals.create(owner, new MealRequest(DAY.minusDays(1), MealType.DINNER, 500, null, null, null, LocalTime.of(20, 0), null, 30));
        meals.copyDay(owner, request(DAY, List.of(timed(MealType.BREAKFAST, LocalTime.of(8, 0), 45), timed(MealType.DINNER, LocalTime.of(20, 0), 60))));
        var completed = fasts.findAll(owner).stream().filter(f -> f.getEndTime() != null).toList();
        assertEquals(2, completed.size());
        assertTrue(completed.stream().anyMatch(f -> java.time.Duration.between(f.getStartTime(), f.getEndTime()).toMinutes() == 690));
        assertTrue(completed.stream().anyMatch(f -> java.time.Duration.between(f.getStartTime(), f.getEndTime()).toMinutes() == 675));
    }

    @Test void serializesCompetingCopiesUnderTheUserLock() throws Exception {
        User owner = user("concurrent");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Boolean> save = () -> { start.await(); try { meals.copyDay(owner, request(DAY, List.of(totals(MealType.LUNCH, 1)))); return true; } catch (BadRequestException expected) { return false; } };
            var first = pool.submit(save);
            var second = pool.submit(save);
            start.countDown();
            assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, repository.countByUser(owner));
        }
    }

    @Test void coachControllerRebuildsFromCommittedCopiedMealsAfterWaitingForTheirTransaction() throws Exception {
        User owner = user("coach-controller-concurrent");
        records.replaceSettings(owner, new com.jllado.weightcontrol.api.dto.PersonalRecordDtos.SettingsRequest(List.of(
            new com.jllado.weightcontrol.api.dto.PersonalRecordDtos.SettingOverrideRequest(PersonalRecordCatalogMetric.DAILY_CALORIES, PersonalRecordMode.MAXIMUM))));
        var routine = routines.create(owner, new com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineRequest("Copied fruit", new java.util.LinkedHashSet<>(java.util.Set.of(RoutineType.MIND)), List.of(LocalTime.of(8, 0)), true));
        routine.setStartDate(com.jllado.weightcontrol.util.DateTimes.startOfDay(DAY));
        routine.setAutomaticTrigger(RoutineAutomaticTrigger.FRUIT_MEAL);
        routine.setAutomaticCompletionEnabled(true);
        routineRepository.saveAndFlush(routine);
        var snapshot = snapshot(MealType.BREAKFAST, 1);
        var breakfast = new MealSnapshotRequest(snapshot.mealType(), snapshot.mealSequence(), snapshot.calories(), snapshot.proteinGrams(),
            snapshot.carbohydrateGrams(), snapshot.fatGrams(), LocalTime.of(6, 0), 30, snapshot.notes(), snapshot.dishes());
        var coachRequest = completeCoachLunch();
        var enteredMutation = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            enteredMutation.countDown();
            return invocation.callRealMethod();
        }).when(mutations).createConfirmedMeal(org.mockito.ArgumentMatchers.any(User.class), org.mockito.ArgumentMatchers.eq(coachRequest));
        var pending = new java.util.concurrent.atomic.AtomicReference<Future<MealResponse>>();
        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                mutations.copyMealDay(owner, request(DAY, List.of(breakfast, timed(MealType.DINNER, LocalTime.of(22, 0), 30))));
                pending.set(pool.submit(() -> {
                    var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        new com.jllado.weightcontrol.security.AuthenticatedUser(owner.getId(), owner.getEmail()), null, List.of()));
                    org.springframework.security.core.context.SecurityContextHolder.setContext(context);
                    try { return coachController.createMeal(coachRequest); }
                    finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
                }));
                try {
                    assertTrue(enteredMutation.await(5, TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> pending.get().get(200, TimeUnit.MILLISECONDS));
                } catch (InterruptedException exception) { throw new RuntimeException(exception); }
            });
            assertEquals(MealType.LUNCH, pending.get().get(15, TimeUnit.SECONDS).mealType());
        }
        assertEquals(3, repository.countByUser(owner));
        var completed = fasts.findAll(owner).stream().filter(f -> f.getEndTime() != null).toList();
        assertEquals(1, completed.size());
        assertEquals(570, java.time.Duration.between(completed.getFirst().getStartTime(), completed.getFirst().getEndTime()).toMinutes());
        assertEquals(1, jdbc.queryForObject("select count(*) from routine_automatic_evidence where routine_id = ?", Integer.class, routine.getId()));
        assertEquals(new BigDecimal("965.00"), jdbc.queryForObject("select value from personal_record_snapshots where user_id = ? and metric = 'DAILY_CALORIES_MAXIMUM'", BigDecimal.class, owner.getId()));
    }

    private CoachMealRequest completeCoachLunch() throws Exception {
        return mapper.readValue("""
            {"date":"2026-08-12","mealType":"LUNCH","calories":100,"proteinGrams":0,"carbohydrateGrams":0,"fatGrams":0,
             "mealTime":"12:00:00","durationMinutes":30,"source":"MANUAL","confirmed":true,"nutrientSchemaVersion":3,
             "dishes":[{"name":"Coach food","calories":100,"proteinGrams":0,"carbohydrateGrams":0,"fatGrams":0,
              "quantity":100,"unit":"GRAM","fruit":false,"addToCatalog":false,
              "vitaminDMicrograms":0,"omega3Milligrams":0,"magnesiumMilligrams":0,"nutrientSource":"Test evidence","nutrientsEstimated":false,
              "epaDhaMilligrams":0,"epaDhaSource":"Test evidence","epaDhaEstimated":false,"alaMilligrams":0,"alaSource":"Test evidence","alaEstimated":false,
              "fiberGrams":0,"fiberSource":"Test evidence","fiberEstimated":false,"saltGrams":0,"saltSource":"Test evidence","saltEstimated":false,
              "saturatedFatGrams":0,"saturatedFatSource":"Test evidence","saturatedFatEstimated":false,
              "reference":{"quantity":100,"calories":100,"proteinGrams":0,"carbohydrateGrams":0,"fatGrams":0,"vitaminDMicrograms":0,
               "omega3Milligrams":0,"magnesiumMilligrams":0,"epaDhaMilligrams":0,"alaMilligrams":0,"fiberGrams":0,"saltGrams":0,"saturatedFatGrams":0}}]}
            """, CoachMealRequest.class);
    }

    @Test void ordinaryAndCoachMutationsWaitForTheBatchTransaction() throws Exception {
        User owner = user("locked");
        Meal existing = meals.create(owner, new MealRequest(DAY.minusDays(1), MealType.LUNCH, 500, null, null, null, null, null, null));
        var coach = new CoachMealRequest(DAY.minusDays(1), MealType.SNACK, 100, null, null, null, LocalTime.NOON, null, MealSource.MANUAL, true, List.of(), 30);
        List<Runnable> writes = List.of(
            () -> meals.create(owner, new MealRequest(DAY.minusDays(1), MealType.SNACK, 100, null, null, null, null, null, null)),
            () -> meals.update(owner, existing.getId(), new MealRequest(DAY.minusDays(1), MealType.LUNCH, 501, null, null, null, null, null, null)),
            () -> meals.createConfirmed(owner, coach),
            () -> meals.updateConfirmed(owner, existing.getId(), new CoachMealRequest(DAY.minusDays(1), MealType.LUNCH, 502, null, null, null, LocalTime.NOON, null, MealSource.MANUAL, true, List.of(), 30)),
            () -> meals.deleteConfirmed(owner, existing.getId(), true));
        try (var pool = Executors.newSingleThreadExecutor()) {
            for (Runnable write : writes) {
                var entered = new CountDownLatch(1);
                Future<?>[] pending = new Future<?>[1];
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    users.findByIdForUpdate(owner.getId());
                    pending[0] = pool.submit(() -> { entered.countDown(); write.run(); });
                    try {
                        assertTrue(entered.await(5, TimeUnit.SECONDS));
                        assertThrows(TimeoutException.class, () -> pending[0].get(200, TimeUnit.MILLISECONDS));
                    } catch (InterruptedException e) { throw new RuntimeException(e); }
                });
                pending[0].get(15, TimeUnit.SECONDS);
            }
        }
    }

    private User user(String suffix) { var user = new User(); user.setEmail("copy-day-" + suffix + "@example.com"); return users.save(user); }
    private CopyDayRequest request(LocalDate date, List<MealSnapshotRequest> snapshots) { return new CopyDayRequest(date, snapshots); }
    private MealSnapshotRequest totals(MealType type, int sequence) { return new MealSnapshotRequest(type, sequence, 88, null, BigDecimal.ZERO, null, null, 15, "unchanged notes", List.of()); }
    private MealSnapshotRequest timed(MealType type, LocalTime time, Integer duration) { return new MealSnapshotRequest(type, 1, 88, null, null, null, time, duration, null, List.of()); }
    private MealSnapshotRequest snapshot(MealType type, int sequence) throws Exception {
        return mapper.readValue("""
            {"mealType":"%s","mealSequence":%d,"calories":777,"proteinGrams":12.34,"carbohydrateGrams":null,"fatGrams":0,
             "mealTime":"12:34:56","durationMinutes":37,"notes":"edited local draft",
             "dishes":[{"name":"Fruit with legacy evidence","calories":43,"proteinGrams":0.77,"carbohydrateGrams":null,"fatGrams":1.12,
              "quantity":33.333,"unit":"GRAM","fruit":true,"vitaminDMicrograms":null,"omega3Milligrams":0,"magnesiumMilligrams":2.22,
              "nutrientSource":null,"nutrientsEstimated":null,"epaDhaMilligrams":0,"epaDhaSource":null,"epaDhaEstimated":null,
              "alaMilligrams":3.33,"alaSource":"saved ALA source","alaEstimated":false,"fiberGrams":null,"fiberSource":null,"fiberEstimated":null,
              "saltGrams":0.12,"saltSource":"saved salt source","saltEstimated":true,
              "saturatedFatGrams":0.14,"saturatedFatSource":"saved saturated fat label","saturatedFatEstimated":false,
              "reference":{"quantity":100,"calories":101,"proteinGrams":1.01,"carbohydrateGrams":null,"fatGrams":3.33,
               "vitaminDMicrograms":0,"omega3Milligrams":null,"magnesiumMilligrams":7.77,"epaDhaMilligrams":null,"alaMilligrams":9.99,"fiberGrams":null,"saltGrams":0.5,"saturatedFatGrams":0.50}}]}
            """.formatted(type, sequence), MealSnapshotRequest.class);
    }
}
