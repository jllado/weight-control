package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "coach_warning_revisions")
@Getter
@Setter
public class CoachWarningRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "warning_id", nullable = false)
    private CoachWarning warning;
    @Column(nullable = false)
    private long version;
    @Column(nullable = false, columnDefinition = "text")
    private String snapshot;
}
