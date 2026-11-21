package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.OvernightHeartRateLimit;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OvernightHeartRateLimitRepository extends JpaRepository<OvernightHeartRateLimit, Long> {
    boolean existsByUser(User user);
    List<OvernightHeartRateLimit> findByUserOrderByEffectiveFromAsc(User user);
    Optional<OvernightHeartRateLimit> findFirstByUserAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(User user, LocalDate date);
}
