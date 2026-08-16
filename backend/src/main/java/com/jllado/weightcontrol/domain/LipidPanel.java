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
@Table(name = "lipid_panels", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "panel_date"}))
@Getter
@Setter
public class LipidPanel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "panel_date", nullable = false)
    private LocalDate panelDate;

    @Column(name = "total_cholesterol", nullable = false)
    private Integer totalCholesterol;

    @Column(name = "hdl_cholesterol", nullable = false)
    private Integer hdlCholesterol;

    @Column(name = "ldl_cholesterol", nullable = false)
    private Integer ldlCholesterol;

    @Column(nullable = false)
    private Integer triglycerides;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
