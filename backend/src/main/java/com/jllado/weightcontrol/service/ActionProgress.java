package com.jllado.weightcontrol.service;

import java.time.LocalDate;
import java.util.Set;

public record ActionProgress(int streak, int bestStreak, int percentage, LocalDate completedDate, boolean checked, String status) {
    public static ActionProgress calculate(LocalDate start, int target, Set<LocalDate> checks, LocalDate stopped,
        LocalDate selected, LocalDate today) {
        LocalDate end = stopped != null && stopped.isBefore(selected) ? stopped : selected;
        int run = 0;
        int best = 0;
        LocalDate previous = null;
        LocalDate completion = null;
        for (LocalDate date : checks.stream().filter(date -> !date.isBefore(start) && !date.isAfter(end)).sorted().toList()) {
            run = previous != null && previous.plusDays(1).equals(date) ? run + 1 : 1;
            best = Math.max(best, run);
            if (run >= target && completion == null) completion = date;
            previous = date;
        }
        boolean checked = checks.contains(selected);
        LocalDate anchor = end.equals(today) && !checks.contains(end) ? end.minusDays(1) : end;
        int streak = previous != null && (previous.equals(end) || previous.equals(anchor)) ? run : 0;
        String status = stopped != null && !stopped.isAfter(selected) ? "STOPPED"
            : start.isAfter(selected) ? "SCHEDULED"
            : completion != null ? "COMPLETED" : "ACTIVE";
        if (completion != null && !status.equals("STOPPED")) streak = target;
        return new ActionProgress(streak, best, (int) Math.min(100L, (long) streak * 100 / target), completion, checked, status);
    }
}
