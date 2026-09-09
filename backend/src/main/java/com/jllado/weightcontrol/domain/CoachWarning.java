package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "coach_warnings")
@Getter
@Setter
public class CoachWarning {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private CoachWarningType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private CoachWarningStatus status;
    @Column(nullable = false, columnDefinition = "text")
    private String explanation;
    @Column(nullable = false, columnDefinition = "text")
    private String evidence;
    @Column(nullable = false, length = 500)
    private String action;
    private LocalDate onsetDate;
    @Column(nullable = false)
    private LocalDate reviewedDate;
    @Column(columnDefinition = "text")
    private String resolutionRationale;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant resolvedAt;
    @Column(nullable = false, length = 36)
    private String requestKey;
    @Column(nullable = false, columnDefinition = "text")
    private String createPayload;
    @Column(nullable = false)
    private long version;
}
