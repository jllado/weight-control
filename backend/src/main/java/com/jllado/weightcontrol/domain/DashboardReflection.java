package com.jllado.weightcontrol.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "dashboard_reflections")
@Getter
@Setter
public class DashboardReflection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "reflection_date", nullable = false)
    private LocalDate reflectionDate;

    @Column(name = "window_start", nullable = false)
    private LocalDate windowStart;

    @Column(name = "window_end", nullable = false)
    private LocalDate windowEnd;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "positive_signals_json", nullable = false, columnDefinition = "text")
    private List<String> positiveSignals;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "watchouts_json", nullable = false, columnDefinition = "text")
    private List<String> watchouts;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "next_actions_json", nullable = false, columnDefinition = "text")
    private List<String> nextActions;
}
