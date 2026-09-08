package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.CoachWarningRevision;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachWarningRevisionRepository extends JpaRepository<CoachWarningRevision, Long> {
    Page<CoachWarningRevision> findByWarningIdOrderByVersionDesc(Long warningId, Pageable page);
}
