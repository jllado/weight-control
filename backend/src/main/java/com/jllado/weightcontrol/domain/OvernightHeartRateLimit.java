package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "overnight_heart_rate_limits")
@Getter
public class OvernightHeartRateLimit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Setter
    @Column(nullable = false, precision = 7, scale = 3)
    private BigDecimal bpm;

    protected OvernightHeartRateLimit() { }

    public OvernightHeartRateLimit(User user, LocalDate effectiveFrom, BigDecimal bpm) {
        this.user = user;
        this.effectiveFrom = effectiveFrom;
        this.bpm = bpm;
    }
}
