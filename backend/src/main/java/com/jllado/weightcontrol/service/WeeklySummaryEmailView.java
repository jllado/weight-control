package com.jllado.weightcontrol.service;

import java.util.List;

public record WeeklySummaryEmailView(
    String subject,
    String displayName,
    String dateRange,
    String headlineValue,
    String headlineDetail,
    Comparison previousRoutineComparison,
    Comparison yearAgoRoutineComparison,
    List<DayView> days,
    List<CardRow> cardRows,
    String appUrl
) {

    public record DayView(String label, String value) {
    }

    public record CardRow(MetricCard left, MetricCard right) {
    }

    public record MetricCard(String label, String value, String detail, Comparison previousComparison, Comparison yearAgoComparison) {
    }

    public record Comparison(String text, ComparisonStatus status) {

        public String displayText() {
            return status.symbol().isEmpty() ? text : status.symbol() + " " + text;
        }

        public String cssClass() {
            return status.cssClass();
        }
    }

    public enum ComparisonStatus {
        IMPROVED("↑", "comparison--improved"),
        WORSENED("↓", "comparison--worsened"),
        UNCHANGED("→", "comparison--unchanged"),
        UNKNOWN("", "comparison--unknown");

        private final String symbol;
        private final String cssClass;

        ComparisonStatus(String symbol, String cssClass) {
            this.symbol = symbol;
            this.cssClass = cssClass;
        }

        public String symbol() {
            return symbol;
        }

        public String cssClass() {
            return cssClass;
        }
    }
}
