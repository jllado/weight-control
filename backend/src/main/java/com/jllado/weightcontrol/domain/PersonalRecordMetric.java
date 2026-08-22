package com.jllado.weightcontrol.domain;

import java.util.Arrays;
import lombok.Getter;

@Getter
public enum PersonalRecordMetric {
    BODY_WEIGHT(PersonalRecordCatalogMetric.BODY_WEIGHT, PersonalRecordDirection.MINIMUM, "Lowest weight"),
    BODY_WEIGHT_MAXIMUM(PersonalRecordCatalogMetric.BODY_WEIGHT, PersonalRecordDirection.MAXIMUM, "Highest weight"),
    BODY_FAT_MASS(PersonalRecordCatalogMetric.BODY_FAT_MASS, PersonalRecordDirection.MINIMUM, "Lowest fat mass"),
    BODY_FAT_MASS_MAXIMUM(PersonalRecordCatalogMetric.BODY_FAT_MASS, PersonalRecordDirection.MAXIMUM, "Highest fat mass"),
    BODY_FAT_PERCENTAGE(PersonalRecordCatalogMetric.BODY_FAT_PERCENTAGE, PersonalRecordDirection.MINIMUM, "Lowest fat percentage"),
    BODY_FAT_PERCENTAGE_MAXIMUM(PersonalRecordCatalogMetric.BODY_FAT_PERCENTAGE, PersonalRecordDirection.MAXIMUM, "Highest fat percentage"),
    BODY_MUSCLE_MASS_MINIMUM(PersonalRecordCatalogMetric.BODY_MUSCLE_MASS, PersonalRecordDirection.MINIMUM, "Lowest muscle mass"),
    BODY_MUSCLE_MASS(PersonalRecordCatalogMetric.BODY_MUSCLE_MASS, PersonalRecordDirection.MAXIMUM, "Highest muscle mass"),
    BODY_MUSCLE_PERCENTAGE_MINIMUM(PersonalRecordCatalogMetric.BODY_MUSCLE_PERCENTAGE, PersonalRecordDirection.MINIMUM, "Lowest muscle percentage"),
    BODY_MUSCLE_PERCENTAGE(PersonalRecordCatalogMetric.BODY_MUSCLE_PERCENTAGE, PersonalRecordDirection.MAXIMUM, "Highest muscle percentage"),
    WORKOUT_HEAVIEST_LOAD_MINIMUM(PersonalRecordCatalogMetric.WORKOUT_HEAVIEST_LOAD, PersonalRecordDirection.MINIMUM, "Lightest load"),
    WORKOUT_HEAVIEST_LOAD(PersonalRecordCatalogMetric.WORKOUT_HEAVIEST_LOAD, PersonalRecordDirection.MAXIMUM, "Heaviest load"),
    WORKOUT_REPETITIONS_MINIMUM(PersonalRecordCatalogMetric.WORKOUT_REPETITIONS, PersonalRecordDirection.MINIMUM, "Fewest repetitions"),
    WORKOUT_REPETITIONS(PersonalRecordCatalogMetric.WORKOUT_REPETITIONS, PersonalRecordDirection.MAXIMUM, "Most repetitions"),
    WORKOUT_DURATION_MINIMUM(PersonalRecordCatalogMetric.WORKOUT_DURATION, PersonalRecordDirection.MINIMUM, "Shortest duration"),
    WORKOUT_DURATION(PersonalRecordCatalogMetric.WORKOUT_DURATION, PersonalRecordDirection.MAXIMUM, "Longest duration"),
    CARDIO_DURATION_MINIMUM(PersonalRecordCatalogMetric.CARDIO_DURATION, PersonalRecordDirection.MINIMUM, "Shortest interval"),
    CARDIO_DURATION(PersonalRecordCatalogMetric.CARDIO_DURATION, PersonalRecordDirection.MAXIMUM, "Longest interval"),
    CARDIO_SPEED_MINIMUM(PersonalRecordCatalogMetric.CARDIO_SPEED, PersonalRecordDirection.MINIMUM, "Lowest speed"),
    CARDIO_SPEED(PersonalRecordCatalogMetric.CARDIO_SPEED, PersonalRecordDirection.MAXIMUM, "Highest speed"),
    CARDIO_DISTANCE_MINIMUM(PersonalRecordCatalogMetric.CARDIO_DISTANCE, PersonalRecordDirection.MINIMUM, "Shortest distance"),
    CARDIO_DISTANCE(PersonalRecordCatalogMetric.CARDIO_DISTANCE, PersonalRecordDirection.MAXIMUM, "Longest distance"),
    CARDIO_INCLINE_MINIMUM(PersonalRecordCatalogMetric.CARDIO_INCLINE, PersonalRecordDirection.MINIMUM, "Lowest incline"),
    CARDIO_INCLINE(PersonalRecordCatalogMetric.CARDIO_INCLINE, PersonalRecordDirection.MAXIMUM, "Highest incline"),
    CARDIO_RESISTANCE_MINIMUM(PersonalRecordCatalogMetric.CARDIO_RESISTANCE, PersonalRecordDirection.MINIMUM, "Lowest resistance"),
    CARDIO_RESISTANCE(PersonalRecordCatalogMetric.CARDIO_RESISTANCE, PersonalRecordDirection.MAXIMUM, "Highest resistance"),
    BLOOD_PRESSURE_SYSTOLIC_MINIMUM(PersonalRecordCatalogMetric.BLOOD_PRESSURE_SYSTOLIC, PersonalRecordDirection.MINIMUM, "Lowest systolic pressure"),
    BLOOD_PRESSURE_SYSTOLIC_MAXIMUM(PersonalRecordCatalogMetric.BLOOD_PRESSURE_SYSTOLIC, PersonalRecordDirection.MAXIMUM, "Highest systolic pressure"),
    BLOOD_PRESSURE_DIASTOLIC_MINIMUM(PersonalRecordCatalogMetric.BLOOD_PRESSURE_DIASTOLIC, PersonalRecordDirection.MINIMUM, "Lowest diastolic pressure"),
    BLOOD_PRESSURE_DIASTOLIC_MAXIMUM(PersonalRecordCatalogMetric.BLOOD_PRESSURE_DIASTOLIC, PersonalRecordDirection.MAXIMUM, "Highest diastolic pressure"),
    LIPID_TOTAL_CHOLESTEROL_MINIMUM(PersonalRecordCatalogMetric.LIPID_TOTAL_CHOLESTEROL, PersonalRecordDirection.MINIMUM, "Lowest total cholesterol"),
    LIPID_TOTAL_CHOLESTEROL_MAXIMUM(PersonalRecordCatalogMetric.LIPID_TOTAL_CHOLESTEROL, PersonalRecordDirection.MAXIMUM, "Highest total cholesterol"),
    LIPID_HDL_MINIMUM(PersonalRecordCatalogMetric.LIPID_HDL, PersonalRecordDirection.MINIMUM, "Lowest HDL"),
    LIPID_HDL_MAXIMUM(PersonalRecordCatalogMetric.LIPID_HDL, PersonalRecordDirection.MAXIMUM, "Highest HDL"),
    LIPID_LDL_MINIMUM(PersonalRecordCatalogMetric.LIPID_LDL, PersonalRecordDirection.MINIMUM, "Lowest LDL"),
    LIPID_LDL_MAXIMUM(PersonalRecordCatalogMetric.LIPID_LDL, PersonalRecordDirection.MAXIMUM, "Highest LDL"),
    LIPID_TRIGLYCERIDES_MINIMUM(PersonalRecordCatalogMetric.LIPID_TRIGLYCERIDES, PersonalRecordDirection.MINIMUM, "Lowest triglycerides"),
    LIPID_TRIGLYCERIDES_MAXIMUM(PersonalRecordCatalogMetric.LIPID_TRIGLYCERIDES, PersonalRecordDirection.MAXIMUM, "Highest triglycerides"),
    MOOD_MINIMUM(PersonalRecordCatalogMetric.MOOD, PersonalRecordDirection.MINIMUM, "Lowest mood"),
    MOOD_MAXIMUM(PersonalRecordCatalogMetric.MOOD, PersonalRecordDirection.MAXIMUM, "Highest mood"),
    SLEEP_TOTAL_DURATION_MINIMUM(PersonalRecordCatalogMetric.SLEEP_TOTAL_DURATION, PersonalRecordDirection.MINIMUM, "Shortest total sleep"),
    SLEEP_TOTAL_DURATION_MAXIMUM(PersonalRecordCatalogMetric.SLEEP_TOTAL_DURATION, PersonalRecordDirection.MAXIMUM, "Longest total sleep"),
    SLEEP_DEEP_DURATION_MINIMUM(PersonalRecordCatalogMetric.SLEEP_DEEP_DURATION, PersonalRecordDirection.MINIMUM, "Shortest deep sleep"),
    SLEEP_DEEP_DURATION_MAXIMUM(PersonalRecordCatalogMetric.SLEEP_DEEP_DURATION, PersonalRecordDirection.MAXIMUM, "Longest deep sleep"),
    SLEEP_REM_DURATION_MINIMUM(PersonalRecordCatalogMetric.SLEEP_REM_DURATION, PersonalRecordDirection.MINIMUM, "Shortest REM sleep"),
    SLEEP_REM_DURATION_MAXIMUM(PersonalRecordCatalogMetric.SLEEP_REM_DURATION, PersonalRecordDirection.MAXIMUM, "Longest REM sleep"),
    SLEEP_LIGHT_DURATION_MINIMUM(PersonalRecordCatalogMetric.SLEEP_LIGHT_DURATION, PersonalRecordDirection.MINIMUM, "Shortest light sleep"),
    SLEEP_LIGHT_DURATION_MAXIMUM(PersonalRecordCatalogMetric.SLEEP_LIGHT_DURATION, PersonalRecordDirection.MAXIMUM, "Longest light sleep"),
    SLEEP_AWAKE_TIME_MINIMUM(PersonalRecordCatalogMetric.SLEEP_AWAKE_TIME, PersonalRecordDirection.MINIMUM, "Shortest awake time"),
    SLEEP_AWAKE_TIME_MAXIMUM(PersonalRecordCatalogMetric.SLEEP_AWAKE_TIME, PersonalRecordDirection.MAXIMUM, "Longest awake time"),
    SLEEP_AVERAGE_HEART_RATE_MINIMUM(PersonalRecordCatalogMetric.SLEEP_AVERAGE_HEART_RATE, PersonalRecordDirection.MINIMUM, "Lowest sleep heart rate"),
    SLEEP_AVERAGE_HEART_RATE_MAXIMUM(PersonalRecordCatalogMetric.SLEEP_AVERAGE_HEART_RATE, PersonalRecordDirection.MAXIMUM, "Highest sleep heart rate"),
    SLEEP_AVERAGE_HRV_MINIMUM(PersonalRecordCatalogMetric.SLEEP_AVERAGE_HRV, PersonalRecordDirection.MINIMUM, "Lowest HRV"),
    SLEEP_AVERAGE_HRV_MAXIMUM(PersonalRecordCatalogMetric.SLEEP_AVERAGE_HRV, PersonalRecordDirection.MAXIMUM, "Highest HRV"),
    MEAL_CALORIES_MINIMUM(PersonalRecordCatalogMetric.MEAL_CALORIES, PersonalRecordDirection.MINIMUM, "Lowest meal calories"),
    MEAL_CALORIES_MAXIMUM(PersonalRecordCatalogMetric.MEAL_CALORIES, PersonalRecordDirection.MAXIMUM, "Highest meal calories"),
    MEAL_PROTEIN_MINIMUM(PersonalRecordCatalogMetric.MEAL_PROTEIN, PersonalRecordDirection.MINIMUM, "Lowest meal protein"),
    MEAL_PROTEIN_MAXIMUM(PersonalRecordCatalogMetric.MEAL_PROTEIN, PersonalRecordDirection.MAXIMUM, "Highest meal protein"),
    MEAL_CARBOHYDRATES_MINIMUM(PersonalRecordCatalogMetric.MEAL_CARBOHYDRATES, PersonalRecordDirection.MINIMUM, "Lowest meal carbohydrates"),
    MEAL_CARBOHYDRATES_MAXIMUM(PersonalRecordCatalogMetric.MEAL_CARBOHYDRATES, PersonalRecordDirection.MAXIMUM, "Highest meal carbohydrates"),
    MEAL_FAT_MINIMUM(PersonalRecordCatalogMetric.MEAL_FAT, PersonalRecordDirection.MINIMUM, "Lowest meal fat"),
    MEAL_FAT_MAXIMUM(PersonalRecordCatalogMetric.MEAL_FAT, PersonalRecordDirection.MAXIMUM, "Highest meal fat"),
    DAILY_CALORIES_MINIMUM(PersonalRecordCatalogMetric.DAILY_CALORIES, PersonalRecordDirection.MINIMUM, "Lowest daily calories"),
    DAILY_CALORIES_MAXIMUM(PersonalRecordCatalogMetric.DAILY_CALORIES, PersonalRecordDirection.MAXIMUM, "Highest daily calories"),
    DAILY_PROTEIN_MINIMUM(PersonalRecordCatalogMetric.DAILY_PROTEIN, PersonalRecordDirection.MINIMUM, "Lowest daily protein"),
    DAILY_PROTEIN_MAXIMUM(PersonalRecordCatalogMetric.DAILY_PROTEIN, PersonalRecordDirection.MAXIMUM, "Highest daily protein"),
    DAILY_CARBOHYDRATES_MINIMUM(PersonalRecordCatalogMetric.DAILY_CARBOHYDRATES, PersonalRecordDirection.MINIMUM, "Lowest daily carbohydrates"),
    DAILY_CARBOHYDRATES_MAXIMUM(PersonalRecordCatalogMetric.DAILY_CARBOHYDRATES, PersonalRecordDirection.MAXIMUM, "Highest daily carbohydrates"),
    DAILY_FAT_MINIMUM(PersonalRecordCatalogMetric.DAILY_FAT, PersonalRecordDirection.MINIMUM, "Lowest daily fat"),
    DAILY_FAT_MAXIMUM(PersonalRecordCatalogMetric.DAILY_FAT, PersonalRecordDirection.MAXIMUM, "Highest daily fat"),
    HABIT_COMPLETION_TOTAL_MINIMUM(PersonalRecordCatalogMetric.HABIT_COMPLETION_TOTAL, PersonalRecordDirection.MINIMUM, "Fewest habit completions"),
    HABIT_COMPLETION_TOTAL_MAXIMUM(PersonalRecordCatalogMetric.HABIT_COMPLETION_TOTAL, PersonalRecordDirection.MAXIMUM, "Most habit completions"),
    HABIT_CURRENT_STREAK_MINIMUM(PersonalRecordCatalogMetric.HABIT_CURRENT_STREAK, PersonalRecordDirection.MINIMUM, "Shortest habit current streak"),
    HABIT_CURRENT_STREAK_MAXIMUM(PersonalRecordCatalogMetric.HABIT_CURRENT_STREAK, PersonalRecordDirection.MAXIMUM, "Longest habit current streak"),
    HABIT_BEST_STREAK_MINIMUM(PersonalRecordCatalogMetric.HABIT_BEST_STREAK, PersonalRecordDirection.MINIMUM, "Lowest habit best streak"),
    HABIT_BEST_STREAK_MAXIMUM(PersonalRecordCatalogMetric.HABIT_BEST_STREAK, PersonalRecordDirection.MAXIMUM, "Highest habit best streak"),
    ROUTINE_COMPLETION_TOTAL_MINIMUM(PersonalRecordCatalogMetric.ROUTINE_COMPLETION_TOTAL, PersonalRecordDirection.MINIMUM, "Fewest routine completions"),
    ROUTINE_COMPLETION_TOTAL_MAXIMUM(PersonalRecordCatalogMetric.ROUTINE_COMPLETION_TOTAL, PersonalRecordDirection.MAXIMUM, "Most routine completions"),
    ROUTINE_CURRENT_STREAK_MINIMUM(PersonalRecordCatalogMetric.ROUTINE_CURRENT_STREAK, PersonalRecordDirection.MINIMUM, "Shortest routine current streak"),
    ROUTINE_CURRENT_STREAK_MAXIMUM(PersonalRecordCatalogMetric.ROUTINE_CURRENT_STREAK, PersonalRecordDirection.MAXIMUM, "Longest routine current streak"),
    ROUTINE_BEST_STREAK_MINIMUM(PersonalRecordCatalogMetric.ROUTINE_BEST_STREAK, PersonalRecordDirection.MINIMUM, "Lowest routine best streak"),
    ROUTINE_BEST_STREAK_MAXIMUM(PersonalRecordCatalogMetric.ROUTINE_BEST_STREAK, PersonalRecordDirection.MAXIMUM, "Highest routine best streak"),
    DECISION_TOTAL_MINIMUM(PersonalRecordCatalogMetric.DECISION_TOTAL, PersonalRecordDirection.MINIMUM, "Fewest decisions"),
    DECISION_TOTAL_MAXIMUM(PersonalRecordCatalogMetric.DECISION_TOTAL, PersonalRecordDirection.MAXIMUM, "Most decisions"),
    DECISION_WIN_RATE_MINIMUM(PersonalRecordCatalogMetric.DECISION_WIN_RATE, PersonalRecordDirection.MINIMUM, "Lowest decision WIN rate"),
    DECISION_WIN_RATE_MAXIMUM(PersonalRecordCatalogMetric.DECISION_WIN_RATE, PersonalRecordDirection.MAXIMUM, "Highest decision WIN rate"),
    DECISION_WIN_STREAK_MINIMUM(PersonalRecordCatalogMetric.DECISION_WIN_STREAK, PersonalRecordDirection.MINIMUM, "Shortest decision WIN streak"),
    DECISION_WIN_STREAK_MAXIMUM(PersonalRecordCatalogMetric.DECISION_WIN_STREAK, PersonalRecordDirection.MAXIMUM, "Longest decision WIN streak");

    private final PersonalRecordCatalogMetric catalogMetric;
    private final PersonalRecordDirection direction;
    private final String label;

    PersonalRecordMetric(PersonalRecordCatalogMetric catalogMetric, PersonalRecordDirection direction, String label) {
        this.catalogMetric = catalogMetric;
        this.direction = direction;
        this.label = label;
    }

    public PersonalRecordDomain getDomain() { return catalogMetric.getDomain(); }
    public PersonalRecordUnit getUnit() { return catalogMetric.getUnit(); }
    public String getSubjectLabel() { return catalogMetric.getSubjectLabel(); }

    public static PersonalRecordMetric forDirection(PersonalRecordCatalogMetric catalogMetric, PersonalRecordDirection direction) {
        return Arrays.stream(values()).filter(metric -> metric.catalogMetric == catalogMetric && metric.direction == direction).findFirst().orElseThrow();
    }
}
