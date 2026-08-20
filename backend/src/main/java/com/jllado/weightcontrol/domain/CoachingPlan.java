package com.jllado.weightcontrol.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "coaching_plans", uniqueConstraints = @UniqueConstraint(name = "uq_coaching_plans_user", columnNames = "user_id"))
@Getter
@Setter
public class CoachingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String goal;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "principles_json", nullable = false, columnDefinition = "text")
    private List<String> principles;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "priorities_json", nullable = false, columnDefinition = "text")
    private List<String> priorities;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "actions_json", nullable = false, columnDefinition = "text")
    private List<String> actions;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Column(columnDefinition = "text")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
