package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "routines")
@Getter
@Setter
public class Routine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legacy_firebase_id", unique = true, length = 64)
    private String legacyFirebaseId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "start_date", nullable = false)
    private OffsetDateTime startDate;

    @Column(name = "last_time_date")
    private OffsetDateTime lastTimeDate;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "reminder_time")
    private LocalTime reminderTime;

    @Column(name = "reminder_snoozed_until")
    private OffsetDateTime reminderSnoozedUntil;

    @Column(name = "current_strike", nullable = false)
    private Integer currentStrike;

    @Column(name = "best_strike", nullable = false)
    private Integer bestStrike;

    @ElementCollection(targetClass = RoutineType.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "routine_types", joinColumns = @JoinColumn(name = "routine_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private Set<RoutineType> types = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
