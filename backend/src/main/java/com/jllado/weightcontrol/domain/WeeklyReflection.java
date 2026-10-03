package com.jllado.weightcontrol.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "weekly_reflections", uniqueConstraints = @UniqueConstraint(name = "uq_weekly_reflections_summary", columnNames = "weekly_summary_id"))
@Getter
@Setter
public class WeeklyReflection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "weekly_summary_id", nullable = false, updatable = false)
    private SavedWeeklySummary weeklySummary;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(name = "body_composition_summary", nullable = false, length = 400)
    private String bodyCompositionSummary;

    @Column(name = "body_composition_next_action", nullable = false, length = 200)
    private String bodyCompositionNextAction;

    @Column(name = "blood_pressure_summary", nullable = false, length = 400)
    private String bloodPressureSummary;

    @Column(name = "blood_pressure_next_action", nullable = false, length = 200)
    private String bloodPressureNextAction;

    @Column(name = "routines_summary", nullable = false, length = 400)
    private String routinesSummary;

    @Column(name = "routines_next_action", nullable = false, length = 200)
    private String routinesNextAction;

    @Column(name = "nutrition_summary", nullable = false, length = 400)
    private String nutritionSummary;

    @Column(name = "nutrition_next_action", nullable = false, length = 200)
    private String nutritionNextAction;

    @Column(name = "training_recovery_summary", nullable = false, length = 400)
    private String trainingRecoverySummary;

    @Column(name = "training_recovery_next_action", nullable = false, length = 200)
    private String trainingRecoveryNextAction;

    @Column(name = "goal_progress_summary", nullable = false, length = 400)
    private String goalProgressSummary;

    @Column(name = "goal_progress_next_action", nullable = false, length = 200)
    private String goalProgressNextAction;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "next_week_actions_json", nullable = false, columnDefinition = "text")
    private List<String> nextWeekActions;
}
