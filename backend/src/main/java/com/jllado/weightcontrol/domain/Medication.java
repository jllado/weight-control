package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "medications")
@Getter
@Setter
public class Medication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "dose_amount", nullable = false, precision = 10, scale = 3)
    private BigDecimal doseAmount;

    @Column(name = "dose_unit", nullable = false, length = 32)
    private String doseUnit;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "repeat_every", nullable = false)
    private Integer repeatEvery;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_unit", nullable = false, length = 16)
    private MedicationRepeatUnit repeatUnit;

    @Column(nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "medication", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("reminderTime asc")
    private Set<MedicationReminderTime> reminderTimes = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
