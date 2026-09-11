package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.StretchingSetEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StretchingSetEntryRepository extends JpaRepository<StretchingSetEntry, Long> {
    boolean existsByExercise(Exercise exercise);
}
