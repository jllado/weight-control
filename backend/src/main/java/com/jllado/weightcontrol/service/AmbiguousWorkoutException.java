package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.SessionChoice;
import java.util.List;

public class AmbiguousWorkoutException extends RuntimeException {
    private final List<SessionChoice> sessions;
    public AmbiguousWorkoutException(List<SessionChoice> sessions) {
        super("Several sessions were recorded on this date; choose a sessionReference and try again");
        this.sessions = List.copyOf(sessions);
    }
    public List<SessionChoice> getSessions() { return sessions; }
}
