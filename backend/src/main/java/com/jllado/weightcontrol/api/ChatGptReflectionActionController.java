package com.jllado.weightcontrol.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.ReflectionOverviewResponse;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.ReflectionResponse;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.SaveReflectionRequest;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.ReflectionValidationError;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.ReflectionValidationResponse;
import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.SaveWeeklyReflectionRequest;
import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.WeeklyReflectionContextResponse;
import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.WeeklyReflectionOverviewResponse;
import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.WeeklyReflectionResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.GptActionNotificationService;
import com.jllado.weightcontrol.service.DashboardReflectionService;
import com.jllado.weightcontrol.service.WeeklyReflectionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/chatgpt-actions/reflections")
public class ChatGptReflectionActionController {

    private final DashboardReflectionService reflectionService;
    private final CurrentUserService currentUserService;
    private final GptActionNotificationService actionNotifications;
    private final WeeklyReflectionService weeklyReflectionService;

    public ChatGptReflectionActionController(
        DashboardReflectionService reflectionService,
        CurrentUserService currentUserService,
        GptActionNotificationService actionNotifications,
        WeeklyReflectionService weeklyReflectionService
    ) {
        this.reflectionService = reflectionService;
        this.currentUserService = currentUserService;
        this.actionNotifications = actionNotifications;
        this.weeklyReflectionService = weeklyReflectionService;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ReflectionValidationResponse invalidReflection(MethodArgumentNotValidException exception) {
        var errors = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new ReflectionValidationError(error.getField(), error.getDefaultMessage()))
            .sorted(Comparator.comparing(ReflectionValidationError::field).thenComparing(ReflectionValidationError::message))
            .toList();
        return new ReflectionValidationResponse(errors.stream()
            .map(error -> error.field() + " " + error.message())
            .collect(Collectors.joining("; ")), errors);
    }

    @GetMapping(value = "/overview", params = "!target")
    public ReflectionOverviewResponse getOverview() {
        return reflectionService.getOverview(currentUserService.requireUser());
    }

    @GetMapping(value = "/overview", params = "target=DAILY")
    public ReflectionOverviewResponse getDailyOverview() {
        return reflectionService.getOverview(currentUserService.requireUser());
    }

    @GetMapping(value = "/overview", params = "target=WEEKLY")
    public WeeklyReflectionOverviewResponse getWeeklyOverview() {
        return weeklyReflectionService.getOverview(currentUserService.requireUser());
    }

    @GetMapping(value = "/{date}/context", params = "!target")
    public JsonNode getContext(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return reflectionService.getContext(currentUserService.requireUser(), date);
    }

    @GetMapping(value = "/{date}/context", params = "target=DAILY")
    public JsonNode getDailyContext(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return reflectionService.getContext(currentUserService.requireUser(), date);
    }

    @GetMapping(value = "/{date}/context", params = "target=WEEKLY")
    public WeeklyReflectionContextResponse getWeeklyContext(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return weeklyReflectionService.getContext(currentUserService.requireUser(), date);
    }

    @PostMapping(value = "/{date}", params = "!target")
    public ReflectionResponse saveReflection(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @Valid @RequestBody SaveReflectionRequest request
    ) {
        return actionNotifications.execute(currentUserService.requireUser(), "Reflection saved", "/reflections",
            () -> ReflectionResponse.from(reflectionService.save(currentUserService.requireUser(), date, request))
        );
    }

    @PostMapping(value = "/{date}", params = "target=DAILY")
    public ReflectionResponse saveDailyReflection(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @Valid @RequestBody SaveReflectionRequest request
    ) {
        return saveReflection(date, request);
    }

    @PostMapping(value = "/{date}", params = "target=WEEKLY")
    public WeeklyReflectionResponse saveWeeklyReflection(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @Valid @RequestBody SaveWeeklyReflectionRequest request
    ) {
        return actionNotifications.execute(currentUserService.requireUser(), "Weekly reflection saved", "/weekly-summaries?date=" + date,
            () -> weeklyReflectionService.save(currentUserService.requireUser(), date, request)
        );
    }
}
