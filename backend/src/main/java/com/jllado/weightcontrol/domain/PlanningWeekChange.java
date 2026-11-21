package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "planning_week_changes", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "effective_date"}))
@Getter
public class PlanningWeekChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Setter
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    protected PlanningWeekChange() { }

    public PlanningWeekChange(User user, LocalDate effectiveDate) {
        this.user = user;
        this.effectiveDate = effectiveDate;
    }
}
