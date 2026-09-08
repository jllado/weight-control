package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachWarningRepository extends JpaRepository<CoachWarning, Long> {
    List<CoachWarning> findByUserAndStatusOrderByTypeAsc(User user, CoachWarningStatus status);
    Page<CoachWarning> findByUserAndStatusOrderByUpdatedAtDescIdDesc(User user, CoachWarningStatus status, Pageable page);
    Optional<CoachWarning> findByUserAndId(User user, Long id);
    Optional<CoachWarning> findByUserAndRequestKey(User user, String requestKey);
    boolean existsByUserAndTypeAndStatus(User user, CoachWarningType type, CoachWarningStatus status);
}
