package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.RoutineReminder;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineReminderRepository extends JpaRepository<RoutineReminder, Long> {
    List<RoutineReminder> findByReminderTime(LocalTime reminderTime);
    List<RoutineReminder> findByReminderSnoozedUntilBetween(OffsetDateTime start, OffsetDateTime end);
}
