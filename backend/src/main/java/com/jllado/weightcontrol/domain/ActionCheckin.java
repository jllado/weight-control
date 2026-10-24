package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "action_checkins", uniqueConstraints = @UniqueConstraint(name = "uq_action_checkin_date", columnNames = {"attempt_id", "checkin_date"}))
@Getter @Setter
public class ActionCheckin {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false, updatable = false) private ActionAttempt attempt;
    @Column(name = "checkin_date", nullable = false, updatable = false) private LocalDate checkinDate;
}
