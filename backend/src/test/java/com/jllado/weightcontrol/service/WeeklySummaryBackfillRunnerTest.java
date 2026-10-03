package com.jllado.weightcontrol.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.SavedWeeklySummary;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.SavedWeeklySummaryRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class WeeklySummaryBackfillRunnerTest {

    @Test
    void dryRunListsEligibleWeeksAndWarningsWithoutCreatingSummaries() {
        User owner = owner();
        WeeklySummaryService service = mock(WeeklySummaryService.class);
        SavedWeeklySummaryRepository summaries = mock(SavedWeeklySummaryRepository.class);
        UserRepository users = mock(UserRepository.class);
        LocalDate friday = LocalDate.of(2026, 8, 14);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(service.earliestUnderlyingDate(owner)).thenReturn(Optional.of(LocalDate.of(2026, 8, 8)));
        when(service.latestClosedOutcomeWeekEnd(any())).thenReturn(friday);
        when(summaries.existsByUserAndFridayDate(owner, friday)).thenReturn(false);
        when(service.outcomeWarnings(owner, friday)).thenReturn(List.of("No blood pressure measurement was recorded Friday, Saturday, or Sunday."));

        runner(service, summaries, users, "dry-run").run(new DefaultApplicationArguments());

        verify(service).outcomeWarnings(owner, friday);
        verify(service, never()).createForFridayIfMissing(any(), any(), any());
        verify(summaries).existsByUserAndFridayDate(owner, friday);
    }

    @Test
    void applyRerunSkipsExistingWeeksAndCreatesOnlyMissingOnes() {
        User owner = owner();
        WeeklySummaryService service = mock(WeeklySummaryService.class);
        SavedWeeklySummaryRepository summaries = mock(SavedWeeklySummaryRepository.class);
        UserRepository users = mock(UserRepository.class);
        LocalDate firstFriday = LocalDate.of(2026, 8, 14);
        LocalDate secondFriday = firstFriday.plusWeeks(1);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(service.earliestUnderlyingDate(owner)).thenReturn(Optional.of(LocalDate.of(2026, 8, 8)));
        when(service.latestClosedOutcomeWeekEnd(any())).thenReturn(secondFriday);
        when(summaries.existsByUserAndFridayDate(owner, firstFriday)).thenReturn(false);
        when(summaries.existsByUserAndFridayDate(owner, secondFriday)).thenReturn(true);
        SavedWeeklySummary first = saved(owner, firstFriday);
        SavedWeeklySummary second = saved(owner, secondFriday);
        when(service.createForFridayIfMissing(eq(owner), eq(firstFriday), any())).thenReturn(new WeeklySummaryService.CreationResult(first, true));
        when(service.createForFridayIfMissing(eq(owner), eq(secondFriday), any())).thenReturn(new WeeklySummaryService.CreationResult(second, false));
        when(service.readSnapshot(first)).thenReturn(snapshot(firstFriday));
        when(service.readSnapshot(second)).thenReturn(snapshot(secondFriday));

        runner(service, summaries, users, "apply").run(new DefaultApplicationArguments());

        verify(service).createForFridayIfMissing(eq(owner), eq(firstFriday), any());
        verify(service).createForFridayIfMissing(eq(owner), eq(secondFriday), any());
        verify(service).readSnapshot(first);
        verify(service).readSnapshot(second);
        verify(service, never()).outcomeWarnings(any(), any());
    }

    private WeeklySummaryBackfillRunner runner(WeeklySummaryService service, SavedWeeklySummaryRepository summaries, UserRepository users, String mode) {
        return new WeeklySummaryBackfillRunner(service, summaries, users, properties(), mode);
    }

    private WeeklySummarySnapshot snapshot(LocalDate friday) {
        return new WeeklySummarySnapshot(1, friday.minusDays(6), friday, null, null, List.of(), null, List.of(), List.of());
    }

    private SavedWeeklySummary saved(User owner, LocalDate friday) {
        SavedWeeklySummary summary = new SavedWeeklySummary();
        summary.setUser(owner);
        summary.setFridayDate(friday);
        summary.setSnapshotJson("{}");
        return summary;
    }

    private User owner() {
        User user = new User();
        user.setId(1L);
        user.setEmail("owner@example.com");
        return user;
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
