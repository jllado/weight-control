package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.WeightDtos.WeightRequest;
import com.jllado.weightcontrol.api.dto.WeightDtos.WeightResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.DashboardService;
import com.jllado.weightcontrol.service.WeightService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/weights")
public class WeightController {

    private final WeightService weightService;
    private final CurrentUserService currentUserService;
    private final DashboardService dashboardService;

    public WeightController(WeightService weightService, CurrentUserService currentUserService, DashboardService dashboardService) {
        this.weightService = weightService;
        this.currentUserService = currentUserService;
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public List<WeightResponse> all() {
        User user = currentUserService.requireUser();
        return weightService.findAll(user).stream().map(weight -> WeightResponse.from(
            weight,
            weight.getPhotoFrontPath() == null ? null : "/api/weights/" + weight.getId() + "/photos/front",
            weight.getPhotoLeftPath() == null ? null : "/api/weights/" + weight.getId() + "/photos/left",
            weight.getPhotoRightPath() == null ? null : "/api/weights/" + weight.getId() + "/photos/right"
        )).toList();
    }

    @PostMapping
    public WeightResponse create(@Valid @RequestBody WeightRequest request) {
        User user = currentUserService.requireUser();
        var weight = weightService.create(user, request);
        dashboardService.refreshCurrentStatus(user);
        return WeightResponse.from(weight, null, null, null);
    }

    @PutMapping("/{id}")
    public WeightResponse update(@PathVariable Long id, @Valid @RequestBody WeightRequest request) {
        User user = currentUserService.requireUser();
        var weight = weightService.update(user, id, request);
        dashboardService.refreshCurrentStatus(user);
        return WeightResponse.from(
            weight,
            weight.getPhotoFrontPath() == null ? null : "/api/weights/" + weight.getId() + "/photos/front",
            weight.getPhotoLeftPath() == null ? null : "/api/weights/" + weight.getId() + "/photos/left",
            weight.getPhotoRightPath() == null ? null : "/api/weights/" + weight.getId() + "/photos/right"
        );
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        User user = currentUserService.requireUser();
        weightService.delete(user, id);
        dashboardService.refreshCurrentStatus(user);
    }

    @PostMapping(path = "/{id}/photos/{side}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WeightResponse uploadPhoto(@PathVariable Long id, @PathVariable String side, @RequestParam("file") MultipartFile file) throws IOException {
        User user = currentUserService.requireUser();
        var weight = weightService.uploadPhoto(user, id, side, file);
        return WeightResponse.from(
            weight,
            weight.getPhotoFrontPath() == null ? null : "/api/weights/" + weight.getId() + "/photos/front",
            weight.getPhotoLeftPath() == null ? null : "/api/weights/" + weight.getId() + "/photos/left",
            weight.getPhotoRightPath() == null ? null : "/api/weights/" + weight.getId() + "/photos/right"
        );
    }

    @GetMapping("/{id}/photos/{side}")
    public ResponseEntity<Resource> getPhoto(@PathVariable Long id, @PathVariable String side) {
        User user = currentUserService.requireUser();
        return ResponseEntity.ok(weightService.getPhoto(user, id, side));
    }

    @DeleteMapping("/{id}/photos/{side}")
    public void deletePhoto(@PathVariable Long id, @PathVariable String side) throws IOException {
        User user = currentUserService.requireUser();
        weightService.deletePhoto(user, id, side);
    }
}
