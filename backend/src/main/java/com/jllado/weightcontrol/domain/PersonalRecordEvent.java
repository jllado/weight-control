package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "personal_record_events", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "event_key"}))
@Getter
@Setter
public class PersonalRecordEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "event_key", nullable = false, length = 64)
    private String eventKey;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private PersonalRecordDomain domain;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 48)
    private PersonalRecordMetric metric;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private PersonalRecordDirection direction;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private PersonalRecordEventKind kind;
    @Column(name = "record_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal value;
    @Column(name = "previous_value", precision = 12, scale = 2)
    private BigDecimal previousValue;
    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;
    @Column(name = "current_record", nullable = false)
    private boolean currentRecord;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "exercise_id")
    private Exercise exercise;
    @Column(name = "subject_type", length = 16) private String subjectType;
    @Column(name = "subject_id") private Long subjectId;
    @Column(name = "subject_label", length = 255) private String subjectLabel;
    @Column(name = "load_kg", precision = 7, scale = 2) private BigDecimal loadKg;
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false, length = 16)
    private PersonalRecordSourceType sourceType;
    @Column(name = "source_id") private Long sourceId;
    @Column(name = "line_position") private Integer linePosition;
    @Column(name = "segment_position") private Integer segmentPosition;
}
