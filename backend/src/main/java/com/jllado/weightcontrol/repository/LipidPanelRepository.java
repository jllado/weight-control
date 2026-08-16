package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.LipidPanel;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LipidPanelRepository extends JpaRepository<LipidPanel, Long> {
    List<LipidPanel> findByUserOrderByPanelDateDesc(User user);
    Optional<LipidPanel> findByUserAndPanelDate(User user, LocalDate panelDate);
    Optional<LipidPanel> findFirstByUserOrderByPanelDateAsc(User user);
    Optional<LipidPanel> findFirstByUserOrderByPanelDateDesc(User user);
    List<LipidPanel> findByUserAndPanelDateBetweenOrderByPanelDateAsc(User user, LocalDate startDate, LocalDate endDate);
    long countByUser(User user);
}
