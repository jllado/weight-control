package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Exercise;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {
    List<Exercise> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    boolean existsByNameIgnoreCase(String name);
}
