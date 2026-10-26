package com.jllado.weightcontrol.domain;

public record WarningReviewItem(
    Long id,
    CoachWarningType type,
    CoachWarningStatus status,
    long version,
    WarningProgress progress,
    java.time.Instant createdAt
) {}
