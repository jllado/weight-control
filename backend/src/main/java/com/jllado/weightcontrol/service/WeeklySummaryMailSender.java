package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.HistoryEventResponse;
import com.jllado.weightcontrol.domain.User;
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
        StringBuilder text = new StringBuilder()
            .append("Weight Control weekly summary\n")
            .append(view.dateRange()).append("\n\n")
            .append("Hello, ").append(view.displayName()).append(".\n\n")
            .append("Routine completion: ").append(view.headlineValue()).append("\n")
            .append(view.headlineDetail()).append("\n")
            .append(view.previousRoutineComparison().displayText()).append("\n")
            .append(view.yearAgoRoutineComparison().displayText()).append("\n\n");
        text.append("New records\n");
        if (view.records().isEmpty()) {
            text.append("No new records this week\n\n");
        } else {
            view.records().forEach(record -> text.append(record.label()).append(": ").append(record.value()).append(" · ").append(record.date()).append("\n"));
            text.append("\n");
        }
        for (WeeklySummaryEmailView.CardRow row : view.cardRows()) {
            appendCard(text, row.left());
            if (row.right() != null) {
                appendCard(text, row.right());
            }
        }
        return text.append("Open Weight Control: ").append(view.appUrl()).append("\n\n")
            .append("Missing days are not treated as zero.\n")
            .toString();
    }

    private void appendCard(StringBuilder text, WeeklySummaryEmailView.MetricCard card) {
        text.append(card.label()).append(": ").append(card.value()).append("\n")
            .append(card.detail()).append("\n")
            .append(card.previousComparison().displayText()).append("\n")
            .append(card.yearAgoComparison().displayText()).append("\n\n");
    }
}
