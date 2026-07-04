package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "daily_statuses", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "status_date"}))
@Getter
@Setter
public class DailyStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legacy_firebase_id", unique = true, length = 64)
    private String legacyFirebaseId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "status_date", nullable = false)
    private LocalDate statusDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "weight_id")
    private Weight weight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blood_pressure_id")
    private BloodPressure bloodPressure;

    @Column(name = "total_routines", nullable = false)
    private Integer totalRoutines;

    @Column(name = "total_weight_routines", nullable = false)
    private Integer totalWeightRoutines;

    @Column(name = "total_blood_pressure_routines", nullable = false)
    private Integer totalBloodPressureRoutines;

    @Column(name = "total_flexibility_routines", nullable = false)
    private Integer totalFlexibilityRoutines;

    @Column(name = "total_mind_routines", nullable = false)
    private Integer totalMindRoutines;

    @Column(name = "routines_done", nullable = false)
    private Integer routinesDone;

    @Column(name = "weight_done", nullable = false)
    private Integer weightDone;

    @Column(name = "blood_pressure_done", nullable = false)
    private Integer bloodPressureDone;

    @Column(name = "flexibility_done", nullable = false)
    private Integer flexibilityDone;

    @Column(name = "mind_done", nullable = false)
    private Integer mindDone;

    @Column(name = "routines_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal routinesPercentage;

    @Column(name = "weight_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal weightPercentage;

    @Column(name = "blood_pressure_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal bloodPressurePercentage;

    @Column(name = "flexibility_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal flexibilityPercentage;

    @Column(name = "mind_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal mindPercentage;

    @Column(name = "routines_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal routinesScore;

    @Column(name = "weight_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal weightScore;

    @Column(name = "blood_pressure_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal bloodPressureScore;

    @Column(name = "flexibility_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal flexibilityScore;

    @Column(name = "mind_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal mindScore;

    @Column(name = "routines_status", nullable = false, precision = 6, scale = 2)
    private BigDecimal routinesStatus;

    @Column(name = "weight_status", nullable = false, precision = 6, scale = 2)
    private BigDecimal weightStatus;

    @Column(name = "blood_pressure_status", nullable = false, precision = 6, scale = 2)
    private BigDecimal bloodPressureStatus;

    @Column(name = "flexibility_status", nullable = false, precision = 6, scale = 2)
    private BigDecimal flexibilityStatus;

    @Column(name = "mind_status", nullable = false, precision = 6, scale = 2)
    private BigDecimal mindStatus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
