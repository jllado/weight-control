package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "medication_reminder_times", uniqueConstraints = @UniqueConstraint(columnNames = {"medication_id", "reminder_time"}))
@Getter
@Setter
public class MedicationReminderTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(name = "reminder_time", nullable = false)
    private LocalTime reminderTime;
}
