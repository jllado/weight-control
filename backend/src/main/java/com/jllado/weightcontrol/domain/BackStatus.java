package com.jllado.weightcontrol.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "back_statuses", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "status_date"}))
@Getter
@Setter
public class BackStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "status_date", nullable = false)
    private LocalDate statusDate;

    @Column(name = "lower_pain", nullable = false)
    private Integer lowerPain;

    @Column(name = "lower_stiffness", nullable = false)
    private Integer lowerStiffness;

    @Column(name = "lower_activity_limitation", nullable = false)
    private Integer lowerActivityLimitation;

    @Column(name = "middle_pain", nullable = false)
    private Integer middlePain;

    @Column(name = "middle_stiffness", nullable = false)
    private Integer middleStiffness;

    @Column(name = "middle_activity_limitation", nullable = false)
    private Integer middleActivityLimitation;

    @Column(name = "upper_pain", nullable = false)
    private Integer upperPain;

    @Column(name = "upper_stiffness", nullable = false)
    private Integer upperStiffness;

    @Column(name = "upper_activity_limitation", nullable = false)
    private Integer upperActivityLimitation;

    @Column(length = 500)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
