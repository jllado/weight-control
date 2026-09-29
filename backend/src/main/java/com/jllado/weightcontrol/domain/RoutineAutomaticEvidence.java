package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "routine_automatic_evidence", uniqueConstraints = @UniqueConstraint(columnNames = {"routine_id", "source_kind", "source_key"}))
@Getter
@Setter
public class RoutineAutomaticEvidence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id", nullable = false)
    private Routine routine;

    @Column(name = "source_kind", nullable = false, length = 16)
    private String sourceKind;

    @Column(name = "source_key", nullable = false, length = 255)
    private String sourceKey;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;
}
