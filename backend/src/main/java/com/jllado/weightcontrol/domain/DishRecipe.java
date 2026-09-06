package com.jllado.weightcontrol.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "dish_recipes", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "normalized_name"}))
@Getter
@Setter
public class DishRecipe {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 255)
    private String name;
    @Column(name = "normalized_name", nullable = false, length = 765)
    private String normalizedName;
    @Column(nullable = false, precision = 11, scale = 3)
    private BigDecimal servings;
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<RecipeIngredient> ingredients = new ArrayList<>();
}
