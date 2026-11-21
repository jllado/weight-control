package com.jllado.weightcontrol.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.DayOfWeek;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "weekly_calorie_plans", uniqueConstraints = @UniqueConstraint(name = "uq_weekly_calorie_plans_user_week", columnNames = {"user_id", "week_start"}))
@Getter
@Setter
public class WeeklyCaloriePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "saturday_calories")
    private Integer saturdayCalories;
    @Column(name = "sunday_calories")
    private Integer sundayCalories;
    @Column(name = "monday_calories")
    private Integer mondayCalories;
    @Column(name = "tuesday_calories")
    private Integer tuesdayCalories;
    @Column(name = "wednesday_calories")
    private Integer wednesdayCalories;
    @Column(name = "thursday_calories")
    private Integer thursdayCalories;
    @Column(name = "friday_calories")
    private Integer fridayCalories;

    @jakarta.persistence.Version
    @Column(nullable = false)
    private Long revision;

    public Integer caloriesOn(DayOfWeek day) {
        return switch (day) {
            case SATURDAY -> saturdayCalories;
            case SUNDAY -> sundayCalories;
            case MONDAY -> mondayCalories;
            case TUESDAY -> tuesdayCalories;
            case WEDNESDAY -> wednesdayCalories;
            case THURSDAY -> thursdayCalories;
            case FRIDAY -> fridayCalories;
        };
    }

    public void setCaloriesOn(DayOfWeek day, int calories) {
        switch (day) {
            case SATURDAY -> saturdayCalories = calories;
            case SUNDAY -> sundayCalories = calories;
            case MONDAY -> mondayCalories = calories;
            case TUESDAY -> tuesdayCalories = calories;
            case WEDNESDAY -> wednesdayCalories = calories;
            case THURSDAY -> thursdayCalories = calories;
            case FRIDAY -> fridayCalories = calories;
        }
    }
}
