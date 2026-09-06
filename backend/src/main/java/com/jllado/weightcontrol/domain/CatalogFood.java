package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "catalog_foods", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "normalized_name"}))
@Getter
@Setter
public class CatalogFood extends FoodPortion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "normalized_name", nullable = false, length = 765)
    private String normalizedName;
    @Column(nullable = false)
    private boolean deleted;
}
