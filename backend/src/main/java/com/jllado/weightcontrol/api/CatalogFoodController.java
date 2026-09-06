package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.MealDtos.*;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.CatalogFoodService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/foods")
public class CatalogFoodController {
    private final CatalogFoodService service;
    private final CurrentUserService currentUserService;
    public CatalogFoodController(CatalogFoodService service, CurrentUserService currentUserService) { this.service = service; this.currentUserService = currentUserService; }
    @GetMapping public List<CatalogFoodResponse> all() { return service.findAll(currentUserService.requireUser()); }
    @PostMapping public CatalogFoodResponse create(@Valid @RequestBody MealDishRequest request) { return service.create(currentUserService.requireUser(), request); }
    @PutMapping("/{id}") public CatalogFoodResponse update(@PathVariable Long id, @Valid @RequestBody MealDishRequest request) { return service.update(currentUserService.requireUser(), id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(currentUserService.requireUser(), id); }
    @ExceptionHandler(DataIntegrityViolationException.class) @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict() { return "The food could not be saved. Check that its name is unique."; }
}
