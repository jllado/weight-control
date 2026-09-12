package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.ExerciseRepository;
import com.jllado.weightcontrol.repository.StretchingSetRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class StretchingSetService {
    private final StretchingSetRepository repository;
    private final ExerciseRepository exercises;
    public StretchingSetService(StretchingSetRepository repository, ExerciseRepository exercises) { this.repository = repository; this.exercises = exercises; }
    public List<StretchingSetResponse> findAll(User user) { return repository.findByUserOrderByNameAsc(user).stream().map(StretchingSetResponse::from).toList(); }
    public StretchingSetResponse create(User user, StretchingSetRequest request) {
        StretchingSet set = new StretchingSet();
        set.setUser(user);
        apply(set, request);
        return StretchingSetResponse.from(repository.save(set));
    }
    public StretchingSetResponse update(User user, Long id, StretchingSetRequest request) {
        StretchingSet set = require(user, id);
        apply(set, request);
        return StretchingSetResponse.from(set);
    }
    public void delete(User user, Long id) { repository.delete(require(user, id)); }
    private StretchingSet require(User user, Long id) { return repository.findByIdAndUser(id, user).orElseThrow(() -> new NotFoundException("Stretching set not found")); }
    private void apply(StretchingSet set, StretchingSetRequest request) {
        String name = request.name().trim();
        String normalized = name.toLowerCase(Locale.ROOT);
        repository.findByUserAndNormalizedName(set.getUser(), normalized).filter(existing -> !existing.getId().equals(set.getId())).ifPresent(existing -> {
            throw new BadRequestException("Stretching set name already exists");
        });
        var ids = new HashSet<Long>();
        var entries = new ArrayList<StretchingSetEntry>();
        // Lock in ID order, matching catalog mutations, so referenced exercises cannot change during a save.
        var selected = request.entries().stream().map(StretchingSetEntryRequest::exerciseId).distinct().sorted()
            .map(id -> exercises.findForUpdateById(id).orElseThrow(() -> new NotFoundException("Exercise not found")))
            .collect(java.util.stream.Collectors.toMap(Exercise::getId, exercise -> exercise));
        for (var item : request.entries()) {
            if (!ids.add(item.exerciseId())) { throw new BadRequestException("Each exercise can appear only once in a stretching set"); }
            Exercise exercise = selected.get(item.exerciseId());
            if (exercise.getExerciseType() != ExerciseType.STRETCHING) { throw new BadRequestException("Select stretching exercises only"); }
            if (item.durations().stream().anyMatch(duration -> duration % 5 != 0)) { throw new BadRequestException("Duration must use 5-second steps"); }
            StretchingSetEntry entry = new StretchingSetEntry();
            entry.setStretchingSet(set);
            entry.setExercise(exercise);
            entry.setPosition(entries.size());
            entry.setDurations(new ArrayList<>(item.durations()));
            entry.setStretchingUnit(item.stretchingUnit());
            entry.setBreaths(new ArrayList<>(item.breaths()));
            entries.add(entry);
        }
        set.setName(name);
        set.setNormalizedName(normalized);
        set.getEntries().clear();
        set.getEntries().addAll(entries);
    }
}
