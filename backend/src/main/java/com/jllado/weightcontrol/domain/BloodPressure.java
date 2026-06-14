package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "blood_pressures")
@Getter
@Setter
public class BloodPressure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legacy_firebase_id", unique = true, length = 64)
    private String legacyFirebaseId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "measured_at", nullable = false)
    private OffsetDateTime measuredAt;

    @Column(nullable = false)
    private Integer upper;

    @Column(nullable = false)
    private Integer lower;

    @Column(name = "lost_upper", nullable = false)
    private Integer lostUpper;

    @Column(name = "lost_lower", nullable = false)
    private Integer lostLower;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
