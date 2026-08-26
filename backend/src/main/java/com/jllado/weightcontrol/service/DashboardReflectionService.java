package com.jllado.weightcontrol.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.ReflectionOverviewResponse;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.ReflectionSummaryResponse;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.SaveReflectionRequest;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.DashboardReflection;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.DailyStatusRepository;
import com.jllado.weightcontrol.repository.DashboardReflectionRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class DashboardReflectionService {

    private final DashboardReflectionRepository reflectionRepository;
    private final DailyStatusRepository dailyStatusRepository;
    private final DailyStatusSnapshotService snapshotService;
    private final HealthDataContextService healthDataContextService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public DashboardReflectionService(
        DashboardReflectionRepository reflectionRepository,
        DailyStatusRepository dailyStatusRepository,
        DailyStatusSnapshotService snapshotService,
        HealthDataContextService healthDataContextService,
        AppProperties properties,
        ObjectMapper objectMapper
    ) {
        this.reflectionRepository = reflectionRepository;
        this.dailyStatusRepository = dailyStatusRepository;
        this.snapshotService = snapshotService;
        this.healthDataContextService = healthDataContextService;
        this.properties = properties;
        this.objectMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public ReflectionOverviewResponse getOverview(User user) {
        LocalDate firstTrackedDate = dailyStatusRepository.findFirstByUserOrderByStatusDateAsc(user)
            .map(DailyStatus::getStatusDate)
            .orElse(null);
        List<ReflectionSummaryResponse> summaries = reflectionRepository.findByUserOrderByReflectionDateDesc(user).stream()
            .map(ReflectionSummaryResponse::from)
            .toList();
        return new ReflectionOverviewResponse(
            firstTrackedDate,
            user.getLastCompletedDashboardDate(),
            !properties.chatGptActions().token().isBlank(),
            summaries
        );
    }

    public Optional<DashboardReflection> find(User user, LocalDate reflectionDate) {
        return reflectionRepository.findByUserAndReflectionDate(user, reflectionDate);
    }

    public JsonNode getContext(User user, LocalDate reflectionDate) {
        validateEligibleDate(user, reflectionDate);
        snapshotService.getOrBuild(user, reflectionDate);
        return objectMapper.valueToTree(healthDataContextService.getReflectionContext(user, reflectionDate));
    }

    public DashboardReflection save(User user, LocalDate reflectionDate, SaveReflectionRequest request) {
        validateEligibleDate(user, reflectionDate);
        DashboardReflection reflection = reflectionRepository.findByUserAndReflectionDate(user, reflectionDate)
            .orElseGet(() -> {
                DashboardReflection created = new DashboardReflection();
                created.setUser(user);
                return created;
            });
        reflection.setReflectionDate(reflectionDate);
        reflection.setWindowStart(reflectionDate.minusDays(HealthDataContextService.REFLECTION_CONTEXT_DAYS - 1L));
        reflection.setWindowEnd(reflectionDate);
        reflection.setGeneratedAt(Instant.now());
        reflection.setModel("ChatGPT");
        reflection.setTitle(request.title());
        reflection.setSummary(request.summary());
        reflection.setPlanProgressScore(request.planProgressScore());
        reflection.setPlanProgressRationale(request.planProgressRationale());
        reflection.setPositiveSignals(request.positiveSignals());
        reflection.setWatchouts(request.watchouts());
        reflection.setNextActions(request.nextActions());
        return reflectionRepository.save(reflection);
    }

    private void validateEligibleDate(User user, LocalDate reflectionDate) {
        LocalDate firstTrackedDate = dailyStatusRepository.findFirstByUserOrderByStatusDateAsc(user)
            .map(DailyStatus::getStatusDate)
            .orElseThrow(() -> new BadRequestException("No tracked dashboard dates are available"));
        LocalDate lastCompletedDate = user.getLastCompletedDashboardDate();
        if (lastCompletedDate == null || reflectionDate.isBefore(firstTrackedDate) || reflectionDate.isAfter(lastCompletedDate)) {
            throw new BadRequestException("Reflections can only be generated for completed tracked dates");
        }
    }
}
