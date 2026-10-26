package com.jllado.weightcontrol.domain;

import java.time.LocalDate;

public record WarningProgress(
    WarningDirection direction,
    String assessment,
    String evidence,
    String nextAction,
    String resolutionCriteria,
    String criteriaProgress,
    LocalDate recentStart,
    LocalDate recentEnd,
    LocalDate priorStart,
    LocalDate priorEnd,
    String criteriaChangeReason,
    String resolutionRationale,
    LocalDate reviewedDate,
    long sourceVersion,
    boolean initialReview,
    WarningMetricReview metricsReview,
    WarningGoalStatus goalStatus,
    java.time.Instant reviewedAt
) {}
