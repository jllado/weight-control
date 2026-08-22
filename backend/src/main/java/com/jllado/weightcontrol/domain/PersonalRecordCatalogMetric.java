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
    DAILY_FAT(PersonalRecordDomain.NUTRITION, PersonalRecordUnit.GRAMS, 2, PersonalRecordMode.BOTH, "Daily fat", "Daily nutrition");

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
