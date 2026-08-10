package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.BackStatus;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackStatusRepository extends JpaRepository<BackStatus, Long> {
    List<BackStatus> findByUserOrderByStatusDateDesc(User user);
    Optional<BackStatus> findByUserAndStatusDate(User user, LocalDate statusDate);
}
