package com.jllado.weightcontrol.domain;

import java.util.List;

public enum PersonalRecordMode {
    DISABLED(List.of()),
    MINIMUM(List.of(PersonalRecordDirection.MINIMUM)),
    MAXIMUM(List.of(PersonalRecordDirection.MAXIMUM)),
    BOTH(List.of(PersonalRecordDirection.MINIMUM, PersonalRecordDirection.MAXIMUM));

    private final List<PersonalRecordDirection> directions;

    PersonalRecordMode(List<PersonalRecordDirection> directions) {
        this.directions = directions;
    }

    public List<PersonalRecordDirection> directions() {
        return directions;
    }
}
