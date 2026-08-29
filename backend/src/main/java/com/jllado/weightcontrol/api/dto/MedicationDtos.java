package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.Medication;
import com.jllado.weightcontrol.domain.MedicationDose;
import com.jllado.weightcontrol.domain.MedicationDoseSource;
import com.jllado.weightcontrol.domain.MedicationDoseStatus;
import com.jllado.weightcontrol.domain.MedicationReminderTime;
import com.jllado.weightcontrol.domain.MedicationRepeatUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public final class MedicationDtos {

    private MedicationDtos() {
    }

    public record MedicationRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal doseAmount,
        @NotBlank @Size(max = 32) String doseUnit,
        @Size(max = 2000) String notes,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @Min(1) Integer repeatEvery,
        @NotNull MedicationRepeatUnit repeatUnit,
        @NotEmpty List<@NotNull LocalTime> reminderTimes,
        boolean active
    ) {
    }

    public record MedicationResponse(
        Long id,
        String name,
        BigDecimal doseAmount,
        String doseUnit,
        String notes,
        LocalDate startDate,
        LocalDate endDate,
        Integer repeatEvery,
        MedicationRepeatUnit repeatUnit,
        List<LocalTime> reminderTimes,
        boolean active
    ) {
        public static MedicationResponse from(Medication medication) {
            return new MedicationResponse(
                medication.getId(),
                medication.getName(),
                medication.getDoseAmount(),
                medication.getDoseUnit(),
                medication.getNotes(),
                medication.getStartDate(),
                medication.getEndDate(),
                medication.getRepeatEvery(),
                medication.getRepeatUnit(),
                medication.getReminderTimes().stream()
                    .map(MedicationReminderTime::getReminderTime)
                    .sorted()
                    .toList(),
                medication.isActive()
            );
        }
    }

    public record MedicationDoseActionRequest(@NotNull OffsetDateTime takenAt) {
    }

    public record MedicationReminderTimeRequest(@NotNull LocalTime oldTime, @NotNull LocalTime time) {
    }

    public record MedicationDoseSnoozeRequest(@NotNull Integer minutes) {
    }

    public record MedicationDoseSnoozeResponse(OffsetDateTime nextReminderAt) {
    }

    public record MedicationDoseResponse(
        Long id,
        Long medicationId,
        OffsetDateTime scheduledAt,
        MedicationDoseStatus status,
        MedicationDoseSource source,
        OffsetDateTime takenAt,
        OffsetDateTime snoozedUntil,
        String medicationName,
        BigDecimal doseAmount,
        String doseUnit,
        String notes
    ) {
        public static MedicationDoseResponse from(MedicationDose dose) {
            return new MedicationDoseResponse(
                dose.getId(),
                dose.getMedication().getId(),
                dose.getScheduledAt(),
                dose.getStatus(),
                dose.getSource(),
                dose.getTakenAt(),
                dose.getSnoozedUntil(),
                dose.getMedicationName(),
                dose.getDoseAmount(),
                dose.getDoseUnit(),
                dose.getNotes()
            );
        }
    }
}
