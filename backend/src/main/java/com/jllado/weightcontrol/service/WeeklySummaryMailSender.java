package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.HistoryEventResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.WeeklyReflection;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Component
public class WeeklySummaryMailSender {

    private static final String LOGO_CONTENT_ID = "weight-control-logo";

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final WeeklySummaryEmailViewFactory viewFactory;
    private final AppProperties properties;

    public WeeklySummaryMailSender(
        JavaMailSender mailSender,
        SpringTemplateEngine templateEngine,
        WeeklySummaryEmailViewFactory viewFactory,
        AppProperties properties
    ) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.viewFactory = viewFactory;
        this.properties = properties;
    }

    public void send(User user, WeeklyMetrics.Progress progress, WeeklySummaryMeasurements measurements, List<HistoryEventResponse> records) {
        WeeklySummaryEmailView view = viewFactory.create(user, progress, measurements, records, properties.weeklySummary().appUrl());
        send(view);
    }

    public void send(User user, WeeklySummarySnapshot snapshot, WeeklyReflection reflection) {
        send(viewFactory.create(user, snapshot, reflection, properties.weeklySummary().appUrl()));
    }

    private void send(WeeklySummaryEmailView view) {
        Context context = new Context();
        context.setVariable("summary", view);
        String html = templateEngine.process("email/weekly-summary", context);
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.weeklySummary().senderEmail(), "Weight Control");
            helper.setTo(properties.weeklySummary().recipientEmail());
            helper.setSubject(view.subject());
            helper.setText(plainText(view), html);
            helper.addInline(LOGO_CONTENT_ID, new ClassPathResource("email/weight-control-logo.png"), "image/png");
            message.setHeader("X-Mailgun-Track", "no");
            message.setHeader("X-Mailgun-Track-Clicks", "no");
            message.setHeader("X-Mailgun-Track-Opens", "no");
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("Could not create weekly summary email", e);
        }
        mailSender.send(message);
    }

    private String plainText(WeeklySummaryEmailView view) {
        var summary = view.visualSummary();
        var routines = summary.routines();
        StringBuilder text = new StringBuilder()
            .append("Weight Control weekly summary\n")
            .append(view.dateRange()).append("\n\n")
            .append("Hello, ").append(view.displayName()).append(".\n\n")
            .append("Routine completion: ").append(routines.value()).append("\n")
            .append(routines.completed()).append(" of ").append(routines.opportunities())
            .append(" daily routine opportunities completed\n")
            .append("vs last week: ").append(routines.previous().text()).append("\n")
            .append("vs 52 weeks ago: ").append(routines.yearAgo().text()).append("\n");
        routines.days().forEach(day -> text.append(day.label()).append(": ").append(day.value()).append(" · "));
        text.append("\n");
        if (routines.watchoutCount() > 0) {
            text.append("\nNeeds attention: ").append(routines.watchoutCount()).append(" routines below 60%\n");
            routines.watchouts().forEach(row -> text.append(row.name()).append(": ").append(row.value()).append("\n"));
            text.append("See all routine details: ").append(view.appUrl()).append("\n");
        }
        if (!summary.weeklyRoutines().isEmpty()) {
            text.append("\nWeekly routines · separate from daily completion\n");
            summary.weeklyRoutines().forEach(row -> text.append(row.name()).append(": ").append(row.value())
                .append(" · ").append(row.dates()).append("\n"));
        }
        text.append("\nYour week at a glance\n").append(summary.goalsLabel()).append("\n\n");
        summary.overview().forEach(card -> {
            text.append(card.label()).append(": ").append(card.value())
                .append("\n").append(card.status().label()).append(" · ").append(card.detail());
            if (!card.key().equals("weekendWeight") && !card.key().equals("bloodPressure")) {
                text.append("\nvs last week: ").append(card.previous().text())
                    .append("\nvs 52 weeks ago: ").append(card.yearAgo().text());
            }
            text.append("\n\n");
        });
        text.append("Workout activity\n")
            .append("This week: ").append(summary.periods().current())
            .append("\nLast week: ").append(summary.periods().previous())
            .append("\n52 weeks ago: ").append(summary.periods().yearAgo()).append("\n")
            .append("Mixed workouts count in each category; categories do not add up to total sessions.\n")
            .append("Changes show this week minus the comparison week.\n\n");
        summary.workouts().forEach(row -> text.append(row.label()).append(": ").append(row.current())
            .append("\nLast week: ").append(row.previous()).append(" · Change: ").append(row.previousDelta().text())
            .append("\n52 weeks ago: ").append(row.yearAgo()).append(" · Change: ").append(row.yearAgoDelta().text()).append("\n\n"));
        text.append("Nutrition · daily averages\n")
            .append("Available targets come from saved evidence. ≈ marks estimated values; partial coverage is shown beside the nutrient.\n\n");
        summary.nutrition().rows().forEach(row -> {
            text.append(row.label()).append(": ").append(row.current()).append(" · ").append(row.status().label())
                .append("\nSaved target: ").append(row.target()).append(" · ").append(row.coverage())
                .append("\nLast week: ").append(row.previous()).append(" · Change: ").append(row.previousDelta().text())
                .append(" · Target: ").append(row.previousTarget()).append("\n");
            text.append("\n");
        });
        text.append(summary.nutrition().note()).append("\n\n");
        if (summary.nutrition().hasYearAgo()) {
            text.append("See the available 52-week nutrient comparison in the full report: ").append(view.appUrl()).append("\n\n");
        }
        if (summary.reflection() != null) {
            text.append("Weekly reflection: ").append(summary.reflection().title()).append("\n")
                .append(summary.reflection().summary()).append("\n\n");
        }
        return text.append("Open this weekly summary: ").append(view.appUrl()).append("\n\n")
            .append("Full evidence and recommendations are in Weight Control.\n")
            .append("Missing days are not treated as zero.\n").toString();
    }
}
