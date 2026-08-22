package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Medication;
import com.jllado.weightcontrol.domain.MedicationDose;
import com.jllado.weightcontrol.domain.MedicationDoseStatus;
import com.jllado.weightcontrol.domain.User;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationDoseRepository extends JpaRepository<MedicationDose, Long> {
    Optional<MedicationDose> findByMedicationAndScheduledAt(Medication medication, OffsetDateTime scheduledAt);
    List<MedicationDose> findByMedicationAndStatusInAndScheduledAtBefore(Medication medication, Collection<MedicationDoseStatus> statuses, OffsetDateTime scheduledAt);
    List<MedicationDose> findByStatusAndSnoozedUntilLessThanEqual(MedicationDoseStatus status, OffsetDateTime snoozedUntil);
    List<MedicationDose> findByMedicationUserAndScheduledAtBetweenOrderByScheduledAtDesc(User user, OffsetDateTime from, OffsetDateTime to);
    Optional<MedicationDose> findFirstByMedicationAndStatusInOrderByScheduledAtDesc(Medication medication, Collection<MedicationDoseStatus> statuses);
}
