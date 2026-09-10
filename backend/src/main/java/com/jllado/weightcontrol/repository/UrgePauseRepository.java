package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.UrgePause;
import com.jllado.weightcontrol.domain.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UrgePauseRepository extends JpaRepository<UrgePause, Long> {
    Optional<UrgePause> findByUserAndStatus(User user, UrgePause.Status status);
    Optional<UrgePause> findByIdAndUser(Long id, User user);
    List<UrgePause> findByStatusAndNotifiedAtIsNullAndEndsAtLessThanEqual(UrgePause.Status status, OffsetDateTime now);
    long countByUser(User user);
    Optional<UrgePause> findFirstByUserOrderByStartedAtAsc(User user);
    Optional<UrgePause> findFirstByUserOrderByStartedAtDesc(User user);
    @Query("select p from UrgePause p where p.user = :user and p.startedAt < :until and coalesce(p.closedAt, p.endsAt) >= :from order by p.startedAt, p.id")
    List<UrgePause> findOverlapping(User user, OffsetDateTime from, OffsetDateTime until);
}
