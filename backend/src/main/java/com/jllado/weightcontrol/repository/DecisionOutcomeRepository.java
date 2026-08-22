package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.DecisionOutcome;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionOutcomeRepository extends JpaRepository<DecisionOutcome, Long> {
    List<DecisionOutcome> findByUserOrderByOutcomeDateAscIdAsc(User user);
    List<DecisionOutcome> findByUserAndOutcomeDateLessThanEqualOrderByOutcomeDateAscIdAsc(User user, LocalDate outcomeDate);
    List<DecisionOutcome> findByUserAndOutcomeDateBetweenOrderByOutcomeDateAscIdAsc(User user, LocalDate startDate, LocalDate endDate);
    Optional<DecisionOutcome> findFirstByUserOrderByOutcomeDateAscIdAsc(User user);
    Optional<DecisionOutcome> findFirstByUserOrderByOutcomeDateDescIdDesc(User user);
    long countByUser(User user);
}
