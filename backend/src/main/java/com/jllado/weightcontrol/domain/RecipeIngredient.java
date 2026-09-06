package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "recipe_ingredients", uniqueConstraints = @UniqueConstraint(columnNames = {"recipe_id", "position"}))
@Getter
@Setter
public class RecipeIngredient extends FoodPortion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private DishRecipe recipe;
    @Column(nullable = false)
    private Integer position;
}
