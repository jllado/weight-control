package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.SaveWeeklyReflectionRequest;
import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.WeeklyReflectionContextResponse;
import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.WeeklyReflectionOverviewResponse;
import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.WeeklyReflectionResponse;
import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.WeeklySummaryReflectionItem;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.SavedWeeklySummary;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.WeeklyReflection;
import com.jllado.weightcontrol.repository.SavedWeeklySummaryRepository;
import com.jllado.weightcontrol.repository.WeeklyReflectionRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class WeeklyReflectionService {

    private final SavedWeeklySummaryRepository summaryRepository;
    private final WeeklyReflectionRepository reflectionRepository;
    private final WeeklySummaryService summaryService;
    private final AppProperties properties;

    public WeeklyReflectionService(
        SavedWeeklySummaryRepository summaryRepository,
        WeeklyReflectionRepository reflectionRepository,
        WeeklySummaryService summaryService,
        AppProperties properties
    ) {
        this.summaryRepository = summaryRepository;
        this.reflectionRepository = reflectionRepository;
        this.summaryService = summaryService;
        this.properties = properties;
    }

    public WeeklyReflectionOverviewResponse getOverview(User user) {
        List<SavedWeeklySummary> summaries = summaryRepository.findByUserOrderByFridayDateDesc(user);
        Set<Long> reflectedSummaryIds = summaries.isEmpty()
            ? Set.of()
            : reflectionRepository.findByWeeklySummaryIn(summaries).stream()
                .map(reflection -> reflection.getWeeklySummary().getId())
                .collect(java.util.stream.Collectors.toSet());
        return new WeeklyReflectionOverviewResponse(
            summaryService.latestClosedOutcomeWeekEnd(LocalDate.now(com.jllado.weightcontrol.util.DateTimes.USER_ZONE)),
            !properties.chatGptActions().token().isBlank(),
            summaries.stream().map(summary -> new WeeklySummaryReflectionItem(
                summary.getFridayDate(),
                reflectedSummaryIds.contains(summary.getId())
            )).toList()
        );
    }

    public WeeklyReflectionContextResponse getContext(User user, LocalDate fridayDate) {
        SavedWeeklySummary summary = requireSavedSummary(user, fridayDate);
        WeeklyReflection reflection = reflectionRepository.findByWeeklySummary(summary).orElse(null);
        return WeeklyReflectionContextResponse.from(summary, summaryService.readSnapshot(summary), reflection);
    }

    public WeeklyReflectionResponse save(User user, LocalDate fridayDate, SaveWeeklyReflectionRequest request) {
        SavedWeeklySummary summary = summaryRepository.findByUserAndFridayDateForUpdate(user, fridayDate)
            .orElseThrow(() -> new BadRequestException("A saved weekly summary is required before saving its reflection"));
        WeeklyReflection reflection = reflectionRepository.findByWeeklySummary(summary).orElseGet(() -> {
            WeeklyReflection created = new WeeklyReflection();
            created.setWeeklySummary(summary);
            return created;
        });
        reflection.setGeneratedAt(Instant.now());
        reflection.setModel("ChatGPT");
        reflection.setTitle(request.title());
        reflection.setSummary(request.summary());
        reflection.setBodyCompositionSummary(request.bodyComposition().summary());
        reflection.setBodyCompositionNextAction(request.bodyComposition().nextAction());
        reflection.setBloodPressureSummary(request.bloodPressure().summary());
        reflection.setBloodPressureNextAction(request.bloodPressure().nextAction());
        reflection.setRoutinesSummary(request.routines().summary());
        reflection.setRoutinesNextAction(request.routines().nextAction());
        reflection.setNutritionSummary(request.nutrition().summary());
        reflection.setNutritionNextAction(request.nutrition().nextAction());
        reflection.setTrainingRecoverySummary(request.trainingRecovery().summary());
        reflection.setTrainingRecoveryNextAction(request.trainingRecovery().nextAction());
        reflection.setGoalProgressSummary(request.goalProgress().summary());
        reflection.setGoalProgressNextAction(request.goalProgress().nextAction());
        reflection.setNextWeekActions(request.nextWeekActions());
        return WeeklyReflectionResponse.from(reflectionRepository.save(reflection));
    }

    private SavedWeeklySummary requireSavedSummary(User user, LocalDate fridayDate) {
        return summaryRepository.findByUserAndFridayDate(user, fridayDate)
            .orElseThrow(() -> new BadRequestException("A saved weekly summary is required for this date"));
    }
}
