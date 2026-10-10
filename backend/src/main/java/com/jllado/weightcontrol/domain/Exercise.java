package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "cardio_metric", length = 16)
    private CardioMetric cardioMetric;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_muscle_group", length = 16)
    private PrimaryMuscleGroup primaryMuscleGroup;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exercise_muscle_group_assignments", joinColumns = @JoinColumn(name = "exercise_id"))
    private Set<ExerciseMuscleGroup> muscleGroups = new LinkedHashSet<>();

    @Column(name = "built_in_image_key", length = 100)
    private String builtInImageKey;

    @Column(name = "custom_image_path", length = 255)
    private String customImagePath;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void setPrimaryMuscleGroup(PrimaryMuscleGroup primaryMuscleGroup) {
        this.primaryMuscleGroup = primaryMuscleGroup;
        muscleGroups.removeIf(assignment -> assignment.getRole() == MuscleGroupRole.PRIMARY);
        if (primaryMuscleGroup != null) muscleGroups.add(new ExerciseMuscleGroup(primaryMuscleGroup, MuscleGroupRole.PRIMARY));
    }
}
