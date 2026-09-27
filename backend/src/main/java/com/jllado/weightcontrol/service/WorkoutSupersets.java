package com.jllado.weightcontrol.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class WorkoutSupersets {
    private WorkoutSupersets() { }

    static void validate(List<String> groupIds, List<Integer> segmentCounts) {
        Map<String, Integer> members = new HashMap<>();
        Map<String, Integer> expectedSegments = new HashMap<>();
        String active = null;
        for (int i = 0; i < groupIds.size(); i++) {
            String id = groupIds.get(i);
            if (id == null) {
                active = null;
                continue;
            }
            if (!id.matches("[0-9a-fA-F-]{1,36}")) throw new BadRequestException("Superset group identifier is invalid");
            if (!id.equals(active) && members.containsKey(id)) throw new BadRequestException("Superset exercises must stay together in order");
            active = id;
            members.merge(id, 1, Integer::sum);
            Integer count = expectedSegments.putIfAbsent(id, segmentCounts.get(i));
            if (count != null && count != segmentCounts.get(i)) throw new BadRequestException("Superset exercises must have the same number of sets or intervals");
        }
        if (members.values().stream().anyMatch(count -> count < 2)) throw new BadRequestException("A superset must contain at least two exercises");
    }
}
