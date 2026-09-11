package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.StretchingSet;
import com.jllado.weightcontrol.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StretchingSetRepository extends JpaRepository<StretchingSet, Long> {
    List<StretchingSet> findByUserOrderByNameAsc(User user);
    Optional<StretchingSet> findByIdAndUser(Long id, User user);
    Optional<StretchingSet> findByUserAndNormalizedName(User user, String normalizedName);
}
