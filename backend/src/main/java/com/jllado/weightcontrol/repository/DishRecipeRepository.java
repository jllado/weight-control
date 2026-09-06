package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.DishRecipe;
import com.jllado.weightcontrol.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DishRecipeRepository extends JpaRepository<DishRecipe, Long> {
    List<DishRecipe> findByUserOrderByNameAsc(User user);
    Optional<DishRecipe> findByUserAndNormalizedName(User user, String normalizedName);
}
