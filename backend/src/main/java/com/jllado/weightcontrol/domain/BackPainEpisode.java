package com.jllado.weightcontrol.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "back_pain_episodes")
@Getter
@Setter
public class BackPainEpisode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "episode_date", nullable = false)
    private LocalDate episodeDate;

    @Column(name = "episode_time")
    private LocalTime episodeTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BackRegion region;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private BackSide side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BackPainSeverity severity;

    @Column(length = 500)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
