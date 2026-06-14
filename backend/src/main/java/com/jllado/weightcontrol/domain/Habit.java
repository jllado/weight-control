package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "habits")
@Getter
@Setter
public class Habit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legacy_firebase_id", unique = true, length = 64)
    private String legacyFirebaseId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "start_date", nullable = false)
    private OffsetDateTime startDate;

    @Column(nullable = false)
    private Integer duration;

    @Column(name = "last_time_date")
    private OffsetDateTime lastTimeDate;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private Integer times;

    @Column(name = "current_strike", nullable = false)
    private Integer currentStrike;

    @Column(name = "best_strike", nullable = false)
    private Integer bestStrike;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
