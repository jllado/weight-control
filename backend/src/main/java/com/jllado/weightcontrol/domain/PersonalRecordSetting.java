package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "personal_record_settings", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "metric"}))
@Getter
@Setter
public class PersonalRecordSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private PersonalRecordCatalogMetric metric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PersonalRecordMode mode;
}
