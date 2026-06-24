package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Sickness;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SicknessRepository extends JpaRepository<Sickness, Long> {
    List<Sickness> findByUserOrderBySicknessDateDesc(User user);
    Optional<Sickness> findByUserAndSicknessDate(User user, LocalDate sicknessDate);
}
