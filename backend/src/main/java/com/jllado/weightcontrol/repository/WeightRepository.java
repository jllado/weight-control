package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeightRepository extends JpaRepository<Weight, Long> {
    List<Weight> findByUserOrderByMeasuredAtDesc(User user);
    Optional<Weight> findFirstByUserOrderByMeasuredAtAsc(User user);
    Optional<Weight> findFirstByUserOrderByMeasuredAtDesc(User user);
    Optional<Weight> findFirstByUserAndMeasuredAtLessThanOrderByMeasuredAtDesc(User user, OffsetDateTime measuredAt);
    Optional<Weight> findFirstByUserAndMeasuredAtGreaterThanOrderByMeasuredAtAsc(User user, OffsetDateTime measuredAt);
    Optional<Weight> findFirstByUserAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(User user, OffsetDateTime measuredAt);
    List<Weight> findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(User user, OffsetDateTime startInclusive, OffsetDateTime endExclusive);
    long countByUser(User user);
    boolean existsByLegacyFirebaseId(String legacyFirebaseId);
}
