package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.CoachDtos.ReflectionsContext;
import com.jllado.weightcontrol.api.dto.CoachDtos.CreateWarningRequest;
import com.jllado.weightcontrol.api.dto.CoachDtos.WarningContent;
import com.jllado.weightcontrol.api.dto.CoachDtos.UpdateWarningRequest;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.*;
import com.jllado.weightcontrol.domain.CoachDomain;
import com.jllado.weightcontrol.domain.CoachWarningStatus;
import com.jllado.weightcontrol.domain.CoachWarningType;
import com.jllado.weightcontrol.domain.DashboardReflection;
import com.jllado.weightcontrol.domain.InAppNotificationType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.WarningDirection;
import com.jllado.weightcontrol.repository.DashboardReflectionRepository;
import com.jllado.weightcontrol.repository.InAppNotificationRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.jllado.weightcontrol.util.DateTimes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {"app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example", "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"})
class ReflectionSectionsPersistenceTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean @ServiceConnection MariaDBContainer<?> database() {
            return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("reflection_sections");
        }
    }

    @Autowired DashboardReflectionService reflections;
    @Autowired DailyStatusSnapshotService snapshots;
    @Autowired HealthDataContextService context;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;
    @Autowired DashboardReflectionRepository records;
    @Autowired CoachWarningService warnings;
    @Autowired GptActionNotificationService actionNotifications;
    @Autowired InAppNotificationRepository notifications;

    @Test
    void laterWriteFailureRollsBackEarlierReviewAndProducesNoNotification() {
        var today = LocalDate.now(DateTimes.USER_ZONE);
        var date = today.minusDays(1);
        var owner = user("atomic-warning-review-" + UUID.randomUUID() + "@example.com", date);
        snapshots.getOrBuild(owner, date);
        var warning = warnings.create(owner, new CreateWarningRequest(UUID.randomUUID(), CoachWarningType.SLEEP_DISRUPTION,
            new WarningContent("Sleep varies.", "Earlier nights were short.", "Record sleep.", today.minusDays(14), date)));
        var assessment = assessment(today, WarningDirection.IMPROVING);
        var review = new WarningReviewEnvelope(today,
            List.of(new ExistingWarningReview(warning.id(), warning.version(), assessment)), List.of());
        var section = new ReflectionSection("Recorded evidence is mixed.", "Continue recording.");

        assertThrows(BadRequestException.class, () -> actionNotifications.execute(owner, "Reflection saved", "/reflections", () -> {
            reflections.save(owner, date, withReview(section, review));
            throw new BadRequestException("Later write rejected");
        }));

        assertTrue(reflections.find(owner, date).isEmpty());
        assertEquals(0, warnings.overview(owner).active().getFirst().version());
        assertEquals(1, warnings.revisions(owner, warning.id(), 0).items().size());
        assertTrue(notifications.findPending(owner, today, OffsetDateTime.now(DateTimes.USER_ZONE), Set.of(InAppNotificationType.GPT_ACTION)).isEmpty());
    }

    @Test
    void activeWarningSetAndVersionsMustMatchUnderOwnerLock() {
        var today = LocalDate.now(DateTimes.USER_ZONE);
        var date = today.minusDays(1);
        var owner = user("warning-set-owner-" + UUID.randomUUID() + "@example.com", date);
        var other = user("warning-set-other-" + UUID.randomUUID() + "@example.com", date);
        snapshots.getOrBuild(owner, date);
        var section = new ReflectionSection("Recorded evidence is mixed.", "Continue recording.");
        var draftedWhenEmpty = new WarningReviewEnvelope(today, List.of(), List.of());
        var warning = warnings.create(owner, new CreateWarningRequest(UUID.randomUUID(), CoachWarningType.SLEEP_DISRUPTION,
            new WarningContent("Sleep varies.", "Earlier nights were short.", "Record sleep.", today.minusDays(14), date)));
        var otherWarning = warnings.create(other, new CreateWarningRequest(UUID.randomUUID(), CoachWarningType.RECOVERY_STRAIN,
            new WarningContent("Recovery varies.", "Earlier recovery was lower.", "Record recovery.", today.minusDays(14), date)));
        var assessment = assessment(today, WarningDirection.IMPROVING);

        var changedSet = assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> reflections.save(owner, date, withReview(section, draftedWhenEmpty)));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, changedSet.getStatusCode());
        var stale = assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> reflections.save(owner, date, withReview(section, new WarningReviewEnvelope(today,
                List.of(new ExistingWarningReview(warning.id(), warning.version() + 1, assessment)), List.of()))));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, stale.getStatusCode());
        assertThrows(NotFoundException.class, () -> reflections.save(owner, date, withReview(section, new WarningReviewEnvelope(today,
            List.of(new ExistingWarningReview(otherWarning.id(), otherWarning.version(), assessment)), List.of()))));
        assertThrows(BadRequestException.class, () -> reflections.save(owner, date, withReview(section, new WarningReviewEnvelope(today,
            List.of(new ExistingWarningReview(warning.id(), warning.version(), assessment),
                new ExistingWarningReview(warning.id(), warning.version(), assessment)), List.of()))));
        assertThrows(BadRequestException.class, () -> reflections.save(owner, date, withReview(section, new WarningReviewEnvelope(today.minusDays(1),
            List.of(new ExistingWarningReview(warning.id(), warning.version(), assessment)), List.of()))));
        var duplicateType = assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> reflections.save(owner, date, withReview(section, new WarningReviewEnvelope(today,
                List.of(new ExistingWarningReview(warning.id(), warning.version(), assessment)),
                List.of(new NewWarningReview(UUID.randomUUID(), CoachWarningType.SLEEP_DISRUPTION, today.minusDays(3), assessment))))));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, duplicateType.getStatusCode());
        assertTrue(reflections.find(owner, date).isEmpty());
        assertEquals(0, warnings.overview(owner).active().getFirst().version());
    }

    @Test
    void exactRetryAfterCreatingWarningRequiresReadBackAndNewConfirmation() {
        var today = LocalDate.now(DateTimes.USER_ZONE);
        var date = today.minusDays(1);
        var owner = user("warning-retry-" + UUID.randomUUID() + "@example.com", date);
        snapshots.getOrBuild(owner, date);
        var section = new ReflectionSection("Recorded evidence is mixed.", "Continue recording.");
        var initial = assessment(today, WarningDirection.INSUFFICIENT_EVIDENCE);
        initial = new WarningAssessment(initial.direction(), initial.assessment(), initial.evidence(), initial.nextAction(),
            initial.resolutionCriteria(), initial.criteriaProgress(), initial.recentStart(), initial.recentEnd(), null, null, null, null);
        var review = new WarningReviewEnvelope(today, List.of(),
            List.of(new NewWarningReview(UUID.randomUUID(), CoachWarningType.RECOVERY_STRAIN, today.minusDays(3), initial)));
        var request = withReview(section, review);
        reflections.save(owner, date, request);
        var first = ReflectionResponse.from(reflections.find(owner, date).orElseThrow());

        var retry = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> reflections.save(owner, date, request));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, retry.getStatusCode());
        assertEquals(first, ReflectionResponse.from(reflections.find(owner, date).orElseThrow()));
        assertEquals(1, warnings.overview(owner).active().size());
        assertEquals(1, warnings.revisions(owner, first.warningReview().items().getFirst().id(), 0).items().size());
    }

    @Test
    void eachDirectionHasItsOwnSavedOutcomeAndEvidenceBasedResolutionStaysInHistory() {
        var today = LocalDate.now(DateTimes.USER_ZONE);
        var owner = user("warning-directions-" + UUID.randomUUID() + "@example.com", today.minusDays(1));
        var warning = warnings.create(owner, new CreateWarningRequest(UUID.randomUUID(), CoachWarningType.SLEEP_DISRUPTION,
            new WarningContent("Sleep varies.", "Earlier nights were short.", "Record sleep.", today.minusDays(14), today.minusDays(1))));
        var section = new ReflectionSection("Historical evidence was reviewed.", "Continue recording.");
        var directions = List.of(WarningDirection.UNCHANGED, WarningDirection.WORSENING,
            WarningDirection.INSUFFICIENT_EVIDENCE, WarningDirection.RESOLVED);
        for (int index = 0; index < directions.size(); index++) {
            var selectedDate = today.minusDays(directions.size() - index);
            snapshots.getOrBuild(owner, selectedDate);
            var current = warnings.overview(owner).active().getFirst();
            var direction = directions.get(index);
            var base = assessment(today, direction);
            var assessment = new WarningAssessment(direction, base.assessment(), base.evidence(), base.nextAction(),
                base.resolutionCriteria(), base.criteriaProgress(), base.recentStart(), base.recentEnd(),
                direction == WarningDirection.INSUFFICIENT_EVIDENCE ? null : base.priorStart(),
                direction == WarningDirection.INSUFFICIENT_EVIDENCE ? null : base.priorEnd(), null,
                direction == WarningDirection.RESOLVED ? "Recent recorded nights support resolution against the stated criteria." : null);
            reflections.save(owner, selectedDate, withReview(section, new WarningReviewEnvelope(today,
                List.of(new ExistingWarningReview(current.id(), current.version(), assessment)), List.of())));
            var item = ReflectionResponse.from(reflections.find(owner, selectedDate).orElseThrow()).warningReview().items().getFirst();
            assertEquals(direction, item.progress().direction());
            assertEquals(direction == WarningDirection.RESOLVED ? CoachWarningStatus.RESOLVED : CoachWarningStatus.ACTIVE, item.status());
            assertEquals(today, item.progress().reviewedDate());
        }
        assertTrue(warnings.overview(owner).active().isEmpty());
        assertEquals(WarningDirection.RESOLVED, warnings.history(owner, 0).items().getFirst().progress().direction());
        assertEquals(WarningDirection.UNCHANGED, ReflectionResponse.from(reflections.find(owner, today.minusDays(4)).orElseThrow())
            .warningReview().items().getFirst().progress().direction());
    }

    @Test
    void changedResolutionCriteriaNeedAnExplanationAndReplacementTouchesOnlyItsOwnSnapshot() {
        var today = LocalDate.now(DateTimes.USER_ZONE);
        var firstDate = today.minusDays(2);
        var replacementDate = today.minusDays(1);
        var owner = user("warning-criteria-" + UUID.randomUUID() + "@example.com", replacementDate);
        snapshots.getOrBuild(owner, firstDate);
        snapshots.getOrBuild(owner, replacementDate);
        var warning = warnings.create(owner, new CreateWarningRequest(UUID.randomUUID(), CoachWarningType.SLEEP_DISRUPTION,
            new WarningContent("Sleep varies.", "Earlier nights were short.", "Record sleep.", today.minusDays(14), firstDate)));
        var section = new ReflectionSection("Historical evidence was reviewed.", "Continue recording.");
        var original = assessment(today, WarningDirection.IMPROVING);
        reflections.save(owner, firstDate, withReview(section, new WarningReviewEnvelope(today,
            List.of(new ExistingWarningReview(warning.id(), warning.version(), original)), List.of())));
        var originalSnapshot = ReflectionResponse.from(reflections.find(owner, firstDate).orElseThrow()).warningReview();
        var changed = new WarningAssessment(WarningDirection.UNCHANGED, original.assessment(), original.evidence(), original.nextAction(),
            "With sufficient recent records, both sleep minimums are met without recurring disruption.",
            original.criteriaProgress(), original.recentStart(), original.recentEnd(), original.priorStart(), original.priorEnd(), null, null);
        var current = warnings.overview(owner).active().getFirst();
        assertThrows(BadRequestException.class, () -> reflections.save(owner, replacementDate, withReview(section,
            new WarningReviewEnvelope(today, List.of(new ExistingWarningReview(warning.id(), current.version(), changed)), List.of()))));
        assertTrue(reflections.find(owner, replacementDate).isEmpty());
        assertEquals(current.version(), warnings.overview(owner).active().getFirst().version());

        var explained = new WarningAssessment(changed.direction(), changed.assessment(), changed.evidence(), changed.nextAction(),
            changed.resolutionCriteria(), changed.criteriaProgress(), changed.recentStart(), changed.recentEnd(),
            changed.priorStart(), changed.priorEnd(), "The previous wording omitted evidence coverage.", null);
        reflections.save(owner, replacementDate, withReview(section, new WarningReviewEnvelope(today,
            List.of(new ExistingWarningReview(warning.id(), current.version(), explained)), List.of())));
        var firstReplacement = ReflectionResponse.from(reflections.find(owner, replacementDate).orElseThrow()).warningReview();
        assertEquals("The previous wording omitted evidence coverage.", firstReplacement.items().getFirst().progress().criteriaChangeReason());

        var next = warnings.overview(owner).active().getFirst();
        reflections.save(owner, replacementDate, withReview(section, new WarningReviewEnvelope(today,
            List.of(new ExistingWarningReview(warning.id(), next.version(), changed)), List.of())));
        var updatedReplacement = ReflectionResponse.from(reflections.find(owner, replacementDate).orElseThrow()).warningReview();
        assertEquals(firstReplacement.items().getFirst().id(), updatedReplacement.items().getFirst().id());
        assertEquals(firstReplacement.items().getFirst().version() + 1, updatedReplacement.items().getFirst().version());
        assertEquals(originalSnapshot, ReflectionResponse.from(reflections.find(owner, firstDate).orElseThrow()).warningReview());
    }

    @Test
    void newlyDetectedWarningHasInitialProgressAndOnlyReplacementSnapshotChanges() {
        var today = LocalDate.now(DateTimes.USER_ZONE);
        var firstDate = today.minusDays(2);
        var nextDate = today.minusDays(1);
        var owner = user("new-warning-review-" + UUID.randomUUID() + "@example.com", nextDate);
        snapshots.getOrBuild(owner, firstDate);
        snapshots.getOrBuild(owner, nextDate);
        var section = new ReflectionSection("Recorded evidence is mixed.", "Continue recording.");
        var initial = assessment(today, WarningDirection.INSUFFICIENT_EVIDENCE);
        initial = new WarningAssessment(initial.direction(), initial.assessment(), initial.evidence(), initial.nextAction(),
            initial.resolutionCriteria(), initial.criteriaProgress(), initial.recentStart(), initial.recentEnd(), null, null, null, null);
        var requestKey = UUID.randomUUID();
        var create = new WarningReviewEnvelope(today, List.of(),
            List.of(new NewWarningReview(requestKey, CoachWarningType.RECOVERY_STRAIN, today.minusDays(3), initial)));
        reflections.save(owner, firstDate, withReview(section, create));
        var oldSnapshot = ReflectionResponse.from(reflections.find(owner, firstDate).orElseThrow()).warningReview();
        assertEquals(1, oldSnapshot.items().size());
        assertTrue(oldSnapshot.items().getFirst().progress().initialReview());
        assertEquals(WarningDirection.INSUFFICIENT_EVIDENCE, oldSnapshot.items().getFirst().progress().direction());

        var warning = warnings.overview(owner).active().getFirst();
        var reassessment = new WarningReviewEnvelope(today,
            List.of(new ExistingWarningReview(warning.id(), warning.version(), assessment(today, WarningDirection.IMPROVING))), List.of());
        reflections.save(owner, nextDate, withReview(section, reassessment));
        assertEquals(oldSnapshot, ReflectionResponse.from(reflections.find(owner, firstDate).orElseThrow()).warningReview());
        assertFalse(ReflectionResponse.from(reflections.find(owner, nextDate).orElseThrow()).warningReview().items().getFirst().progress().initialReview());
        var latest = warnings.overview(owner).active().getFirst();
        assertEquals(WarningDirection.IMPROVING, latest.progress().direction());

        warnings.update(owner, warning.id(), new UpdateWarningRequest(latest.version(),
            new WarningContent("Clarified explanation.", "Supplementary evidence.", "Continue observing.", today.minusDays(3), today)));
        assertEquals(today, warnings.overview(owner).active().getFirst().progress().reviewedDate());
        assertEquals(WarningDirection.IMPROVING, warnings.overview(owner).active().getFirst().progress().direction());
        assertEquals(oldSnapshot, ReflectionResponse.from(reflections.find(owner, firstDate).orElseThrow()).warningReview());
    }

    @Test
    void dailySaveReviewsEveryActiveWarningAndKeepsItsDatedSnapshot() {
        var reviewedDate = LocalDate.now(DateTimes.USER_ZONE);
        var selectedDate = reviewedDate.minusDays(1);
        var owner = user("reviewed-reflection-" + UUID.randomUUID() + "@example.com", selectedDate);
        snapshots.getOrBuild(owner, selectedDate);
        var warning = warnings.create(owner, new CreateWarningRequest(UUID.randomUUID(), CoachWarningType.SLEEP_DISRUPTION,
            new WarningContent("Sleep has varied.", "Earlier recorded nights were short.", "Keep recording sleep.", reviewedDate.minusDays(14), selectedDate)));
        var assessment = new WarningAssessment(WarningDirection.IMPROVING, "More recent nights meet both personal minimums.",
            "Recent recorded nights improved against the earlier period; one short night remains.", "Keep a consistent sleep window.",
            "With sufficient recent evidence, sleep is consistently at least 7h in bed and 6h asleep without recurring disruption.", "Recent evidence moves toward the criteria; one short night needs context.",
            reviewedDate.minusDays(6), reviewedDate, reviewedDate.minusDays(13), reviewedDate.minusDays(7), null, null);
        var review = new WarningReviewEnvelope(reviewedDate, List.of(new ExistingWarningReview(warning.id(), warning.version(), assessment)), List.of());
        var section = new ReflectionSection("Recorded evidence is mixed.", "Continue recording.");
        var base = request(section, section);
        var request = new SaveReflectionRequest(base.title(), base.summary(), base.planProgressScore(), base.planProgressRationale(),
            base.positiveSignals(), base.watchouts(), base.nextActions(), base.meals(), base.workouts(), review);

        reflections.save(owner, selectedDate, request);

        var saved = ReflectionResponse.from(reflections.find(owner, selectedDate).orElseThrow()).warningReview();
        assertEquals(reviewedDate, saved.reviewedDate());
        assertEquals(1, saved.items().size());
        assertEquals(warning.id(), saved.items().getFirst().id());
        assertEquals(CoachWarningStatus.ACTIVE, saved.items().getFirst().status());
        assertEquals(WarningDirection.IMPROVING, saved.items().getFirst().progress().direction());
        assertEquals(1, warnings.overview(owner).active().getFirst().version());
        assertEquals(2, warnings.revisions(owner, warning.id(), 0).items().size());
    }

    @Test
    void omittedWarningReviewCannotSaveDailyReflectionEvenWithNoActiveWarnings() {
        var date = LocalDate.of(2026, 8, 21);
        var owner = user("missing-warning-review-" + UUID.randomUUID() + "@example.com", date);
        snapshots.getOrBuild(owner, date);
        var section = new ReflectionSection("No measured change.", "Keep recording evidence.");

        var valid = request(section, section);
        var omitted = new SaveReflectionRequest(valid.title(), valid.summary(), valid.planProgressScore(), valid.planProgressRationale(),
            valid.positiveSignals(), valid.watchouts(), valid.nextActions(), valid.meals(), valid.workouts(), null);
        assertThrows(BadRequestException.class, () -> reflections.save(owner, date, omitted));
        assertTrue(reflections.find(owner, date).isEmpty());
    }

    @Test
    void savesReadsReplacesAndRejectsIncompleteWritesWithoutChangingPriorEvidence() throws Exception {
        var date = LocalDate.of(2026, 8, 20);
        var owner = user("reflection-owner@example.com", date.plusDays(1));
        var other = user("reflection-other@example.com", date.plusDays(1));
        snapshots.getOrBuild(owner, date);
        var meals = new ReflectionSection("Macros are partial in the recorded meals.", "Record portions at lunch.");
        var workouts = new ReflectionSection("Two comparable strength sessions were recorded.", "Repeat the planned strength session.");
        var saved = reflections.save(owner, date, request(meals, workouts));
        var read = ReflectionResponse.from(reflections.find(owner, date).orElseThrow());
        assertEquals(meals, read.meals());
        assertEquals(workouts, read.workouts());
        assertEquals(7, read.planProgressScore());
        assertEquals(List.of("Mood improved"), read.positiveSignals());
        assertTrue(reflections.find(other, date).isEmpty());
        assertThrows(BadRequestException.class, () -> reflections.save(owner, date.plusDays(2), request(meals, workouts)));

        var recent = context.getReflectionContext(owner, date.plusDays(1)).recentReflections().getFirst();
        assertEquals(meals, recent.meals());
        assertEquals(workouts, recent.workouts());
        assertTrue(context.getReflectionContext(owner, date).recentReflections().isEmpty());
        var coach = (ReflectionsContext) context.getHealthContext(owner, date, date, Set.of(CoachDomain.REFLECTIONS), OffsetDateTime.now()).data().get(CoachDomain.REFLECTIONS);
        assertEquals(meals, coach.reflections().getFirst().meals());
        assertFalse(json.writeValueAsString(coach).contains(owner.getEmail()));
        assertFalse(json.writeValueAsString(coach).contains("\"id\""));
        var privateContext = (ReflectionsContext) context.getHealthContext(other, date, date, Set.of(CoachDomain.REFLECTIONS), OffsetDateTime.now()).data().get(CoachDomain.REFLECTIONS);
        assertTrue(privateContext.reflections().isEmpty());

        var expandedBase = request(meals, workouts);
        var longAction = "A".repeat(200);
        var expanded = new SaveReflectionRequest(expandedBase.title(), expandedBase.summary(), expandedBase.planProgressScore(),
            expandedBase.planProgressRationale(), expandedBase.positiveSignals(), expandedBase.watchouts(), List.of(longAction), meals, workouts, expandedBase.warningReview());
        assertEquals(saved.getId(), reflections.save(owner, date, expanded).getId());
        assertEquals(List.of(longAction), ReflectionResponse.from(reflections.find(owner, date).orElseThrow()).nextActions());

        var replacement = new ReflectionSection("Portions remain partly recorded.", "Add a portion note.");
        assertEquals(saved.getId(), reflections.save(owner, date, request(replacement, workouts)).getId());
        var prior = json.writeValueAsString(ReflectionResponse.from(reflections.find(owner, date).orElseThrow()));
        var valid = new ReflectionSection("No evidence recorded.", "Record evidence.");
        for (var invalid : java.util.Arrays.asList(null,
            new ReflectionSection(" ", "Action"), new ReflectionSection("Summary", " "),
            new ReflectionSection(null, "Action"), new ReflectionSection("Summary", null),
            new ReflectionSection("S".repeat(201), "Action"), new ReflectionSection("Summary", "A".repeat(121)))) {
            assertThrows(BadRequestException.class, () -> reflections.save(owner, date, request(invalid, valid)));
            assertThrows(BadRequestException.class, () -> reflections.save(owner, date, request(valid, invalid)));
            assertEquals(prior, json.writeValueAsString(ReflectionResponse.from(reflections.find(owner, date).orElseThrow())));
        }
        var compact = request(valid, valid);
        var tooLong = new SaveReflectionRequest(compact.title(), compact.summary(), compact.planProgressScore(),
            compact.planProgressRationale(), compact.positiveSignals(), compact.watchouts(), List.of("A".repeat(201)), valid, valid, compact.warningReview());
        assertThrows(BadRequestException.class, () -> reflections.save(owner, date, tooLong));
        assertEquals(prior, json.writeValueAsString(ReflectionResponse.from(reflections.find(owner, date).orElseThrow())));
        var omitted = json.readValue("""
            {"title":"Legacy client","summary":"Summary","positiveSignals":["Positive"],"watchouts":["Watch"],"nextActions":["Action"]}
            """, SaveReflectionRequest.class);
        assertThrows(BadRequestException.class, () -> reflections.save(owner, date, omitted));
        assertEquals(prior, json.writeValueAsString(ReflectionResponse.from(reflections.find(owner, date).orElseThrow())));

        // Simulate an existing pre-section record; read contracts remain compatible.
        var legacy = reflections.find(owner, date).orElseThrow();
        legacy.setMealsSummary(null);
        legacy.setMealsNextAction(null);
        legacy.setWorkoutsSummary(null);
        legacy.setWorkoutsNextAction(null);
        records.saveAndFlush(legacy);
        read = ReflectionResponse.from(reflections.find(owner, date).orElseThrow());
        assertNull(read.meals());
        assertNull(read.workouts());
        assertEquals(1, reflections.getOverview(owner).reflections().size());
    }

    @Test
    void pagedDailyArchiveIsOwnedAndCoachOverviewRetainsAllSummaries() {
        var newest = LocalDate.of(2026, 10, 20);
        var owner = user("paged-reflection-owner@example.com", newest);
        var other = user("paged-reflection-other@example.com", newest);
        for (int index = 0; index < 13; index++) {
            records.save(reflection(owner, newest.minusDays(index), "Owner " + index));
        }
        records.saveAndFlush(reflection(other, newest, "Other account"));

        var firstPage = reflections.getPagedOverview(owner, 0);
        var secondPage = reflections.getPagedOverview(owner, 1);

        assertEquals(13, firstPage.totalElements());
        assertEquals(2, firstPage.totalPages());
        assertEquals(10, firstPage.reflections().size());
        assertEquals(newest, firstPage.reflections().getFirst().reflectionDate());
        assertEquals(3, secondPage.reflections().size());
        assertEquals(newest.minusDays(10), secondPage.reflections().getFirst().reflectionDate());
        assertEquals(newest.minusDays(12), secondPage.reflections().getLast().reflectionDate());
        assertEquals(13, reflections.getOverview(owner).reflections().size());
        var otherPage = reflections.getPagedOverview(other, 0);
        assertEquals(1, otherPage.totalElements());
        assertEquals("Other account", otherPage.reflections().getFirst().title());
    }

    private DashboardReflection reflection(User user, LocalDate date, String title) {
        var reflection = new DashboardReflection();
        reflection.setUser(user);
        reflection.setReflectionDate(date);
        reflection.setWindowStart(date.minusDays(89));
        reflection.setWindowEnd(date);
        reflection.setGeneratedAt(Instant.parse("2026-10-20T08:00:00Z"));
        reflection.setModel("ChatGPT");
        reflection.setTitle(title);
        reflection.setSummary("Recorded summary");
        reflection.setPositiveSignals(List.of("Positive"));
        reflection.setWatchouts(List.of("Watchout"));
        reflection.setNextActions(List.of("Action"));
        return reflection;
    }

    private User user(String email, LocalDate completed) {
        var user = new User();
        user.setEmail(email);
        user.setLastCompletedDashboardDate(completed);
        return users.save(user);
    }

    private SaveReflectionRequest request(ReflectionSection meals, ReflectionSection workouts) {
        return new SaveReflectionRequest("Steady progress", "Review of recorded evidence.", 7, "Agreed actions are progressing.",
            List.of("Mood improved"), List.of("Sleep varies"), List.of("Keep a regular bedtime"), meals, workouts,
            new WarningReviewEnvelope(LocalDate.now(DateTimes.USER_ZONE), List.of(), List.of()));
    }

    private SaveReflectionRequest withReview(ReflectionSection section, WarningReviewEnvelope review) {
        var base = request(section, section);
        return new SaveReflectionRequest(base.title(), base.summary(), base.planProgressScore(), base.planProgressRationale(),
            base.positiveSignals(), base.watchouts(), base.nextActions(), base.meals(), base.workouts(), review);
    }

    private WarningAssessment assessment(LocalDate today, WarningDirection direction) {
        return new WarningAssessment(direction, "Recent records improved.", "Recent records compared with the earlier period.",
            "Keep recording the relevant signal.", "Sustained records meet the agreed personal minimums.",
            "Evidence moves toward the agreed criteria.", today.minusDays(6), today, today.minusDays(13), today.minusDays(7), null, null);
    }
}
