package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.WeeklyReflectionResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WeeklyRecommendationMapper {
    private WeeklyRecommendationMapper() { }

    public record Recommendation(String sourceKey, String wording, List<String> provenance) { }

    public static List<Recommendation> from(String source, WeeklyReflectionResponse reflection) {
        Map<String, Recommendation> rows = new LinkedHashMap<>();
        reflection.nextWeekActions().forEach(text -> add(rows, source, text, "Next week"));
        add(rows, source, reflection.bodyComposition().nextAction(), "Body composition");
        add(rows, source, reflection.bloodPressure().nextAction(), "Blood pressure");
        add(rows, source, reflection.routines().nextAction(), "Routines");
        add(rows, source, reflection.nutrition().nextAction(), "Nutrition");
        add(rows, source, reflection.trainingRecovery().nextAction(), "Training and recovery");
        add(rows, source, reflection.goalProgress().nextAction(), "Goal progress");
        return List.copyOf(rows.values());
    }

    public static String normalized(String text) { return text.trim().toLowerCase(Locale.ROOT); }

    public static String key(String source, String text) { return source + ":" + fingerprint(text); }

    public static String fingerprint(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized(text).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void add(Map<String, Recommendation> rows, String source, String text, String provenance) {
        String normalized = normalized(text);
        Recommendation existing = rows.get(normalized);
        var origins = existing == null ? new ArrayList<String>() : new ArrayList<>(existing.provenance());
        if (!origins.contains(provenance)) origins.add(provenance);
        rows.put(normalized, new Recommendation(key(source, text), existing == null ? text.trim() : existing.wording(), List.copyOf(origins)));
    }
}
