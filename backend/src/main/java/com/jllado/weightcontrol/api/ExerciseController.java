package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.ExerciseRequest;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.ExerciseResponse;
import com.jllado.weightcontrol.service.ExerciseService;
import com.jllado.weightcontrol.service.ExerciseImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workout-exercises")
public class ExerciseController {

    private final ExerciseService service;
    private final ExerciseImageService images;

    public ExerciseController(ExerciseService service, ExerciseImageService images) {
        this.service = service;
        this.images = images;
    }

    @GetMapping
    public List<ExerciseResponse> all() {
        return service.findAll().stream().map(ExerciseResponse::from).toList();
    }

    @PostMapping
    public ExerciseResponse create(@Valid @RequestBody ExerciseRequest request) {
        return ExerciseResponse.from(service.create(request));
    }

    @PutMapping("/{id}")
    public ExerciseResponse update(@PathVariable Long id, @Valid @RequestBody ExerciseRequest request) {
        return ExerciseResponse.from(service.update(id, request));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable Long id) {
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .cacheControl(CacheControl.noStore())
            .body(images.load(id));
    }

    @PostMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExerciseResponse uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ExerciseResponse.from(images.replace(id, file));
    }

    @DeleteMapping("/{id}/image")
    public ExerciseResponse removeImage(@PathVariable Long id) {
        return ExerciseResponse.from(images.remove(id));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
