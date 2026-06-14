package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "routine_checkins", uniqueConstraints = @UniqueConstraint(columnNames = {"routine_id", "checked_at"}))
@Getter
@Setter
public class RoutineCheckin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id", nullable = false)
    private Routine routine;

    @Column(name = "checked_at", nullable = false)
    private OffsetDateTime checkedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
