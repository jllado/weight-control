package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "urge_pauses")
@Getter
@Setter
public class UrgePause {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 36)
    private String sessionKey;
    @Column(length = 500)
    private String description;
    @Column(nullable = false)
    private OffsetDateTime startedAt;
    @Column(nullable = false)
    private OffsetDateTime endsAt;
    private OffsetDateTime closedAt;
    private OffsetDateTime notifiedAt;
    private OffsetDateTime answeredAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private Status status = Status.ACTIVE;
    @Enumerated(EnumType.STRING) @Column(length = 16)
    private Answer answer;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_outcome_id")
    private DecisionOutcome decisionOutcome;

    public enum Status { ACTIVE, CANCELLED, REPEATED, FINISHED }
    public enum Answer { NOT_ANYMORE, STILL_WANT }
}
