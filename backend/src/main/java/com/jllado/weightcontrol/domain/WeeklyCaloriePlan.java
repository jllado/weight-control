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
import java.util.List;
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

    @Column(name = "saturday_calories", nullable = false)
    private int saturdayCalories;
    @Column(name = "sunday_calories", nullable = false)
    private int sundayCalories;
    @Column(name = "monday_calories", nullable = false)
    private int mondayCalories;
    @Column(name = "tuesday_calories", nullable = false)
    private int tuesdayCalories;
    @Column(name = "wednesday_calories", nullable = false)
    private int wednesdayCalories;
    @Column(name = "thursday_calories", nullable = false)
    private int thursdayCalories;
    @Column(name = "friday_calories", nullable = false)
    private int fridayCalories;

    @jakarta.persistence.Version
    @Column(nullable = false)
    private Long revision;

    public List<Integer> getCalories() {
        return List.of(saturdayCalories, sundayCalories, mondayCalories, tuesdayCalories, wednesdayCalories, thursdayCalories, fridayCalories);
    }

    public void setCalories(List<Integer> calories) {
        saturdayCalories = calories.get(0);
        sundayCalories = calories.get(1);
        mondayCalories = calories.get(2);
        tuesdayCalories = calories.get(3);
        wednesdayCalories = calories.get(4);
        thursdayCalories = calories.get(5);
        fridayCalories = calories.get(6);
    }
}
