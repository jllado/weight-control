package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "exercises")
@Getter
@Setter
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_mode", nullable = false, length = 16)
    private ExerciseTrackingMode trackingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "exercise_type", nullable = false, length = 16)
    private ExerciseType exerciseType;

    @Column(name = "default_warm_up", nullable = false)
    private boolean defaultWarmUp;

    @Column(name = "default_repetitions")
    private Integer defaultRepetitions;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
