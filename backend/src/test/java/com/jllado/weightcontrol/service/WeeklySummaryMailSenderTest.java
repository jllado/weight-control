package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.SavedWeeklySummary;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.WeeklyReflection;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class WeeklySummaryMailSenderTest {

    @Test
    void sendsMultipartEmailWithHtmlTextLogoAndPrivacyHeaders() throws Exception {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(message);
        WeeklySummaryMailSender sender = new WeeklySummaryMailSender(
            javaMailSender,
            templateEngine(),
            new WeeklySummaryEmailViewFactory(),
            properties()
        );
        User user = user();
        LocalDate end = LocalDate.of(2026, 8, 14);
        WeeklyMetrics.Progress progress = new WeeklyMetricsCalculator().progress(user, end, input(end));

        sender.send(user, progress, emptyMeasurements(), List.of());
        message.saveChanges();

        verify(javaMailSender).send(message);
        assertEquals("Weight Control", ((InternetAddress) message.getFrom()[0]).getPersonal());
        assertEquals("summary@example.com", ((InternetAddress) message.getAllRecipients()[0]).getAddress());
        assertTrue(message.getSubject().startsWith("Your Weight Control weekly summary"));
        assertArrayEquals(new String[]{"no"}, message.getHeader("X-Mailgun-Track"));
        assertArrayEquals(new String[]{"no"}, message.getHeader("X-Mailgun-Track-Clicks"));
        assertArrayEquals(new String[]{"no"}, message.getHeader("X-Mailgun-Track-Opens"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        message.writeTo(output);
        String raw = output.toString(StandardCharsets.UTF_8);
        assertTrue(raw.contains("multipart/alternative"));
        assertTrue(raw.contains("Content-ID: <weight-control-logo>"));
        assertTrue(raw.contains("Routine completion"));
        assertTrue(raw.contains("No new records this week"));
        assertTrue(htmlContent(message).contains("comparison comparison--improved"));
        assertTrue(raw.contains("Open Weight Control"));
        for (String content : List.of("Workout activity", "Strength sessions", "Cardio sessions", "Stretching sessions", "Active cardio minutes", "0 min")) {
            assertTrue(htmlContent(message).contains(content), content);
            assertTrue(plainText(message).contains(content), content);
        }
    }

    @Test
    void savedSummaryEmailIncludesOnlyItsWeeklyReflectionAndDatedMissingOutcomeWarnings() throws Exception {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        MimeMessage withoutReflection = new MimeMessage(Session.getInstance(new Properties()));
        MimeMessage withReflection = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(withoutReflection, withReflection);
        WeeklySummaryMailSender sender = new WeeklySummaryMailSender(javaMailSender, templateEngine(), new WeeklySummaryEmailViewFactory(), properties());
        User user = user();
        LocalDate friday = LocalDate.of(2026, 8, 14);
        WeeklyMetrics.Progress progress = new WeeklyMetricsCalculator().progress(user, friday, input(friday));
        WeeklySummarySnapshot snapshot = new WeeklySummarySnapshot(
            1, LocalDate.of(2026, 8, 8), friday, progress,
            new WeeklySummarySnapshot.OutcomeMeasurements(null, null), List.of(),
            new WeeklySummarySnapshot.GoalEvidence(false, null, null, null, "Historical plan unavailable."), List.of(),
            List.of("No weight measurement was recorded Friday, Saturday, or Sunday.", "No blood pressure measurement was recorded Friday, Saturday, or Sunday.")
        );

        sender.send(user, snapshot, null);
        var reflection = weeklyReflection(friday);
        reflection.setId(42L);
        reflection.setNextWeekActions(List.of("Review body data.", " review body DATA. "));
        sender.send(user, snapshot, reflection);
        withoutReflection.saveChanges();
        withReflection.saveChanges();
        String noReflectionHtml = htmlContent(withoutReflection);
        String reflectionHtml = htmlContent(withReflection);
        String reflectionText = plainText(withReflection);

        assertTrue(noReflectionHtml.contains("No measurement recorded"));
        assertTrue(noReflectionHtml.contains("weekly-summaries/2026-08-14"));
        assertTrue(!noReflectionHtml.contains("Weekly reflection</div>"));
        assertTrue(reflectionHtml.contains("Week &lt;review&gt;"));
        assertTrue(reflectionHtml.contains("Saved evidence."));
        assertTrue(reflectionText.contains("Weekly reflection: Week <review>"));
        assertEquals(1, reflectionText.split("Review body data\\.", -1).length - 1);
        assertEquals(1, reflectionHtml.split("Review body data\\.", -1).length - 1);
        assertTrue(reflectionHtml.contains("/actions?source=weekly%3A42%3A"));
        assertTrue(reflectionText.contains("/actions?source=weekly%3A42%3A"));
        assertTrue(!reflectionText.contains("Next action:"));
        assertTrue(!reflectionHtml.contains("Next action:"));
        assertTrue(reflectionText.indexOf("Your week at a glance") < reflectionText.indexOf("Next steps"));
        assertTrue(reflectionText.indexOf("Next steps") < reflectionText.indexOf("New records"));
        assertTrue(reflectionText.indexOf("New records") < reflectionText.indexOf("Detailed measurements"));
        assertTrue(reflectionText.indexOf("Detailed measurements") < reflectionText.indexOf("Routine completion:"));
        java.nio.file.Files.createDirectories(Path.of("build/test-artifacts"));
        java.nio.file.Files.writeString(Path.of("build/test-artifacts/weekly-actions-report.html"), reflectionHtml);
        java.nio.file.Files.writeString(Path.of("build/test-artifacts/weekly-actions-report.txt"), reflectionText);
        verify(javaMailSender, org.mockito.Mockito.times(2)).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    @Test
    void activityHtmlAndPlainTextPreserveFractionsPartialCoverageAndLegacyUnknowns() throws Exception {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        MimeMessage legacyMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(message, legacyMessage);
        User user = user();
        LocalDate friday = LocalDate.of(2026, 8, 14);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ObjectNode json = mapper.valueToTree(new WeeklyMetricsCalculator().progress(user, friday, input(friday)));
        ObjectNode current = (ObjectNode) json.at("/currentPeriod/workouts");
        current.put("strengthSessionCount", 2).put("cardioSessionCount", 1).put("stretchingSessionCount", 1)
            .put("activeCardioSeconds", 90).put("cardioDurationComplete", false);
        ObjectNode previous = (ObjectNode) json.at("/previousComparablePeriod/workouts");
        previous.put("strengthSessionCount", 1).put("activeCardioSeconds", 30);
        ((ObjectNode) json.at("/yearAgoComparablePeriod/workouts")).remove(List.of("strengthSessionCount", "cardioSessionCount", "stretchingSessionCount", "activeCardioSeconds", "cardioDurationComplete"));
        WeeklyMetrics.Progress progress = mapper.treeToValue(json, WeeklyMetrics.Progress.class);
        WeeklySummarySnapshot snapshot = new WeeklySummarySnapshot(5, friday.minusDays(6), friday, progress,
            new WeeklySummarySnapshot.OutcomeMeasurements(null, null), List.of(), null, List.of(), List.of());
        WeeklySummaryMailSender sender = new WeeklySummaryMailSender(javaMailSender, templateEngine(), new WeeklySummaryEmailViewFactory(), properties());
        sender.send(user, snapshot, null);
        message.saveChanges();
        String html = htmlContent(message);
        String text = plainText(message);
        for (String value : List.of("Strength sessions", "2 sessions", "Cardio sessions", "Stretching sessions", "Active cardio minutes", "1.5 min · Partial coverage", "+1 min vs last week", "0.5 min", "Not available", "52 weeks ago")) {
            assertTrue(html.contains(value), "HTML: " + value);
            assertTrue(text.contains(value), "Text: " + value);
        }
        assertTrue(!html.contains("Timed activity"));
        assertEquals(1, text.split("Workouts:", -1).length - 1);
        java.nio.file.Files.createDirectories(Path.of("build/test-artifacts"));
        java.nio.file.Files.writeString(Path.of("build/test-artifacts/weekly-workout-activity.html"), html);
        java.nio.file.Files.writeString(Path.of("build/test-artifacts/weekly-workout-activity.txt"), text);
        for (String period : List.of("currentPeriod", "previousComparablePeriod", "yearAgoComparablePeriod")) {
            ((ObjectNode) json.at("/" + period + "/workouts")).remove(List.of("strengthSessionCount", "cardioSessionCount", "stretchingSessionCount", "activeCardioSeconds", "cardioDurationComplete"));
        }
        WeeklySummarySnapshot legacy = new WeeklySummarySnapshot(4, friday.minusDays(6), friday, mapper.treeToValue(json, WeeklyMetrics.Progress.class),
            new WeeklySummarySnapshot.OutcomeMeasurements(null, null), List.of(), null, List.of(), List.of());
        sender.send(user, legacy, null);
        legacyMessage.saveChanges();
        assertTrue(plainText(legacyMessage).contains("Strength sessions: Not available"));
        assertTrue(plainText(legacyMessage).contains("Active cardio minutes: Not available"));
        java.nio.file.Files.writeString(Path.of("build/test-artifacts/weekly-workout-legacy.html"), htmlContent(legacyMessage));
    }

    private String htmlContent(Part part) throws Exception {
        if (part.isMimeType("text/html")) {
            return part.getContent().toString();
        }
        Multipart multipart = (Multipart) part.getContent();
        for (int index = 0; index < multipart.getCount(); index++) {
            Part bodyPart = multipart.getBodyPart(index);
            if (bodyPart.isMimeType("text/html") || bodyPart.isMimeType("multipart/*")) {
                String html = htmlContent(bodyPart);
                if (!html.isEmpty()) {
                    return html;
                }
            }
        }
        return "";
    }

    private String plainText(Part part) throws Exception {
        if (part.isMimeType("text/plain")) return part.getContent().toString();
        Multipart multipart = (Multipart) part.getContent();
        for (int index = 0; index < multipart.getCount(); index++) {
            String text = plainText(multipart.getBodyPart(index));
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    private WeeklyReflection weeklyReflection(LocalDate friday) {
        SavedWeeklySummary summary = new SavedWeeklySummary();
        summary.setFridayDate(friday);
        WeeklyReflection reflection = new WeeklyReflection();
        reflection.setWeeklySummary(summary);
        reflection.setGeneratedAt(java.time.Instant.parse("2026-08-17T07:00:00Z"));
        reflection.setModel("ChatGPT");
        reflection.setTitle("Week <review>");
        reflection.setSummary("Saved evidence.");
        reflection.setBodyCompositionSummary("Body evidence.");
        reflection.setBodyCompositionNextAction("Review body data.");
        reflection.setBloodPressureSummary("BP evidence.");
        reflection.setBloodPressureNextAction("Review BP.");
        reflection.setRoutinesSummary("Routine evidence.");
        reflection.setRoutinesNextAction("Continue routines.");
        reflection.setNutritionSummary("Nutrition evidence.");
        reflection.setNutritionNextAction("Log meals.");
        reflection.setTrainingRecoverySummary("Training evidence.");
        reflection.setTrainingRecoveryNextAction("Recover well.");
        reflection.setGoalProgressSummary("Goal evidence.");
        reflection.setGoalProgressNextAction("Review plan.");
        reflection.setNextWeekActions(List.of("Continue"));
        return reflection;
    }

    private SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private AppProperties properties() {
        return new AppProperties(
            new AppProperties.Auth("client", "test-jwt-secret-test-jwt-secret", 7, false),
            new AppProperties.Cors(List.of()),
            new AppProperties.Storage(Path.of("data")),
            new AppProperties.ChatGptActions("", "owner@example.com", "https://test.example", "test-file-signing-secret-32-bytes-long"),
            new AppProperties.Push(false, "", "", "", ""),
            new AppProperties.WeeklySummary(true, "owner@example.com", "summary@example.com", "sender@example.com", "https://weight.example")
        );
    }

    private WeeklyMetricsCalculator.Input input(LocalDate end) {
        List<DailyStatus> statuses = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            statuses.add(status(end.minusDays(6).plusDays(index), 3));
            statuses.add(status(end.minusWeeks(1).minusDays(6).plusDays(index), 2));
        }
        return new WeeklyMetricsCalculator.Input(statuses, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private WeeklySummaryMeasurements emptyMeasurements() {
        WeeklySummaryMeasurements.PeriodMeasurements empty = new WeeklySummaryMeasurements.PeriodMeasurements(null, null);
        return new WeeklySummaryMeasurements(empty, empty, empty);
    }

    private DailyStatus status(LocalDate date, int completed) {
        DailyStatus status = new DailyStatus();
        status.setStatusDate(date);
        status.setRoutinesDone(completed);
        status.setTotalRoutines(4);
        status.setTotalWeightRoutines(0);
        status.setTotalBloodPressureRoutines(0);
        status.setTotalFlexibilityRoutines(0);
        status.setTotalMindRoutines(0);
        status.setRoutinesPercentage(BigDecimal.valueOf(completed * 25L));
        status.setWeightPercentage(BigDecimal.ZERO);
        status.setBloodPressurePercentage(BigDecimal.ZERO);
        status.setFlexibilityPercentage(BigDecimal.ZERO);
        status.setMindPercentage(BigDecimal.ZERO);
        return status;
    }

    private User user() {
        User user = new User();
        user.setEmail("owner@example.com");
        user.setDisplayName("Owner");
        user.setTypicalCaloriesSaturday(2000);
        user.setTypicalCaloriesSunday(2000);
        user.setTypicalCaloriesMonday(2000);
        user.setTypicalCaloriesTuesday(2000);
        user.setTypicalCaloriesWednesday(2000);
        user.setTypicalCaloriesThursday(2000);
        user.setTypicalCaloriesFriday(2000);
        return user;
    }
}
