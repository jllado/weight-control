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
    List<RecordView> records,
    List<RoutineWatchout> routineWatchouts,
    List<CardRow> cardRows,
    List<String> warnings,
    OutcomeView outcomes,
    WeeklyReflectionView weeklyReflection,
    String appUrl,
    WeeklySummaryPresentationMapper.Presentation presentation,
    VisualSummary visualSummary
) {

    public record VisualSummary(PeriodHeaders periods, RoutineHero routines, List<OverviewCard> overview,
        List<WorkoutRow> workouts, NutritionTable nutrition, List<WeeklyRoutineView> weeklyRoutines,
        ReflectionTeaser reflection, String goalsLabel) { }
    public record PeriodHeaders(String current, String previous, String yearAgo) { }
    public record Status(String label, String color) { }
    public record Delta(String text, String color) { }
    public record RoutineHero(String value, int completed, int opportunities, Delta previous, Delta yearAgo,
        List<DayBar> days, List<RoutineAttention> watchouts, int watchoutCount) { }
    public record DayBar(String label, String value, int width, Status status) { }
    public record RoutineAttention(String name, String value) { }
    public record OverviewCard(String key, String label, String value, String detail, Status status, Delta previous, Delta yearAgo) { }
    public record WorkoutRow(String label, String current, String previous, Delta previousDelta, String yearAgo, Delta yearAgoDelta) { }
    public record NutritionTable(List<NutrientRow> rows, boolean hasYearAgo, String note) { }
    public record NutrientRow(String key, String label, String current, String target, Status status, String previous,
        String previousTarget, Delta previousDelta, String yearAgo, String yearAgoTarget, Delta yearAgoDelta, String coverage) { }
    public record WeeklyRoutineView(String name, String value, String dates) { }
    public record ReflectionTeaser(String title, String summary) { }

    public String actionUrl(String sourceKey) {
        return java.net.URI.create(appUrl).resolve("/actions?source=" + java.net.URLEncoder.encode(sourceKey, java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    public record DayView(String label, String value, String cssClass) {
    }

    public record RecordView(String label, String value, String date) {
    }

    public record RoutineWatchout(String name, int completedDays, int eligibleDays, String percentage) {
    }

    public record CardRow(MetricCard left, MetricCard right, String section) {
        public CardRow(MetricCard left, MetricCard right) {
            this(left, right, null);
        }
    }

    public record MetricCard(String label, String value, String detail, Comparison previousComparison, Comparison yearAgoComparison) {
    }

    public record WeeklyReflectionView(String title, String summary, List<ReflectionSectionView> sections, List<String> nextWeekActions) {
    }

    public record ReflectionSectionView(String title, String summary, String nextAction) {
    }

    public record OutcomeView(String weight, String bloodPressure) {
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
