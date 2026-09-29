package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineAutomaticEvidence;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineAutomaticEvidenceRepository extends JpaRepository<RoutineAutomaticEvidence, Long> {
    List<RoutineAutomaticEvidence> findByRoutine(Routine routine);
    Optional<RoutineAutomaticEvidence> findByRoutineAndSourceKindAndSourceKey(Routine routine, String sourceKind, String sourceKey);
    void deleteByRoutine(Routine routine);
    void deleteByRoutineAndSourceKind(Routine routine, String sourceKind);
}
