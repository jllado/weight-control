package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "google_sub", unique = true, length = 255)
    private String googleSub;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "dashboard_anchor_date")
    private LocalDate dashboardAnchorDate;

    @Column(name = "last_completed_dashboard_date")
    private LocalDate lastCompletedDashboardDate;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "height_cm")
    private Integer heightCm;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex")
    private UserSex sex;

    @Enumerated(EnumType.STRING)
    @Column(name = "fitness_level")
    private UserFitnessLevel fitnessLevel;

    @Column(name = "takes_medication", nullable = false)
    private boolean takesMedication;

    @Column(name = "typical_calories_saturday", nullable = false)
    private int typicalCaloriesSaturday;

    @Column(name = "typical_calories_sunday", nullable = false)
    private int typicalCaloriesSunday;

    @Column(name = "typical_calories_monday", nullable = false)
    private int typicalCaloriesMonday;

    @Column(name = "typical_calories_tuesday", nullable = false)
    private int typicalCaloriesTuesday;

    @Column(name = "typical_calories_wednesday", nullable = false)
    private int typicalCaloriesWednesday;

    @Column(name = "typical_calories_thursday", nullable = false)
    private int typicalCaloriesThursday;

    @Column(name = "typical_calories_friday", nullable = false)
    private int typicalCaloriesFriday;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
