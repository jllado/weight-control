package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PersonalRecordDtos {

    private PersonalRecordDtos() {
    }

    public record PersonalRecordSubjectResponse(String type, Long id, String label) {
    }

    public record PersonalRecordQualifierResponse(BigDecimal loadKg, String label) {
    }

    public record PersonalRecordSourceResponse(PersonalRecordSourceType type, Long id, Integer linePosition, Integer segmentPosition) {
    }

    public record CurrentRecordResponse(
        PersonalRecordMetric metric,
        String metricLabel,
        PersonalRecordDomain domain,
        PersonalRecordDirection direction,
        BigDecimal value,
        PersonalRecordUnit unit,
        LocalDate recordDate,
        PersonalRecordSubjectResponse subject,
        PersonalRecordQualifierResponse qualifier,
        PersonalRecordSourceResponse source
    ) {
    }

    public record HistoryEventResponse(
        PersonalRecordMetric metric,
        String metricLabel,
        PersonalRecordDomain domain,
        PersonalRecordDirection direction,
        PersonalRecordEventKind kind,
        BigDecimal value,
        BigDecimal previousValue,
        PersonalRecordUnit unit,
        LocalDate recordDate,
        boolean currentRecord,
        PersonalRecordSubjectResponse subject,
        PersonalRecordQualifierResponse qualifier,
        PersonalRecordSourceResponse source
    ) {
    }

    public record RecordAchievementResponse(
        PersonalRecordMetric metric,
        String metricLabel,
        PersonalRecordDomain domain,
        PersonalRecordDirection direction,
        PersonalRecordEventKind kind,
        BigDecimal value,
        BigDecimal previousValue,
        PersonalRecordUnit unit,
        LocalDate recordDate,
        PersonalRecordSubjectResponse subject,
        PersonalRecordQualifierResponse qualifier,
        PersonalRecordSourceResponse source
    ) {
    }

    public record HistoryPageResponse(List<HistoryEventResponse> items, int page, int size, long totalElements, int totalPages) {
    }

    public record RecordMutationResponse<T>(T result, List<RecordAchievementResponse> recordAchievements) {
    }

    public record CatalogDirectionResponse(PersonalRecordDirection direction, PersonalRecordMetric metric, String label) {
    }

    public record CatalogMetricResponse(
        PersonalRecordCatalogMetric key,
        String label,
        PersonalRecordDomain domain,
        PersonalRecordUnit unit,
        int precision,
        PersonalRecordMode defaultMode,
        PersonalRecordMode mode,
        List<CatalogDirectionResponse> directions
    ) {
    }

    public record SettingOverrideRequest(@NotNull PersonalRecordCatalogMetric metric, @NotNull PersonalRecordMode mode) {
    }

    public record SettingsRequest(@NotNull List<@Valid SettingOverrideRequest> overrides) {
    }
}
