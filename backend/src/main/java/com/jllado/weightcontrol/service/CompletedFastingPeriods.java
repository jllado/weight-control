package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.FastingPeriod;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class CompletedFastingPeriods {

    private CompletedFastingPeriods() {
    }

    static List<MergedPeriod> merge(List<FastingPeriod> sourcePeriods) {
        List<FastingPeriod> periods = sourcePeriods.stream()
            .filter(period -> period.getEndTime() != null)
            .sorted(Comparator.comparing(FastingPeriod::getStartTime))
            .toList();
        List<MergedPeriod> merged = new ArrayList<>();
        for (FastingPeriod period : periods) {
            MergedPeriod previous = merged.isEmpty() ? null : merged.getLast();
            if (previous != null && !period.getStartTime().isAfter(previous.endTime())) {
                previous.add(period);
            } else {
                merged.add(new MergedPeriod(period));
            }
        }
        return List.copyOf(merged);
    }

    static final class MergedPeriod {
        private final OffsetDateTime startTime;
        private OffsetDateTime endTime;
        private final List<FastingPeriod> contributors = new ArrayList<>();

        private MergedPeriod(FastingPeriod period) {
            startTime = period.getStartTime();
            endTime = period.getEndTime();
            contributors.add(period);
        }

        private void add(FastingPeriod period) {
            contributors.add(period);
            if (period.getEndTime().isAfter(endTime)) {
                endTime = period.getEndTime();
            }
        }

        OffsetDateTime startTime() {
            return startTime;
        }

        OffsetDateTime endTime() {
            return endTime;
        }

        List<FastingPeriod> contributors() {
            return List.copyOf(contributors);
        }
    }
}
