package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.FoodPortion;
import java.util.Collection;

public final class FoodSnapshotCompatibility {
    private FoodSnapshotCompatibility() { }

    public static void requireAwareReplacement(Integer schemaVersion, Collection<? extends FoodPortion> previous) {
        if (Integer.valueOf(2).equals(schemaVersion)) return;
        if (previous.stream().anyMatch(food -> food.getEpaDhaMilligrams() != null || food.getReferenceEpaDhaMilligrams() != null
            || food.getAlaMilligrams() != null || food.getReferenceAlaMilligrams() != null
            || food.getFiberGrams() != null || food.getReferenceFiberGrams() != null)) {
            throw new BadRequestException("Reload this record before replacing its EPA+DHA, ALA, or fiber values");
        }
    }
}
