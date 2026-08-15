package com.jllado.weightcontrol.service;

import java.util.List;

public record WeeklySummaryEmailView(
    String subject,
    String displayName,
    String dateRange,
    String headlineValue,
    String headlineDetail,
    String previousRoutineComparison,
    String yearAgoRoutineComparison,
    List<DayView> days,
    List<CardRow> cardRows,
    String appUrl
) {

    public record DayView(String label, String value) {
    }

    public record CardRow(MetricCard left, MetricCard right) {
    }

    public record MetricCard(String label, String value, String detail, String previousComparison, String yearAgoComparison) {
    }
}
