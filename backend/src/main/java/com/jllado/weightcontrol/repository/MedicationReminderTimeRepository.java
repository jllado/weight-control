package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.MedicationReminderTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationReminderTimeRepository extends JpaRepository<MedicationReminderTime, Long> {
    List<MedicationReminderTime> findByReminderTime(LocalTime reminderTime);
}
