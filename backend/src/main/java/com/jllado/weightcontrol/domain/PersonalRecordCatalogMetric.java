package com.jllado.weightcontrol.domain;

import lombok.Getter;

@Getter
public enum PersonalRecordCatalogMetric {
    BODY_WEIGHT(PersonalRecordDomain.BODY, PersonalRecordUnit.KG, 2, PersonalRecordMode.MINIMUM, "Body weight", "Body"),
    BODY_FAT_MASS(PersonalRecordDomain.BODY, PersonalRecordUnit.KG, 2, PersonalRecordMode.MINIMUM, "Fat mass", "Body"),
    BODY_FAT_PERCENTAGE(PersonalRecordDomain.BODY, PersonalRecordUnit.PERCENT, 2, PersonalRecordMode.MINIMUM, "Fat percentage", "Body"),
    BODY_MUSCLE_MASS(PersonalRecordDomain.BODY, PersonalRecordUnit.KG, 2, PersonalRecordMode.MAXIMUM, "Muscle mass", "Body"),
    BODY_MUSCLE_PERCENTAGE(PersonalRecordDomain.BODY, PersonalRecordUnit.PERCENT, 2, PersonalRecordMode.MAXIMUM, "Muscle percentage", "Body"),
    WORKOUT_HEAVIEST_LOAD(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.KG, 2, PersonalRecordMode.MAXIMUM, "Exercise load", null),
    WORKOUT_REPETITIONS(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.REPETITIONS, 0, PersonalRecordMode.MAXIMUM, "Repetitions", null),
    WORKOUT_DURATION(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.SECONDS, 0, PersonalRecordMode.MAXIMUM, "Exercise duration", null),
    CARDIO_DURATION(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.SECONDS, 0, PersonalRecordMode.MAXIMUM, "Cardio interval duration", null),
    CARDIO_SPEED(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.KM_PER_HOUR, 2, PersonalRecordMode.MAXIMUM, "Cardio speed", null),
    CARDIO_DISTANCE(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.KM, 2, PersonalRecordMode.MAXIMUM, "Cardio distance", null),
    CARDIO_INCLINE(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.PERCENT, 2, PersonalRecordMode.MAXIMUM, "Cardio incline", null),
    CARDIO_RESISTANCE(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.LEVEL, 0, PersonalRecordMode.MAXIMUM, "Cardio resistance", null),
    BLOOD_PRESSURE_SYSTOLIC(PersonalRecordDomain.VITALS, PersonalRecordUnit.MM_HG, 0, PersonalRecordMode.BOTH, "Systolic pressure", "Blood pressure"),
    BLOOD_PRESSURE_DIASTOLIC(PersonalRecordDomain.VITALS, PersonalRecordUnit.MM_HG, 0, PersonalRecordMode.BOTH, "Diastolic pressure", "Blood pressure"),
    LIPID_TOTAL_CHOLESTEROL(PersonalRecordDomain.VITALS, PersonalRecordUnit.MG_PER_DL, 0, PersonalRecordMode.MINIMUM, "Total cholesterol", "Lipids"),
    LIPID_HDL(PersonalRecordDomain.VITALS, PersonalRecordUnit.MG_PER_DL, 0, PersonalRecordMode.MAXIMUM, "HDL", "Lipids"),
    LIPID_LDL(PersonalRecordDomain.VITALS, PersonalRecordUnit.MG_PER_DL, 0, PersonalRecordMode.MINIMUM, "LDL", "Lipids"),
    LIPID_TRIGLYCERIDES(PersonalRecordDomain.VITALS, PersonalRecordUnit.MG_PER_DL, 0, PersonalRecordMode.MINIMUM, "Triglycerides", "Lipids"),
    MOOD(PersonalRecordDomain.RECOVERY, PersonalRecordUnit.SCORE_OUT_OF_FIVE, 0, PersonalRecordMode.MAXIMUM, "Mood", "Mood"),
    SLEEP_TOTAL_DURATION(PersonalRecordDomain.RECOVERY, PersonalRecordUnit.SECONDS, 0, PersonalRecordMode.MAXIMUM, "Total sleep", "Sleep"),
    SLEEP_DEEP_DURATION(PersonalRecordDomain.RECOVERY, PersonalRecordUnit.SECONDS, 0, PersonalRecordMode.MAXIMUM, "Deep sleep", "Sleep"),
    SLEEP_REM_DURATION(PersonalRecordDomain.RECOVERY, PersonalRecordUnit.SECONDS, 0, PersonalRecordMode.MAXIMUM, "REM sleep", "Sleep"),
    SLEEP_LIGHT_DURATION(PersonalRecordDomain.RECOVERY, PersonalRecordUnit.SECONDS, 0, PersonalRecordMode.MAXIMUM, "Light sleep", "Sleep"),
    SLEEP_AWAKE_TIME(PersonalRecordDomain.RECOVERY, PersonalRecordUnit.SECONDS, 0, PersonalRecordMode.MINIMUM, "Awake time", "Sleep"),
    SLEEP_AVERAGE_HEART_RATE(PersonalRecordDomain.RECOVERY, PersonalRecordUnit.BPM, 2, PersonalRecordMode.MINIMUM, "Sleep heart rate", "Sleep"),
    SLEEP_AVERAGE_HRV(PersonalRecordDomain.RECOVERY, PersonalRecordUnit.MILLISECONDS, 0, PersonalRecordMode.MAXIMUM, "HRV", "Sleep"),
    MEAL_CALORIES(PersonalRecordDomain.NUTRITION, PersonalRecordUnit.KCAL, 0, PersonalRecordMode.BOTH, "Meal calories", "Meals"),
    MEAL_PROTEIN(PersonalRecordDomain.NUTRITION, PersonalRecordUnit.GRAMS, 2, PersonalRecordMode.BOTH, "Meal protein", "Meals"),
    MEAL_CARBOHYDRATES(PersonalRecordDomain.NUTRITION, PersonalRecordUnit.GRAMS, 2, PersonalRecordMode.BOTH, "Meal carbohydrates", "Meals"),
    MEAL_FAT(PersonalRecordDomain.NUTRITION, PersonalRecordUnit.GRAMS, 2, PersonalRecordMode.BOTH, "Meal fat", "Meals"),
    DAILY_CALORIES(PersonalRecordDomain.NUTRITION, PersonalRecordUnit.KCAL, 0, PersonalRecordMode.BOTH, "Daily calories", "Daily nutrition"),
    DAILY_PROTEIN(PersonalRecordDomain.NUTRITION, PersonalRecordUnit.GRAMS, 2, PersonalRecordMode.BOTH, "Daily protein", "Daily nutrition"),
    DAILY_CARBOHYDRATES(PersonalRecordDomain.NUTRITION, PersonalRecordUnit.GRAMS, 2, PersonalRecordMode.BOTH, "Daily carbohydrates", "Daily nutrition"),
    DAILY_FAT(PersonalRecordDomain.NUTRITION, PersonalRecordUnit.GRAMS, 2, PersonalRecordMode.BOTH, "Daily fat", "Daily nutrition"),
    HABIT_COMPLETION_TOTAL(PersonalRecordDomain.BEHAVIOR, PersonalRecordUnit.COMPLETIONS, 0, PersonalRecordMode.MAXIMUM, "Habit completions", null),
    HABIT_CURRENT_STREAK(PersonalRecordDomain.BEHAVIOR, PersonalRecordUnit.DAYS, 0, PersonalRecordMode.MAXIMUM, "Habit current streak", null),
    HABIT_BEST_STREAK(PersonalRecordDomain.BEHAVIOR, PersonalRecordUnit.DAYS, 0, PersonalRecordMode.MAXIMUM, "Habit best streak", null),
    ROUTINE_COMPLETION_TOTAL(PersonalRecordDomain.BEHAVIOR, PersonalRecordUnit.COMPLETIONS, 0, PersonalRecordMode.MAXIMUM, "Routine completions", null),
    ROUTINE_CURRENT_STREAK(PersonalRecordDomain.BEHAVIOR, PersonalRecordUnit.DAYS, 0, PersonalRecordMode.MAXIMUM, "Routine current streak", null),
    ROUTINE_BEST_STREAK(PersonalRecordDomain.BEHAVIOR, PersonalRecordUnit.DAYS, 0, PersonalRecordMode.MAXIMUM, "Routine best streak", null),
    DECISION_TOTAL(PersonalRecordDomain.BEHAVIOR, PersonalRecordUnit.DECISIONS, 0, PersonalRecordMode.MAXIMUM, "Decision total", "Decisions"),
    DECISION_WIN_RATE(PersonalRecordDomain.BEHAVIOR, PersonalRecordUnit.PERCENT, 2, PersonalRecordMode.MAXIMUM, "Decision WIN rate", "Decisions"),
    DECISION_WIN_STREAK(PersonalRecordDomain.BEHAVIOR, PersonalRecordUnit.DECISIONS, 0, PersonalRecordMode.MAXIMUM, "Decision WIN streak", "Decisions"),
    BODY_BMI(PersonalRecordDomain.BODY, PersonalRecordUnit.KG_PER_SQUARE_METER, 2, PersonalRecordMode.MINIMUM, "BMI", "Body"),
    WORKOUT_SESSION_COUNT(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.COUNT, 0, PersonalRecordMode.MAXIMUM, "Workout count", null),
    WORKOUT_SET_COUNT(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.COUNT, 0, PersonalRecordMode.MAXIMUM, "Set count", null),
    WORKOUT_INTERVAL_COUNT(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.COUNT, 0, PersonalRecordMode.MAXIMUM, "Interval count", null),
    WORKOUT_STRENGTH_VOLUME(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.KG_REPETITIONS, 2, PersonalRecordMode.MAXIMUM, "Strength volume", null),
    WORKOUT_CALORIES(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.KCAL, 0, PersonalRecordMode.MAXIMUM, "Workout calories", null),
    WORKOUT_AVERAGE_HEART_RATE(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.BPM, 2, PersonalRecordMode.BOTH, "Workout heart rate", null),
    DASHBOARD_TOTAL_COUNT(PersonalRecordDomain.BEHAVIOR, PersonalRecordUnit.COUNT, 0, PersonalRecordMode.MAXIMUM, "Dashboard opportunities", null),
    DASHBOARD_COMPLETED_COUNT(PersonalRecordDomain.BEHAVIOR, PersonalRecordUnit.COUNT, 0, PersonalRecordMode.MAXIMUM, "Dashboard completions", null),
    DASHBOARD_COMPLETION_PERCENTAGE(PersonalRecordDomain.BEHAVIOR, PersonalRecordUnit.PERCENT, 2, PersonalRecordMode.MAXIMUM, "Dashboard completion", null),
    DASHBOARD_SCORE(PersonalRecordDomain.BEHAVIOR, PersonalRecordUnit.SCORE, 2, PersonalRecordMode.MAXIMUM, "Dashboard score", null),
    DASHBOARD_STATUS(PersonalRecordDomain.BEHAVIOR, PersonalRecordUnit.PERCENT, 2, PersonalRecordMode.MAXIMUM, "Dashboard status", null),
    CHANGE_KG(PersonalRecordDomain.BODY, PersonalRecordUnit.KG, 2, PersonalRecordMode.BOTH, "Mass change", null),
    CHANGE_PERCENT(PersonalRecordDomain.BODY, PersonalRecordUnit.PERCENT, 2, PersonalRecordMode.BOTH, "Percentage change", null),
    CHANGE_MM_HG(PersonalRecordDomain.VITALS, PersonalRecordUnit.MM_HG, 2, PersonalRecordMode.BOTH, "Pressure change", null),
    CHANGE_MG_PER_DL(PersonalRecordDomain.VITALS, PersonalRecordUnit.MG_PER_DL, 2, PersonalRecordMode.BOTH, "Lipid change", null),
    CHANGE_SECONDS(PersonalRecordDomain.RECOVERY, PersonalRecordUnit.SECONDS, 2, PersonalRecordMode.BOTH, "Duration change", null),
    CHANGE_BPM(PersonalRecordDomain.RECOVERY, PersonalRecordUnit.BPM, 2, PersonalRecordMode.BOTH, "Heart-rate change", null),
    CHANGE_MILLISECONDS(PersonalRecordDomain.RECOVERY, PersonalRecordUnit.MILLISECONDS, 2, PersonalRecordMode.BOTH, "HRV change", null),
    RECOVERY_CHANGE_SCORE(PersonalRecordDomain.RECOVERY, PersonalRecordUnit.SCORE, 2, PersonalRecordMode.BOTH, "Recovery score change", null),
    CHANGE_KCAL(PersonalRecordDomain.NUTRITION, PersonalRecordUnit.KCAL, 2, PersonalRecordMode.BOTH, "Calorie change", null),
    CHANGE_SCORE(PersonalRecordDomain.BEHAVIOR, PersonalRecordUnit.SCORE, 2, PersonalRecordMode.BOTH, "Score change", null),
    BEHAVIOR_CHANGE_PERCENT(PersonalRecordDomain.BEHAVIOR, PersonalRecordUnit.PERCENT, 2, PersonalRecordMode.BOTH, "Behavior percentage change", null),
    WORKOUT_CHANGE_COUNT(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.COUNT, 2, PersonalRecordMode.BOTH, "Workout count change", null),
    WORKOUT_CHANGE_SECONDS(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.SECONDS, 2, PersonalRecordMode.BOTH, "Workout duration change", null),
    WORKOUT_CHANGE_KCAL(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.KCAL, 2, PersonalRecordMode.BOTH, "Workout calorie change", null),
    WORKOUT_CHANGE_BPM(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.BPM, 2, PersonalRecordMode.BOTH, "Workout heart-rate change", null),
    CHANGE_KM(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.KM, 2, PersonalRecordMode.BOTH, "Distance change", null),
    CHANGE_KG_REPETITIONS(PersonalRecordDomain.WORKOUT, PersonalRecordUnit.KG_REPETITIONS, 2, PersonalRecordMode.BOTH, "Volume change", null),
    TARGET_DIFFERENCE_KCAL(PersonalRecordDomain.NUTRITION, PersonalRecordUnit.KCAL, 2, PersonalRecordMode.BOTH, "Calorie target difference", null);

    private final PersonalRecordDomain domain;
    private final PersonalRecordUnit unit;
    private final int precision;
    private final PersonalRecordMode defaultMode;
    private final String label;
    private final String subjectLabel;

    PersonalRecordCatalogMetric(PersonalRecordDomain domain, PersonalRecordUnit unit, int precision, PersonalRecordMode defaultMode, String label, String subjectLabel) {
        this.domain = domain;
        this.unit = unit;
        this.precision = precision;
        this.defaultMode = defaultMode;
        this.label = label;
        this.subjectLabel = subjectLabel;
    }
}
