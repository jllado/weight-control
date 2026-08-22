package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.Habit;
import com.jllado.weightcontrol.domain.HabitBaseline;
import com.jllado.weightcontrol.domain.HabitCheckin;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;

public final class HabitDtos {

    private HabitDtos() {
    }

    public record HabitRequest(@NotBlank String name, @NotNull Integer duration) {
    }

    public record HabitResponse(
        Long id,
        String startDateFormat,
        OffsetDateTime startDate,
        Integer duration,
        String lastTimeDateFormat,
        OffsetDateTime lastTimeDate,
        String name,
        Integer times,
        Integer currentStrike,
        Integer bestStrike,
        List<LocalDate> checkins,
        HabitBaselineResponse legacyBaseline
    ) {
        public static HabitResponse from(Habit habit) {
            return from(habit, List.of(), null);
        }

        public static HabitResponse from(Habit habit, List<HabitCheckin> checkins, HabitBaseline baseline) {
            return new HabitResponse(
                habit.getId(),
                DateTimes.formatDate(habit.getStartDate()),
                habit.getStartDate(),
                habit.getDuration(),
                habit.getLastTimeDate() == null ? null : DateTimes.formatDate(habit.getLastTimeDate()),
                habit.getLastTimeDate(),
                habit.getName(),
                habit.getTimes(),
                habit.getCurrentStrike(),
                habit.getBestStrike(),
                checkins.stream().map(HabitCheckin::getCheckinDate).toList(),
                baseline == null ? null : HabitBaselineResponse.from(baseline)
            );
        }
    }

    public record HabitBaselineResponse(Integer completionTotal, Integer currentStreak, Integer bestStreak, LocalDate lastDate) {
        public static HabitBaselineResponse from(HabitBaseline baseline) {
            return new HabitBaselineResponse(baseline.getCompletionTotal(), baseline.getCurrentStreak(), baseline.getBestStreak(), baseline.getLastDate());
        }
    }
}
