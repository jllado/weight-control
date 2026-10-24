package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.ActionAttempt;
import com.jllado.weightcontrol.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionAttemptRepository extends JpaRepository<ActionAttempt, Long> {
    @EntityGraph(attributePaths = "checkins")
    List<ActionAttempt> findByUserOrderByStartDateAscIdAsc(User user);
    @EntityGraph(attributePaths = "checkins")
    Optional<ActionAttempt> findByUserAndId(User user, Long id);
    @EntityGraph(attributePaths = "checkins")
    Optional<ActionAttempt> findFirstByUserAndRecommendationFingerprintOrderByIdDesc(User user, String fingerprint);
}
