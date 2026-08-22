package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.User;
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

        sender.send(user, progress, emptyMeasurements());
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
        assertTrue(htmlContent(message).contains("comparison comparison--improved"));
        assertTrue(raw.contains("Open Weight Control"));
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
