package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.InAppNotification;
import com.jllado.weightcontrol.domain.InAppNotificationType;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineReminder;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {
    Optional<InAppNotification> findByUserAndDeduplicationKey(User user, String deduplicationKey);
    Optional<InAppNotification> findByIdAndUser(Long id, User user);
    @Query("""
        select notification from InAppNotification notification
        where notification.user = :user
          and notification.type = :type
          and notification.reminderDate = :reminderDate
          and notification.routineReminder.routine = :routine
          and notification.routineReminder <> :currentReminder
          and notification.dismissedAt is null
        """)
    List<InAppNotification> findPendingRoutineNotificationsForRoutine(
        @Param("user") User user,
        @Param("type") InAppNotificationType type,
        @Param("routine") Routine routine,
        @Param("currentReminder") RoutineReminder currentReminder,
        @Param("reminderDate") LocalDate reminderDate
    );
    @Query("""
        select notification from InAppNotification notification
        where notification.user = :user
          and notification.dismissedAt is null
          and notification.availableAt <= :availableAt
          and (notification.reminderDate = :reminderDate or notification.type in :persistentTypes)
        order by notification.availableAt asc
        """)
    List<InAppNotification> findPending(
        @Param("user") User user,
        @Param("reminderDate") LocalDate reminderDate,
        @Param("availableAt") OffsetDateTime availableAt,
        @Param("persistentTypes") Set<InAppNotificationType> persistentTypes
    );
}
