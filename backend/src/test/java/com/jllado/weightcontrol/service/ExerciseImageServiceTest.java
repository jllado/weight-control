package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.ExerciseResponse;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.repository.ExerciseRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ExerciseImageServiceTest {
    private final ExerciseRepository repository = mock(ExerciseRepository.class);
    private final ExerciseImageStorage storage = mock(ExerciseImageStorage.class);
    private final ExerciseImageService service = new ExerciseImageService(repository, storage);

    @Test
    void replacesCustomPictureAndRestoresBuiltInAfterRemoval() {
        Exercise exercise = new Exercise(); exercise.setId(1L); exercise.setName("Renamed push-up"); exercise.setBuiltInImageKey("push-up");
        exercise.setCustomImagePath("exercise-images/1/old.jpg");
        when(repository.findForUpdateById(1L)).thenReturn(Optional.of(exercise));
        when(repository.save(exercise)).thenReturn(exercise);
        var file = new MockMultipartFile("file", new byte[]{1});
        when(storage.store(1L, file)).thenReturn("exercise-images/1/new.jpg");
        var uploaded = ExerciseResponse.from(service.replace(1L, file));
        assertEquals("/api/workout-exercises/1/image?v=new.jpg", uploaded.imageUrl()); assertTrue(uploaded.hasCustomImage());
        verify(storage).deleteAfterCommit("exercise-images/1/old.jpg");
        var restored = ExerciseResponse.from(service.remove(1L));
        assertEquals("/api/workout-exercises/1/image?v=push-up", restored.imageUrl()); assertFalse(restored.hasCustomImage());
        verify(storage).deleteAfterCommit("exercise-images/1/new.jpg");
        exercise.setBuiltInImageKey(null); assertNull(ExerciseResponse.from(exercise).imageUrl());
    }

    @Test
    void failedReplacementPreservesExistingPicture() {
        Exercise exercise = new Exercise(); exercise.setCustomImagePath("old.jpg");
        when(repository.findForUpdateById(1L)).thenReturn(Optional.of(exercise));
        var file = new MockMultipartFile("file", new byte[]{1});
        when(storage.store(1L, file)).thenThrow(new BadRequestException("Invalid image"));
        assertThrows(BadRequestException.class, () -> service.replace(1L, file));
        assertEquals("old.jpg", exercise.getCustomImagePath());
        verify(storage, never()).deleteAfterCommit(any()); verify(repository, never()).save(any());
    }
}
