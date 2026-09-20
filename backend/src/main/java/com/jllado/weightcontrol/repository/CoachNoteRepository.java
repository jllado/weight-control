package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.CoachNote;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachNoteRepository extends JpaRepository<CoachNote, Long> {
    List<CoachNote> findByUserOrderByNoteDateDescIdDesc(User user);
    List<CoachNote> findByUserAndNoteDateBetweenOrderByNoteDateAscIdAsc(User user, LocalDate from, LocalDate to);
    Optional<CoachNote> findByUserAndId(User user, Long id);
    Optional<CoachNote> findFirstByUserOrderByNoteDateAscIdAsc(User user);
    Optional<CoachNote> findFirstByUserOrderByNoteDateDescIdDesc(User user);
    long countByUser(User user);
}
