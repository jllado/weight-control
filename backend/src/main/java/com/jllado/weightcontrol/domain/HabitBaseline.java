package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "habit_baselines")
@Getter
@Setter
public class HabitBaseline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "habit_id", nullable = false, unique = true)
    private Habit habit;

    @Column(name = "completion_total", nullable = false)
    private Integer completionTotal;

    @Column(name = "current_streak", nullable = false)
    private Integer currentStreak;

    @Column(name = "best_streak", nullable = false)
    private Integer bestStreak;

    @Column(name = "last_date")
    private LocalDate lastDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
