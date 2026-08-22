package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "medication_doses", uniqueConstraints = @UniqueConstraint(columnNames = {"medication_id", "scheduled_at"}))
@Getter
@Setter
public class MedicationDose {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MedicationDoseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MedicationDoseSource source;

    @Column(name = "taken_at")
    private OffsetDateTime takenAt;

    @Column(name = "snoozed_until")
    private OffsetDateTime snoozedUntil;

    @Column(name = "medication_name", nullable = false, length = 255)
    private String medicationName;

    @Column(name = "dose_amount", nullable = false, precision = 10, scale = 3)
    private BigDecimal doseAmount;

    @Column(name = "dose_unit", nullable = false, length = 32)
    private String doseUnit;

    @Column(columnDefinition = "text")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
