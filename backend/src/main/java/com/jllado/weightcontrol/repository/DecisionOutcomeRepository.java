package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.DecisionOutcome;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionOutcomeRepository extends JpaRepository<DecisionOutcome, Long> {
    List<DecisionOutcome> findByUserAndOutcomeDateLessThanEqualOrderByOutcomeDateAscIdAsc(User user, LocalDate outcomeDate);
    List<DecisionOutcome> findByUserAndOutcomeDateBetweenOrderByOutcomeDateAscIdAsc(User user, LocalDate startDate, LocalDate endDate);
}
