package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.repository.ExerciseRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class ExerciseImageService {
    private final ExerciseRepository repository;
    private final ExerciseImageStorage storage;

    public ExerciseImageService(ExerciseRepository repository, ExerciseImageStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public Resource load(Long id) {
        Exercise exercise = repository.findById(id).orElseThrow(() -> new NotFoundException("Exercise not found"));
        if (exercise.getCustomImagePath() != null) return storage.load(exercise.getCustomImagePath());
        if (exercise.getBuiltInImageKey() == null) throw new NotFoundException("Exercise picture not found");
        return new ClassPathResource("exercise-images/" + exercise.getBuiltInImageKey() + ".jpg");
    }

    public Exercise replace(Long id, MultipartFile file) {
        Exercise exercise = requireForUpdate(id);
        String path = storage.store(id, file);
        storage.deleteAfterCommit(exercise.getCustomImagePath());
        exercise.setCustomImagePath(path);
        return repository.save(exercise);
    }

    public Exercise remove(Long id) {
        Exercise exercise = requireForUpdate(id);
        storage.deleteAfterCommit(exercise.getCustomImagePath());
        exercise.setCustomImagePath(null);
        return repository.save(exercise);
    }

    private Exercise requireForUpdate(Long id) {
        return repository.findForUpdateById(id).orElseThrow(() -> new NotFoundException("Exercise not found"));
    }
}
