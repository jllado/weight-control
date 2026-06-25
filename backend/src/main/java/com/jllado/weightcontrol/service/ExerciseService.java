package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.ExerciseRequest;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.repository.ExerciseRepository;
import com.jllado.weightcontrol.repository.WorkoutLineRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class ExerciseService {

    private final ExerciseRepository repository;
    private final WorkoutLineRepository workoutLineRepository;

    public ExerciseService(ExerciseRepository repository, WorkoutLineRepository workoutLineRepository) {
        this.repository = repository;
        this.workoutLineRepository = workoutLineRepository;
    }

    public List<Exercise> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    public Exercise create(ExerciseRequest request) {
        ensureUniqueName(request.name(), null);
        Exercise exercise = new Exercise();
        apply(exercise, request);
        return repository.save(exercise);
    }

    public Exercise update(Long id, ExerciseRequest request) {
        ensureUniqueName(request.name(), id);
        Exercise exercise = require(id);
        if (exercise.getTrackingMode() != request.trackingMode() && workoutLineRepository.existsByExercise(exercise)) {
            throw new BadRequestException("Exercise tracking mode cannot change after it has been used");
        }
        apply(exercise, request);
        return repository.save(exercise);
    }

    public void delete(Long id) {
        Exercise exercise = require(id);
        if (workoutLineRepository.existsByExercise(exercise)) {
            throw new BadRequestException("Exercise cannot be deleted because it is already used in workouts");
        }
        repository.delete(exercise);
    }

    public Exercise require(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Exercise not found"));
    }

    private void apply(Exercise exercise, ExerciseRequest request) {
        exercise.setName(request.name().trim());
        exercise.setDescription(request.description().trim());
        exercise.setTrackingMode(request.trackingMode());
    }

    private void ensureUniqueName(String name, Long id) {
        boolean exists = id == null
            ? repository.existsByNameIgnoreCase(name.trim())
            : repository.existsByNameIgnoreCaseAndIdNot(name.trim(), id);
        if (exists) {
            throw new BadRequestException("Exercise name already exists");
        }
    }
}
