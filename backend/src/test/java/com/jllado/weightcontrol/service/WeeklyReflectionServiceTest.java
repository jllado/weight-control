package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.SaveWeeklyReflectionRequest;
import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.WeeklyReflectionSection;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.util.DateTimes;
import com.jllado.weightcontrol.domain.SavedWeeklySummary;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.WeeklyReflection;
import com.jllado.weightcontrol.repository.SavedWeeklySummaryRepository;
import com.jllado.weightcontrol.repository.WeeklyReflectionRepository;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WeeklyReflectionServiceTest {

    @Test
    void confirmedWeeklyUpdateReplacesOnlyReflectionLinkedToOwnedSavedSummary() {
        User user = new User();
        user.setId(7L);
        SavedWeeklySummary summary = new SavedWeeklySummary();
        summary.setUser(user);
        summary.setFridayDate(LocalDate.of(2026, 8, 14));
        WeeklyReflection existing = new WeeklyReflection();
        existing.setWeeklySummary(summary);
        var summaries = mock(SavedWeeklySummaryRepository.class);
        var reflections = mock(WeeklyReflectionRepository.class);
        when(summaries.findByUserAndFridayDateForUpdate(user, summary.getFridayDate())).thenReturn(Optional.of(summary));
        when(reflections.findByWeeklySummary(summary)).thenReturn(Optional.of(existing));
        when(reflections.save(existing)).thenReturn(existing);
        var service = new WeeklyReflectionService(summaries, reflections, mock(WeeklySummaryService.class), properties());
        WeeklyReflectionSection section = new WeeklyReflectionSection("Supported evidence and an unknown gap.", "Review the next saved week.");
        SaveWeeklyReflectionRequest request = new SaveWeeklyReflectionRequest(
            true, "Week review", "A concise weekly review.", section, section, section, section, section, section,
            List.of("Continue the agreed routine")
        );

        var response = service.save(user, summary.getFridayDate(), request);

        assertEquals(summary.getFridayDate(), response.fridayDate());
        assertEquals("Week review", existing.getTitle());
        assertEquals("A concise weekly review.", existing.getSummary());
        assertEquals("Supported evidence and an unknown gap.", existing.getBodyCompositionSummary());
        assertEquals(List.of("Continue the agreed routine"), existing.getNextWeekActions());
        verify(summaries).findByUserAndFridayDateForUpdate(user, summary.getFridayDate());
        verify(reflections).save(existing);
    }

    @ParameterizedTest
    @CsvSource({"2026-10-09,2026-10-02", "2026-10-10,2026-10-09", "2026-10-11,2026-10-09", "2026-10-12,2026-10-09"})
    void overviewMakesTheCompletedWeekEligibleFromSaturday(String todayValue, String fridayValue) {
        User user = new User();
        var summaries = mock(SavedWeeklySummaryRepository.class);
        var summaryService = mock(WeeklySummaryService.class);
        LocalDate today = LocalDate.parse(todayValue);
        when(summaryService.latestCompletedWeekEnd(today)).thenCallRealMethod();
        var service = new WeeklyReflectionService(summaries, mock(WeeklyReflectionRepository.class), summaryService, properties());

        try (var dates = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            dates.when(() -> LocalDate.now(DateTimes.USER_ZONE)).thenReturn(today);
            assertEquals(LocalDate.parse(fridayValue), service.getOverview(user).latestEligibleFriday());
        }
    }

    private AppProperties properties() {
        return new AppProperties(
            new AppProperties.Auth("client", "test-jwt-secret-test-jwt-secret", 7, false),
            new AppProperties.Cors(List.of()),
            new AppProperties.Storage(Path.of("data")),
            new AppProperties.ChatGptActions("", "owner@example.com", "https://test.example", "test-file-signing-secret-32-bytes-long"),
            new AppProperties.Push(false, "", "", "", ""),
            new AppProperties.WeeklySummary(false, "owner@example.com", "owner@example.com", "sender@example.com", "https://weight.example")
        );
    }
}
