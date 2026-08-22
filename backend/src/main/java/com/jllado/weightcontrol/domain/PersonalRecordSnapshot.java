package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "personal_record_snapshots", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "series_key"}))
@Getter
@Setter
public class PersonalRecordSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "series_key", nullable = false, length = 160)
    private String seriesKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PersonalRecordDomain domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private PersonalRecordMetric metric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PersonalRecordDirection direction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @Column(name = "subject_type", length = 16)
    private String subjectType;

    @Column(name = "subject_id")
    private Long subjectId;

    @Column(name = "subject_label", length = 255)
    private String subjectLabel;

    @Column(name = "load_kg", precision = 7, scale = 2)
    private BigDecimal loadKg;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(name = "record_date")
    private LocalDate recordDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private PersonalRecordSourceType sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "line_position")
    private Integer linePosition;

    @Column(name = "segment_position")
    private Integer segmentPosition;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
