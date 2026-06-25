package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.ExerciseRequest;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.ExerciseResponse;
import com.jllado.weightcontrol.service.ExerciseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workout-exercises")
public class ExerciseController {

    private final ExerciseService service;

    public ExerciseController(ExerciseService service) {
        this.service = service;
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

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
