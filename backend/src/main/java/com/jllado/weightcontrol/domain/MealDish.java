package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "meal_dishes", uniqueConstraints = @UniqueConstraint(columnNames = {"meal_id", "position"}))
@Getter
@Setter
public class MealDish extends FoodPortion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_id", nullable = false)
    private Meal meal;

    @Column(nullable = false)
    private Integer position;

}
