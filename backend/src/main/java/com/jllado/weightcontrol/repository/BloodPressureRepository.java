package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.BloodPressure;
import com.jllado.weightcontrol.domain.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BloodPressureRepository extends JpaRepository<BloodPressure, Long> {
    List<BloodPressure> findByUserOrderByMeasuredAtDesc(User user);
    Optional<BloodPressure> findFirstByUserOrderByMeasuredAtAsc(User user);
    Optional<BloodPressure> findFirstByUserOrderByMeasuredAtDesc(User user);
    Optional<BloodPressure> findFirstByUserAndMeasuredAtLessThanOrderByMeasuredAtDesc(User user, OffsetDateTime measuredAt);
    Optional<BloodPressure> findFirstByUserAndMeasuredAtGreaterThanOrderByMeasuredAtAsc(User user, OffsetDateTime measuredAt);
    Optional<BloodPressure> findFirstByUserAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(User user, OffsetDateTime measuredAt);
    List<BloodPressure> findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(User user, OffsetDateTime startInclusive, OffsetDateTime endExclusive);
    boolean existsByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(User user, OffsetDateTime startInclusive, OffsetDateTime endExclusive);
    long countByUser(User user);
    boolean existsByLegacyFirebaseId(String legacyFirebaseId);
}
