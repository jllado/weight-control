package com.jllado.weightcontrol.service;

public final class RoutineStreakMilestones {

    private RoutineStreakMilestones() {
    }

    public static boolean isMilestone(int days) {
        return days == 21 || days == 60 || days == 90 || days == 180 || days >= 365 && days % 365 == 0;
    }

    public static Integer previousMilestone(int days) {
        if (days <= 21) {
            return null;
        }
        if (days <= 60) {
            return 21;
        }
        if (days <= 90) {
            return 60;
        }
        if (days <= 180) {
            return 90;
        }
        if (days <= 365) {
            return 180;
        }
        return days - 365;
    }
}
