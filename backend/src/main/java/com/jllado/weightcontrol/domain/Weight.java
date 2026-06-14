package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "weights")
@Getter
@Setter
public class Weight {

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

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal weight;

    @Column(name = "fat_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal fatPercentage;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal fat;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal muscle;

    @Column(name = "muscle_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal musclePercentage;

    @Column(name = "lost_weight", nullable = false, precision = 6, scale = 2)
    private BigDecimal lostWeight;

    @Column(name = "lost_fat", nullable = false, precision = 6, scale = 2)
    private BigDecimal lostFat;

    @Column(name = "lost_muscle", nullable = false, precision = 6, scale = 2)
    private BigDecimal lostMuscle;

    @Column(name = "photo_front_path", length = 500)
    private String photoFrontPath;

    @Column(name = "photo_left_path", length = 500)
    private String photoLeftPath;

    @Column(name = "photo_right_path", length = 500)
    private String photoRightPath;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
