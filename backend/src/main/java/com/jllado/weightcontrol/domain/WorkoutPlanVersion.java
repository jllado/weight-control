package com.jllado.weightcontrol.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;

@Embeddable
@Getter
public class WorkoutPlanVersion {
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "review_date", nullable = false) private LocalDate reviewDate;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Convert(converter = WorkoutPlanDaysJsonConverter.class)
    @Column(name = "days_json", nullable = false, columnDefinition = "longtext") private List<WorkoutPlanDay> days;

    protected WorkoutPlanVersion() { }

    WorkoutPlanVersion(LocalDate startDate, LocalDate reviewDate, Instant updatedAt, List<WorkoutPlanDay> days) {
        this.startDate = startDate;
        this.reviewDate = reviewDate;
        this.updatedAt = updatedAt;
        this.days = days;
    }

    public WorkoutPlanDay dayOn(LocalDate date) {
        if (startDate.isAfter(date)) return null;
        return days.stream().filter(day -> day.day() == date.getDayOfWeek()).findFirst().orElseThrow();
    }
}
