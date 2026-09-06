package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.DishRecipeDtos.*;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.DishRecipeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dishes")
public class DishRecipeController {
    private final DishRecipeService service;
    private final CurrentUserService currentUserService;
    public DishRecipeController(DishRecipeService service, CurrentUserService currentUserService) { this.service = service; this.currentUserService = currentUserService; }
    @GetMapping public List<RecipeResponse> all() { return service.findAll(currentUserService.requireUser()); }
    @GetMapping("/{id}") public RecipeResponse find(@PathVariable Long id) { return service.find(currentUserService.requireUser(), id); }
    @PostMapping public RecipeResponse create(@Valid @RequestBody RecipeRequest request) { return service.create(currentUserService.requireUser(), request); }
    @PutMapping("/{id}") public RecipeResponse update(@PathVariable Long id, @Valid @RequestBody RecipeRequest request) { return service.update(currentUserService.requireUser(), id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(currentUserService.requireUser(), id); }
    @ExceptionHandler(DataIntegrityViolationException.class) @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict() { return "The dish could not be saved. Check that its name is unique."; }
}
