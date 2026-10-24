package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "action_attempts")
@Getter @Setter
public class ActionAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false) private User user;
    @Column(nullable = false, length = 255) private String wording;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "target_days", nullable = false) private int targetDays;
    @Column(name = "first_checkin_at") private Instant firstCheckinAt;
    @Column(name = "stopped_date") private LocalDate stoppedDate;
    @Column(name = "source_key", length = 120, updatable = false) private String sourceKey;
    @Column(name = "recommendation_fingerprint", length = 64, updatable = false) private String recommendationFingerprint;
    @Column(name = "source_label", length = 255, updatable = false) private String sourceLabel;
    @Column(name = "restarted_from_id", updatable = false) private Long restartedFromId;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ActionCheckin> checkins = new ArrayList<>();
}
