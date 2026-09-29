package com.jllado.weightcontrol.service;

import java.util.List;

final class WorkoutSauna {
    private WorkoutSauna() { }

    static int totalMinutes(boolean saunaSession, List<Integer> rounds) {
        if (!saunaSession) {
            if (rounds != null && !rounds.isEmpty()) throw new BadRequestException("Sauna rounds require a sauna session");
            return 0;
        }
        if (rounds == null || rounds.isEmpty()) throw new BadRequestException("Add at least one sauna round");
        long total = 0;
        for (Integer minutes : rounds) {
            if (minutes == null || minutes <= 0) throw new BadRequestException("Sauna rounds require positive whole minutes");
            total += minutes;
            if (total > Integer.MAX_VALUE) throw new BadRequestException("Sauna duration exceeds the supported range");
        }
        return (int) total;
    }
}
