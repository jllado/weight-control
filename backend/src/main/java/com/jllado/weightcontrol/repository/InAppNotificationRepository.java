package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.InAppNotification;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {
    Optional<InAppNotification> findByUserAndDeduplicationKey(User user, String deduplicationKey);
    Optional<InAppNotification> findByIdAndUser(Long id, User user);
    List<InAppNotification> findByUserAndReminderDateAndDismissedAtIsNullAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
        User user,
        LocalDate reminderDate,
        OffsetDateTime availableAt
    );
}
