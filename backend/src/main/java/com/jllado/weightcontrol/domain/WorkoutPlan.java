package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "workout_plans")
@Getter @Setter
public class WorkoutPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "review_date", nullable = false) private LocalDate reviewDate;
    @Column(length = 500) private String notes;
    @Convert(converter = WorkoutPlanDaysJsonConverter.class) @Column(name = "days_json", nullable = false, columnDefinition = "longtext") private List<WorkoutPlanDay> days;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "update_token", nullable = false, length = 36) private String updateToken;
}
