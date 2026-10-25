package com.jllado.weightcontrol.domain;

import java.time.LocalDate;
import java.util.List;

public record WarningReviewSnapshot(LocalDate reviewedDate, List<WarningReviewItem> items) {}
