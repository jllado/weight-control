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
    CARDIO_RESISTANCE(PersonalRecordDomain.WORKOUT, PersonalRecordDirection.MAXIMUM, PersonalRecordUnit.LEVEL, "Highest resistance");

    private final PersonalRecordDomain domain;
    private final PersonalRecordDirection direction;
    private final PersonalRecordUnit unit;
    private final String label;

    PersonalRecordMetric(PersonalRecordDomain domain, PersonalRecordDirection direction, PersonalRecordUnit unit, String label) {
        this.domain = domain;
        this.direction = direction;
        this.unit = unit;
        this.label = label;
    }
}
