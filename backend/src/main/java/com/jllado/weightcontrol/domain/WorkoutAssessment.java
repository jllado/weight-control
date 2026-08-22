package com.jllado.weightcontrol.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "workout_assessments",
    uniqueConstraints = @UniqueConstraint(name = "uq_workout_assessments_workout", columnNames = "workout_id")
)
@Getter
@Setter
public class WorkoutAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_id", nullable = false)
    private Workout workout;

    @Column(name = "goal_alignment_score", nullable = false)
    private int goalAlignmentScore;

    @Column(name = "estimated_training_demand_score", nullable = false)
    private int estimatedTrainingDemandScore;

    @Column(nullable = false, columnDefinition = "text")
    private String rationale;

    @Column(nullable = false, columnDefinition = "text")
    private String strength;

    @Column(nullable = false, columnDefinition = "text")
    private String improvement;

    @Column(name = "next_workout_action", nullable = false, columnDefinition = "text")
    private String nextWorkoutAction;

    @Column(name = "goal_snapshot", nullable = false)
    private String goalSnapshot;

    @Column(name = "plan_updated_at", nullable = false)
    private Instant planUpdatedAt;

    @Column(name = "workout_updated_at", nullable = false)
    private Instant workoutUpdatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
