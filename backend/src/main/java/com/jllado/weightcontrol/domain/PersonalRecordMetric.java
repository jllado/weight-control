package com.jllado.weightcontrol.domain;

import lombok.Getter;

@Getter
public enum PersonalRecordMetric {
    BODY_WEIGHT(PersonalRecordDomain.BODY, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.KG, "Lowest weight"),
    BODY_FAT_MASS(PersonalRecordDomain.BODY, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.KG, "Lowest fat mass"),
    BODY_FAT_PERCENTAGE(PersonalRecordDomain.BODY, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.PERCENT, "Lowest fat percentage"),
    BODY_MUSCLE_MASS(PersonalRecordDomain.BODY, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.KG, "Highest muscle mass"),
    BODY_MUSCLE_PERCENTAGE(PersonalRecordDomain.BODY, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.PERCENT, "Highest muscle percentage"),
    WORKOUT_HEAVIEST_LOAD(PersonalRecordDomain.WORKOUT, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.KG, "Heaviest load"),
    WORKOUT_REPETITIONS(PersonalRecordDomain.WORKOUT, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.REPETITIONS, "Most repetitions"),
    WORKOUT_DURATION(PersonalRecordDomain.WORKOUT, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.SECONDS, "Longest duration"),
    CARDIO_DURATION(PersonalRecordDomain.WORKOUT, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.SECONDS, "Longest interval"),
    CARDIO_SPEED(PersonalRecordDomain.WORKOUT, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.KM_PER_HOUR, "Highest speed"),
    CARDIO_DISTANCE(PersonalRecordDomain.WORKOUT, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.KM, "Longest distance"),
    CARDIO_INCLINE(PersonalRecordDomain.WORKOUT, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.PERCENT, "Highest incline"),
    CARDIO_RESISTANCE(PersonalRecordDomain.WORKOUT, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.LEVEL, "Highest resistance"),
    BLOOD_PRESSURE_SYSTOLIC_MINIMUM(PersonalRecordDomain.VITALS, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.MM_HG, "Lowest systolic pressure", "Blood pressure"),
    BLOOD_PRESSURE_SYSTOLIC_MAXIMUM(PersonalRecordDomain.VITALS, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.MM_HG, "Highest systolic pressure", "Blood pressure"),
    BLOOD_PRESSURE_DIASTOLIC_MINIMUM(PersonalRecordDomain.VITALS, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.MM_HG, "Lowest diastolic pressure", "Blood pressure"),
    BLOOD_PRESSURE_DIASTOLIC_MAXIMUM(PersonalRecordDomain.VITALS, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.MM_HG, "Highest diastolic pressure", "Blood pressure"),
    LIPID_TOTAL_CHOLESTEROL_MINIMUM(PersonalRecordDomain.VITALS, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.MG_PER_DL, "Lowest total cholesterol", "Lipids"),
    LIPID_HDL_MAXIMUM(PersonalRecordDomain.VITALS, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.MG_PER_DL, "Highest HDL", "Lipids"),
    LIPID_LDL_MINIMUM(PersonalRecordDomain.VITALS, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.MG_PER_DL, "Lowest LDL", "Lipids"),
    LIPID_TRIGLYCERIDES_MINIMUM(PersonalRecordDomain.VITALS, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.MG_PER_DL, "Lowest triglycerides", "Lipids"),
    MOOD_MAXIMUM(PersonalRecordDomain.RECOVERY, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.SCORE_OUT_OF_FIVE, "Highest mood", "Mood"),
    SLEEP_TOTAL_DURATION_MAXIMUM(PersonalRecordDomain.RECOVERY, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.SECONDS, "Longest total sleep", "Sleep"),
    SLEEP_DEEP_DURATION_MAXIMUM(PersonalRecordDomain.RECOVERY, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.SECONDS, "Longest deep sleep", "Sleep"),
    SLEEP_REM_DURATION_MAXIMUM(PersonalRecordDomain.RECOVERY, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.SECONDS, "Longest REM sleep", "Sleep"),
    SLEEP_LIGHT_DURATION_MAXIMUM(PersonalRecordDomain.RECOVERY, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.SECONDS, "Longest light sleep", "Sleep"),
    SLEEP_AWAKE_TIME_MINIMUM(PersonalRecordDomain.RECOVERY, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.SECONDS, "Shortest awake time", "Sleep"),
    SLEEP_AVERAGE_HEART_RATE_MINIMUM(PersonalRecordDomain.RECOVERY, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.BPM, "Lowest sleep heart rate", "Sleep"),
    SLEEP_AVERAGE_HRV_MAXIMUM(PersonalRecordDomain.RECOVERY, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.MILLISECONDS, "Highest HRV", "Sleep"),
    MEAL_CALORIES_MINIMUM(PersonalRecordDomain.NUTRITION, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.KCAL, "Lowest meal calories", "Meals"),
    MEAL_CALORIES_MAXIMUM(PersonalRecordDomain.NUTRITION, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.KCAL, "Highest meal calories", "Meals"),
    MEAL_PROTEIN_MINIMUM(PersonalRecordDomain.NUTRITION, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.GRAMS, "Lowest meal protein", "Meals"),
    MEAL_PROTEIN_MAXIMUM(PersonalRecordDomain.NUTRITION, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.GRAMS, "Highest meal protein", "Meals"),
    MEAL_CARBOHYDRATES_MINIMUM(PersonalRecordDomain.NUTRITION, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.GRAMS, "Lowest meal carbohydrates", "Meals"),
    MEAL_CARBOHYDRATES_MAXIMUM(PersonalRecordDomain.NUTRITION, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.GRAMS, "Highest meal carbohydrates", "Meals"),
    MEAL_FAT_MINIMUM(PersonalRecordDomain.NUTRITION, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.GRAMS, "Lowest meal fat", "Meals"),
    MEAL_FAT_MAXIMUM(PersonalRecordDomain.NUTRITION, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.GRAMS, "Highest meal fat", "Meals"),
    DAILY_CALORIES_MINIMUM(PersonalRecordDomain.NUTRITION, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.KCAL, "Lowest daily calories", "Daily nutrition"),
    DAILY_CALORIES_MAXIMUM(PersonalRecordDomain.NUTRITION, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.KCAL, "Highest daily calories", "Daily nutrition"),
    DAILY_PROTEIN_MINIMUM(PersonalRecordDomain.NUTRITION, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.GRAMS, "Lowest daily protein", "Daily nutrition"),
    DAILY_PROTEIN_MAXIMUM(PersonalRecordDomain.NUTRITION, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.GRAMS, "Highest daily protein", "Daily nutrition"),
    DAILY_CARBOHYDRATES_MINIMUM(PersonalRecordDomain.NUTRITION, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.GRAMS, "Lowest daily carbohydrates", "Daily nutrition"),
    DAILY_CARBOHYDRATES_MAXIMUM(PersonalRecordDomain.NUTRITION, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.GRAMS, "Highest daily carbohydrates", "Daily nutrition"),
    DAILY_FAT_MINIMUM(PersonalRecordDomain.NUTRITION, PersonalRecordDirection.MINIMUM, PersonalRecordUnit.GRAMS, "Lowest daily fat", "Daily nutrition"),
    DAILY_FAT_MAXIMUM(PersonalRecordDomain.NUTRITION, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.GRAMS, "Highest daily fat", "Daily nutrition");

    private final PersonalRecordDomain domain;
    private final PersonalRecordDirection direction;
    private final PersonalRecordUnit unit;
    private final String label;
    private final String subjectLabel;

    PersonalRecordMetric(PersonalRecordDomain domain, PersonalRecordDirection direction, PersonalRecordUnit unit, String label) {
        this(domain, direction, unit, label, domain == PersonalRecordDomain.BODY ? "Body" : null);
    }

    PersonalRecordMetric(PersonalRecordDomain domain, PersonalRecordDirection direction, PersonalRecordUnit unit, String label, String subjectLabel) {
        this.domain = domain;
        this.direction = direction;
        this.unit = unit;
        this.label = label;
        this.subjectLabel = subjectLabel;
    }
}
