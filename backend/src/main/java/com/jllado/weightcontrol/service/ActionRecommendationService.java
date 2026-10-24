package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.ActionDtos.SuggestionResponse;
import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.WeeklyReflectionResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.WeeklyReflection;
import com.jllado.weightcontrol.repository.*;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service @Transactional
public class ActionRecommendationService {
    private final WeeklyReflectionRepository reflections;
    private final CoachingPlanRepository plans;
    private final ActionAttemptRepository attempts;

    public ActionRecommendationService(WeeklyReflectionRepository reflections, CoachingPlanRepository plans,
        ActionAttemptRepository attempts) {
        this.reflections = reflections;
        this.plans = plans;
        this.attempts = attempts;
    }

    public List<SuggestionResponse> suggestions(User user) {
        var rows = new ArrayList<SuggestionResponse>();
        reflections.findFirstByWeeklySummaryUserOrderByWeeklySummaryFridayDateDesc(user)
            .ifPresent(reflection -> rows.addAll(weekly(user, reflection)));
        plans.findByUser(user).ifPresent(plan -> plan.getActions().stream()
            .collect(java.util.stream.Collectors.toMap(WeeklyRecommendationMapper::normalized, text -> text,
                (first, duplicate) -> first, java.util.LinkedHashMap::new)).values().forEach(text ->
                rows.add(response(user, WeeklyRecommendationMapper.key("plan:" + plan.getId(), text),
                    text.trim(), "Coach plan", List.of("Coach plan")))));
        return rows;
    }

    public SuggestionResponse resolve(User user, String key) {
        if (key.startsWith("weekly:")) {
            String[] parts = key.split(":");
            if (parts.length == 3 && parts[1].matches("[0-9]+")) {
                long id;
                try { id = Long.parseLong(parts[1]); }
                catch (NumberFormatException exception) { throw stale(); }
                return reflections.findByIdAndWeeklySummaryUser(id, user).stream()
                    .flatMap(reflection -> weekly(user, reflection).stream()).filter(row -> row.sourceKey().equals(key))
                    .findFirst().orElseThrow(ActionRecommendationService::stale);
            }
        }
        return suggestions(user).stream().filter(row -> row.sourceKey().equals(key)).findFirst()
            .orElseThrow(ActionRecommendationService::stale);
    }

    public List<SuggestionResponse> weekly(User user, WeeklyReflection reflection) {
        String label = "Weekly summary · " + reflection.getWeeklySummary().getFridayDate();
        return WeeklyRecommendationMapper.from("weekly:" + reflection.getId(), WeeklyReflectionResponse.from(reflection))
            .stream().map(row -> response(user, row.sourceKey(), row.wording(), label, row.provenance())).toList();
    }

    private SuggestionResponse response(User user, String key, String text, String label, List<String> provenance) {
        Long existing = attempts.findFirstByUserAndRecommendationFingerprintOrderByIdDesc(user,
            WeeklyRecommendationMapper.fingerprint(text)).map(attempt -> attempt.getId()).orElse(null);
        return new SuggestionResponse(key, text, label, provenance, existing);
    }

    private static BadRequestException stale() {
        return new BadRequestException("This recommendation has changed or is no longer available. Open its report or Suggestions to choose an action.");
    }
}
