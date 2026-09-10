package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Exercise;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Exercise e where e.id = :id")
    Optional<Exercise> findForUpdateById(Long id);

    List<Exercise> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    boolean existsByNameIgnoreCase(String name);
}
