package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import com.jllado.weightcontrol.api.dto.CoachDtos.*;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.*;
import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.CoachWarningRepository;
import com.jllado.weightcontrol.repository.CoachWarningRevisionRepository;
import com.jllado.weightcontrol.repository.InAppNotificationRepository;
import com.jllado.weightcontrol.repository.MealRepository;
import com.jllado.weightcontrol.repository.SavedWeeklySummaryRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.repository.WeeklyReflectionRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {"app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example", "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"})
class CalorieWarningCreationTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean @ServiceConnection MariaDBContainer<?> database() { return new MariaDBContainer<>("mariadb:11.8"); }
    }

    @Autowired CoachWarningService warnings;
    @Autowired UserRepository users;
    @Autowired MealRepository meals;
    @Autowired WarningMetricEvaluator evaluator;
    @Autowired DashboardReflectionService daily;
    @Autowired DailyStatusSnapshotService snapshots;
    @Autowired WeeklyReflectionService weekly;
    @Autowired SavedWeeklySummaryRepository summaries;
    @Autowired WeeklyReflectionRepository weeklyRecords;
    @Autowired CoachWarningRepository warningRecords;
    @Autowired CoachWarningRevisionRepository revisions;
    @Autowired GptActionNotificationService notifications;
    @Autowired InAppNotificationRepository notificationRecords;
    private final LocalDate today = LocalDate.now(DateTimes.USER_ZONE);
    private final LocalDate end = today.minusDays(1);

    @Test void firstMinorBreachRejectsCreationWithoutChangingExactGoalStatus() {
        var owner = owner(end);
        // The preceding seven days total 17,500; the current seven total 18,190 (2,598.571428... kcal/day).
        record(owner, end.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3190);
        var review = review("2598.5714285714", "2500");
        assertEquals(WarningGoalStatus.NOT_MET, evaluator.evaluate(review, today, null));

        assertThrows(BadRequestException.class, () -> warnings.create(owner, request(CoachWarningType.NUTRITION_IMBALANCE, review)));

        assertTrue(warnings.overview(owner).active().isEmpty());
        assertFalse(warnings.overview(owner).hasHistory());
        assertEquals(new BigDecimal("2598.5714285714"), review.metrics().getFirst().value());
        assertEquals(new BigDecimal("2500"), review.metrics().getFirst().maximum());
        assertEquals(WarningGoalStatus.NOT_MET, evaluator.evaluate(review, today, null));
    }

    @Test void fivePercentBoundaryIsInclusiveAndRawValuesDetermineEligibility() {
        var boundary = owner(end);
        record(boundary, end.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3375);
        assertThrows(BadRequestException.class, () -> warnings.create(boundary,
            request(CoachWarningType.NUTRITION_IMBALANCE, review("2625", "2500"))));

        var above = owner(end);
        record(above, end.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3376);
        // Raw mean is 2,625.142857...; even a supplied value rounded down to 2,625 cannot enable tolerance.
        var created = warnings.create(above, request(CoachWarningType.NUTRITION_IMBALANCE, review("2625", "2500")));
        assertEquals(WarningGoalStatus.NOT_MET, created.progress().goalStatus());
        assertEquals(new BigDecimal("2625"), created.progress().metricsReview().metrics().getFirst().value());
    }

    @Test void toleranceUsesTheMetricGoalRatherThanAConfiguredOrHardcodedMaximum() {
        var owner = owner(end);
        owner.setWeeklyAverageCalorieMaximum(2500);
        users.save(owner);
        record(owner, end.minusDays(7), 2000, 2000, 2000, 2000, 2000, 2000, 2000, 2700);
        assertThrows(BadRequestException.class, () -> warnings.create(owner,
            request(CoachWarningType.NUTRITION_IMBALANCE, review("2100", "2000"))));
    }

    @Test void twoConsecutiveMinorBreachesAreImmediatelyEligible() {
        var owner = owner(end);
        // The previous mean is 2,501 and the current mean is 2,502: both minor, but consecutive.
        record(owner, end.minusDays(7), 2507, 2500, 2500, 2500, 2500, 2500, 2500, 2514);
        var created = warnings.create(owner, request(CoachWarningType.NUTRITION_IMBALANCE, review("2502", "2500")));
        assertEquals(CoachWarningStatus.ACTIVE, created.status());
        assertEquals(WarningGoalStatus.NOT_MET, created.progress().goalStatus());
    }

    @Test void returningToGoalResetsTheBreachDespiteEarlierHighWindows() {
        var owner = owner(end);
        // Earlier rolling totals include 17,600; the immediately preceding total is 17,400, current 17,600.
        record(owner, end.minusDays(9), 2600, 2600, 2400, 2500, 2500, 2500, 2500, 2500, 2500, 2600);
        assertThrows(BadRequestException.class, () -> warnings.create(owner,
            request(CoachWarningType.NUTRITION_IMBALANCE, review("2514.285714", "2500"))));
    }

    @Test void repeatedEvaluationOfTheSameDatesNeverCreatesPersistence() {
        var owner = owner(end);
        record(owner, end.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3190);
        var request = request(CoachWarningType.NUTRITION_IMBALANCE, review("2598.5714285714", "2500"));
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThrows(BadRequestException.class, () -> warnings.create(owner, request));
            assertThrows(BadRequestException.class, () -> warnings.create(owner,
                request(CoachWarningType.NUTRITION_IMBALANCE, request.content().metricsReview())));
        }
        assertTrue(warnings.overview(owner).active().isEmpty());
    }

    @Test void sevenCurrentDaysWithoutThePreviousDateCannotProveAnIsolatedBreach() {
        var owner = owner(end);
        record(owner, end.minusDays(6), 2500, 2500, 2500, 2500, 2500, 2500, 3190);
        assertEquals(CoachWarningStatus.ACTIVE, warnings.create(owner,
            request(CoachWarningType.NUTRITION_IMBALANCE, review("2598.5714285714", "2500"))).status());
    }

    @Test void missingCalendarDayCannotBeReplacedByAnOlderRecordOrSubmittedCoverage() {
        var owner = owner(end);
        record(owner, end.minusDays(8), 2500, 2500, 2500, 2500);
        record(owner, end.minusDays(3), 2500, 2500, 2500, 3190);
        assertEquals(CoachWarningStatus.ACTIVE, warnings.create(owner,
            request(CoachWarningType.NUTRITION_IMBALANCE, review("2598.5714285714", "2500"))).status());
    }

    @Test void uncompletedDatesCannotProveAnIsolatedBreach() {
        var owner = owner(end.minusDays(1));
        record(owner, end.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3190);
        assertEquals(CoachWarningStatus.ACTIVE, warnings.create(owner,
            request(CoachWarningType.NUTRITION_IMBALANCE, review("2598.5714285714", "2500"))).status());
    }

    @Test void todaysCompletedWindowUsesTheSameCalendarRule() {
        var owner = owner(today);
        record(owner, today.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3190);
        var base = review("2598.5714285714", "2500");
        var review = new WarningMetricReview(7, today, true, base.shortStatus(), base.metrics(), null);
        assertThrows(BadRequestException.class, () -> warnings.create(owner, request(CoachWarningType.NUTRITION_IMBALANCE, review)));
    }

    @Test void ownerIsolationPreservesBothIndependentOutcomes() {
        var isolated = owner(end);
        var persistent = owner(end);
        record(isolated, end.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3190);
        record(persistent, end.minusDays(7), 2600, 2600, 2600, 2600, 2600, 2600, 2600, 2600);
        assertThrows(BadRequestException.class, () -> warnings.create(isolated,
            request(CoachWarningType.NUTRITION_IMBALANCE, review("2598.5714285714", "2500"))));
        assertEquals(CoachWarningStatus.ACTIVE, warnings.create(persistent,
            request(CoachWarningType.NUTRITION_IMBALANCE, review("2600", "2500"))).status());
    }

    @Test void anotherOwnersRecordCannotFillAMissingPreviousDate() {
        var owner = owner(end);
        record(owner, end.minusDays(6), 2500, 2500, 2500, 2500, 2500, 2500, 3190);
        record(owner(end), end.minusDays(7), 2500);
        assertEquals(CoachWarningStatus.ACTIVE, warnings.create(owner,
            request(CoachWarningType.NUTRITION_IMBALANCE, review("2598.5714285714", "2500"))).status());
    }

    @ParameterizedTest @ValueSource(strings = {"NUTRITION.calories", "NUTRITION.dailyTotals.calories", "NUTRITION.dailyTotals.calories/completed"})
    void supportedCalorieSourcesAreRecognizedWithoutRelyingOnTheKeyOrLabel(String source) {
        var owner = owner(end);
        record(owner, end.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3190);
        var base = review("2598.5714285714", "2500");
        var metric = base.metrics().getFirst();
        var supported = new WarningMetric("different_key", "Different label", source, metric.aggregation(),
            metric.unit(), metric.value(), metric.minimum(), metric.maximum(), metric.coveredDays(), metric.goalBasis());
        assertThrows(BadRequestException.class, () -> warnings.create(owner,
            request(CoachWarningType.NUTRITION_IMBALANCE, withMetrics(base, List.of(supported)))));
    }

    @Test void independentlyUnmetNutritionMetricRemainsEligible() {
        var owner = owner(end);
        record(owner, end.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3190);
        var base = review("2598.5714285714", "2500");
        var fiber = new WarningMetric("fiber", "Fiber", "NUTRITION.dailyTotals.fiberGrams", WarningMetric.Aggregation.MEAN,
            "g/day", new BigDecimal("10"), new BigDecimal("25"), null, 7, "Recorded fiber minimum");
        var created = warnings.create(owner, request(CoachWarningType.NUTRITION_IMBALANCE,
            withMetrics(base, List.of(base.metrics().getFirst(), fiber))));
        assertEquals(WarningGoalStatus.NOT_MET, created.progress().goalStatus());
    }

    @Test void unrelatedUnknownMetricDoesNotJustifyTheFirstMinorCalorieBreach() {
        var owner = owner(end);
        record(owner, end.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3190);
        var base = review("2598.5714285714", "2500");
        var fiber = new WarningMetric("fiber", "Fiber", "NUTRITION.dailyTotals.fiberGrams", WarningMetric.Aggregation.MEAN,
            "g/day", null, new BigDecimal("25"), null, 6, "Recorded fiber minimum");
        var review = withMetrics(base, List.of(base.metrics().getFirst(), fiber));
        assertEquals(WarningGoalStatus.UNKNOWN, evaluator.evaluate(review, today, null));
        assertThrows(BadRequestException.class, () -> warnings.create(owner, request(CoachWarningType.NUTRITION_IMBALANCE, review)));
        assertEquals(WarningGoalStatus.UNKNOWN, evaluator.evaluate(review, today, null));
    }

    @Test void metIndependentNutritionMetricDoesNotMakeTheFirstCalorieBreachEligible() {
        var owner = owner(end);
        record(owner, end.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3190);
        var base = review("2598.5714285714", "2500");
        var fiber = new WarningMetric("fiber", "Fiber", "NUTRITION.dailyTotals.fiberGrams", WarningMetric.Aggregation.MEAN,
            "g/day", new BigDecimal("25"), new BigDecimal("25"), null, 7, "Recorded fiber minimum");
        assertThrows(BadRequestException.class, () -> warnings.create(owner, request(CoachWarningType.NUTRITION_IMBALANCE,
            withMetrics(base, List.of(base.metrics().getFirst(), fiber)))));
    }

    @Test void unknownCalorieValuesCoverageOrGoalsDoNotEnableTolerance() {
        var base = review("2598.5714285714", "2500");
        var metric = base.metrics().getFirst();
        for (var unknown : List.of(
            new WarningMetric(metric.key(), metric.label(), metric.source(), metric.aggregation(), metric.unit(), null, null, metric.maximum(), 7, metric.goalBasis()),
            new WarningMetric(metric.key(), metric.label(), metric.source(), metric.aggregation(), metric.unit(), metric.value(), null, metric.maximum(), 6, metric.goalBasis()),
            new WarningMetric(metric.key(), metric.label(), metric.source(), metric.aggregation(), metric.unit(), metric.value(), null, null, 7, metric.goalBasis()))) {
            var owner = owner(end);
            record(owner, end.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3190);
            assertEquals(WarningGoalStatus.UNKNOWN, warnings.create(owner,
                request(CoachWarningType.NUTRITION_IMBALANCE, withMetrics(base, List.of(unknown)))).progress().goalStatus());
        }
    }

    @Test void unknownCalorieMetricPreventsProvingIsolationEvenAlongsideAKnownMinorBreach() {
        var owner = owner(end);
        record(owner, end.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3190);
        var base = review("2598.5714285714", "2500");
        var unknown = new WarningMetric("other_calorie_goal", "Other recorded calorie goal", "NUTRITION.dailyTotals.calories",
            WarningMetric.Aggregation.MEAN, "kcal/day", null, null, new BigDecimal("2400"), 6, "Another recorded goal");
        assertEquals(WarningGoalStatus.UNKNOWN, warnings.create(owner, request(CoachWarningType.NUTRITION_IMBALANCE,
            withMetrics(base, List.of(base.metrics().getFirst(), unknown)))).progress().goalStatus());
    }

    @Test void completedZeroCalorieDateStillProvidesCalorieCoverageWithoutMacros() {
        var owner = owner(end);
        record(owner, end.minusDays(7), 0, 2500, 2500, 2500, 2500, 2500, 2500, 3190);
        assertThrows(BadRequestException.class, () -> warnings.create(owner,
            request(CoachWarningType.NUTRITION_IMBALANCE, review("2598.5714285714", "2500"))));
    }

    @Test void otherWarningTypesSourcesAggregationsUnitsAndWindowsRetainCreationBehavior() {
        var base = review("2598.5714285714", "2500");
        var metric = base.metrics().getFirst();
        for (var type : CoachWarningType.values()) {
            if (type == CoachWarningType.NUTRITION_IMBALANCE) continue;
            var owner = owner(end);
            record(owner, end.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3190);
            assertEquals(CoachWarningStatus.ACTIVE, warnings.create(owner, request(type, base)).status());
        }
        for (var other : List.of(
            new WarningMetric("seven_day_calorie_mean", "Calories", "NUTRITION.dailyTotals.proteinGrams", metric.aggregation(), metric.unit(), metric.value(), null, metric.maximum(), 7, metric.goalBasis()),
            new WarningMetric(metric.key(), metric.label(), metric.source(), WarningMetric.Aggregation.SUM, metric.unit(), metric.value(), null, metric.maximum(), 7, metric.goalBasis()),
            new WarningMetric(metric.key(), metric.label(), "NUTRITION.dailyTotals.calories", metric.aggregation(), "kJ/day", metric.value(), null, metric.maximum(), 7, metric.goalBasis()))) {
            var owner = owner(end);
            record(owner, end.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3190);
            assertEquals(CoachWarningStatus.ACTIVE, warnings.create(owner,
                request(CoachWarningType.NUTRITION_IMBALANCE, withMetrics(base, List.of(other)))).status());
        }
        var owner = owner(end);
        record(owner, end.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3190);
        var fourteenDays = new WarningMetricReview(14, end, false, base.shortStatus(), base.metrics(), null);
        assertEquals(WarningGoalStatus.UNKNOWN, warnings.create(owner,
            request(CoachWarningType.NUTRITION_IMBALANCE, fourteenDays)).progress().goalStatus());
    }

    @Test void existingWarningUpdatesAndExactRetriesRemainUnaffectedWhenEvidenceBecomesIsolated() {
        var owner = owner(end);
        record(owner, end.minusDays(7), 2600, 2600, 2600, 2600, 2600, 2600, 2600, 2600);
        var original = request(CoachWarningType.NUTRITION_IMBALANCE, review("2600", "2500"));
        var created = warnings.create(owner, original);
        var rows = meals.findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(owner, end.minusDays(7), end);
        for (int index = 0; index < rows.size(); index++) rows.get(index).setCalories(index == 7 ? 3190 : 2500);
        meals.saveAllAndFlush(rows);

        assertEquals(created.id(), warnings.create(owner, original).id());
        assertEquals(1, warnings.revisions(owner, created.id(), 0).items().size());
        var updated = warnings.update(owner, created.id(), new UpdateWarningRequest(created.version(),
            request(CoachWarningType.NUTRITION_IMBALANCE, review("2598.5714285714", "2500")).content()));
        assertEquals(CoachWarningStatus.ACTIVE, updated.status());
        assertEquals(WarningGoalStatus.NOT_MET, updated.progress().goalStatus());
        assertEquals(2, warnings.revisions(owner, created.id(), 0).items().size());
        assertEquals(created.content(), warnings.revisions(owner, created.id(), 0).items().getLast().content());
    }

    enum WritePath { STANDALONE, DAILY, WEEKLY }

    @ParameterizedTest @EnumSource(WritePath.class)
    void firstMinorBreachRejectsEveryWritePathWithoutSavingWarningsOrReflections(WritePath path) {
        var owner = owner(end);
        record(owner, end.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3190);
        var summary = prepareReflections(owner);
        var request = request(CoachWarningType.NUTRITION_IMBALANCE, review("2598.5714285714", "2500"));
        long revisionCount = revisions.count();
        long notificationCount = notificationRecords.count();
        assertThrows(BadRequestException.class, () -> notifications.execute(owner, "Reflection saved", "/reflections",
            () -> save(path, owner, summary, request)));
        assertTrue(warnings.overview(owner).active().isEmpty());
        assertTrue(daily.find(owner, end).isEmpty());
        assertTrue(weeklyRecords.findByWeeklySummary(summary).isEmpty());
        assertEquals(revisionCount, revisions.count());
        assertEquals(notificationCount, notificationRecords.count());
        assertTrue(warningRecords.findByUserAndRequestKey(owner, request.requestKey().toString()).isEmpty());
    }

    @ParameterizedTest @EnumSource(value = WritePath.class, names = {"DAILY", "WEEKLY"})
    void ineligibleNewWarningRollsBackEarlierExistingWarningReview(WritePath path) {
        var owner = owner(end);
        record(owner, end.minusDays(7), 2500, 2500, 2500, 2500, 2500, 2500, 2500, 3190);
        var summary = prepareReflections(owner);
        var original = warnings.create(owner, request(CoachWarningType.SLEEP_DISRUPTION, WarningMetricFixtures.review(today, false)));
        var met = WarningMetricFixtures.review(today, true);
        var assessment = new WarningAssessment(WarningDirection.RESOLVED, "Goals met", "Complete records", "Keep recording",
            "Meet recorded goals", "Met", met.windowEnd().minusDays(met.windowDays() - 1), met.windowEnd(), null, null, null, null, met);
        var request = request(CoachWarningType.NUTRITION_IMBALANCE, review("2598.5714285714", "2500"));
        long revisionCount = revisions.count();
        long notificationCount = notificationRecords.count();
        assertThrows(BadRequestException.class, () -> notifications.execute(owner, "Reflection saved", "/reflections", () ->
            save(path, owner, summary, request, List.of(new ExistingWarningReview(original.id(), original.version(), assessment)))));
        var current = warnings.current(owner, original.id());
        assertEquals(CoachWarningStatus.ACTIVE, current.status());
        assertEquals(original.version(), current.version());
        assertEquals(original.progress(), current.progress());
        assertEquals(revisionCount, revisions.count());
        assertEquals(notificationCount, notificationRecords.count());
        assertTrue(daily.find(owner, end).isEmpty());
        assertTrue(weeklyRecords.findByWeeklySummary(summary).isEmpty());
    }

    @ParameterizedTest @EnumSource(WritePath.class)
    void persistentMinorBreachCreatesOnEveryWritePathAndLaterFailureRollsBackAtomically(WritePath path) {
        var owner = owner(end);
        record(owner, end.minusDays(7), 2507, 2500, 2500, 2500, 2500, 2500, 2500, 2514);
        var summary = prepareReflections(owner);
        var request = request(CoachWarningType.NUTRITION_IMBALANCE, review("2502", "2500"));
        long revisionCount = revisions.count();
        long notificationCount = notificationRecords.count();
        assertThrows(BadRequestException.class, () -> notifications.execute(owner, "Reflection saved", "/reflections", () -> {
            save(path, owner, summary, request);
            throw new BadRequestException("Later write rejected");
        }));
        assertTrue(warnings.overview(owner).active().isEmpty());
        assertTrue(daily.find(owner, end).isEmpty());
        assertTrue(weeklyRecords.findByWeeklySummary(summary).isEmpty());
        assertEquals(revisionCount, revisions.count());
        assertEquals(notificationCount, notificationRecords.count());

        save(path, owner, summary, request);
        var created = warnings.overview(owner).active().getFirst();
        assertEquals(WarningGoalStatus.NOT_MET, created.progress().goalStatus());
        assertEquals(new BigDecimal("2502"), created.progress().metricsReview().metrics().getFirst().value());
        assertEquals(1, warnings.revisions(owner, created.id(), 0).items().size());
        if (path == WritePath.STANDALONE) {
            assertEquals(created.id(), save(path, owner, summary, request).id());
        } else {
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> save(path, owner, summary, request));
            var snapshot = path == WritePath.DAILY ? daily.find(owner, end).orElseThrow().getWarningReview()
                : weeklyRecords.findByWeeklySummary(summary).orElseThrow().getWarningReview();
            assertEquals(created.id(), snapshot.items().getFirst().id());
            assertEquals(created.progress(), snapshot.items().getFirst().progress());
        }
    }

    private SavedWeeklySummary prepareReflections(User owner) {
        snapshots.getOrBuild(owner, end);
        var summary = new SavedWeeklySummary();
        summary.setUser(owner);
        summary.setFridayDate(end.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.FRIDAY)));
        summary.setSnapshotJson("{}");
        summary.setCreatedAt(Instant.now());
        return summaries.saveAndFlush(summary);
    }

    private WarningResponse save(WritePath path, User owner, SavedWeeklySummary summary, CreateWarningRequest request) {
        return save(path, owner, summary, request, List.of());
    }

    private WarningResponse save(WritePath path, User owner, SavedWeeklySummary summary, CreateWarningRequest request, List<ExistingWarningReview> existing) {
        if (path == WritePath.STANDALONE) return warnings.create(owner, request);
        var metrics = request.content().metricsReview();
        var assessment = new WarningAssessment(WarningDirection.UNCHANGED, "Above the recorded goal", "Recorded daily intake", "Keep recording",
            "Meet the recorded goal", "Above goal", metrics.windowEnd().minusDays(6), metrics.windowEnd(), null, null, null, null, metrics);
        var envelope = new WarningReviewEnvelope(today, existing, List.of(new NewWarningReview(request.requestKey(), request.type(), end.minusDays(6), assessment)));
        if (path == WritePath.DAILY) {
            var section = new ReflectionSection("Recorded evidence", "Keep recording");
            daily.save(owner, end, new SaveReflectionRequest("Daily review", "Recorded evidence", null, null,
                List.of("Consistent recording"), List.of("Intake exceeds goal"), List.of("Keep recording"), section, section, envelope, null));
        } else {
            var section = new WeeklyReflectionSection("Recorded evidence", "Keep recording");
            weekly.save(owner, summary.getFridayDate(), new SaveWeeklyReflectionRequest(true, "Weekly review", "Recorded evidence",
                section, section, section, section, section, section, List.of("Keep recording"), envelope));
        }
        return warnings.overview(owner).active().getFirst();
    }

    private WarningMetricReview withMetrics(WarningMetricReview review, List<WarningMetric> metrics) {
        return new WarningMetricReview(review.windowDays(), review.windowEnd(), review.todayComplete(), review.shortStatus(), metrics, null);
    }

    private User owner(LocalDate completed) {
        var user = new User();
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setLastCompletedDashboardDate(completed);
        return users.save(user);
    }

    private void record(User user, LocalDate start, int... calories) {
        for (int index = 0; index < calories.length; index++) {
            var meal = new Meal();
            meal.setUser(user);
            meal.setMealDate(start.plusDays(index));
            meal.setMealType(MealType.DINNER);
            meal.setMealSequence(1);
            meal.setCalories(calories[index]);
            meal.setSource(MealSource.MANUAL);
            meals.saveAndFlush(meal);
        }
    }

    private WarningMetricReview review(String value, String maximum) {
        var metric = new WarningMetric("arbitrary_key", "Recorded energy", "NUTRITION.dailyTotals.calories/completed", WarningMetric.Aggregation.MEAN,
            "kcal/day", new BigDecimal(value), null, new BigDecimal(maximum), 7, "Recorded personal maximum");
        return new WarningMetricReview(7, end, false, "Above the recorded maximum", List.of(metric), null);
    }

    private CreateWarningRequest request(CoachWarningType type, WarningMetricReview review) {
        return new CreateWarningRequest(UUID.randomUUID(), type, new WarningContent("Above goal", "Recorded daily intake",
            "Continue recording", end.minusDays(6), today, review));
    }
}
