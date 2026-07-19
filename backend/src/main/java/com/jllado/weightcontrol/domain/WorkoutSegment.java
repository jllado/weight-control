package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "workout_segments")
@Getter
@Setter
public class WorkoutSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_line_id", nullable = false)
    private WorkoutLine workoutLine;

    @Column(nullable = false)
    private Integer position;

    @Column
    private Integer repetitions;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(precision = 7, scale = 2)
    private BigDecimal weight;

    @Column(name = "speed_kph", precision = 6, scale = 2)
    private BigDecimal speedKph;

    @Column(name = "distance_km", precision = 7, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "incline_percent", precision = 6, scale = 2)
    private BigDecimal inclinePercent;

    @Column(name = "resistance_level")
    private Integer resistanceLevel;

    @Column
    private Integer calories;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
