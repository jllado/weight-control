package com.jllado.weightcontrol.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseMuscleGroup implements Serializable {
    @Enumerated(EnumType.STRING)
    @Column(name = "muscle_group", nullable = false, length = 16)
    private PrimaryMuscleGroup muscleGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private MuscleGroupRole role;
}
