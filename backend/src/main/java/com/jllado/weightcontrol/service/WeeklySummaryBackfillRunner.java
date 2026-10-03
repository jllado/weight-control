package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.SavedWeeklySummary;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.SavedWeeklySummaryRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.weekly-summary.backfill", name = "mode")
public class WeeklySummaryBackfillRunner implements ApplicationRunner {

    private final WeeklySummaryService summaryService;
    private final SavedWeeklySummaryRepository summaryRepository;
    private final UserRepository userRepository;
    private final AppProperties properties;
    private final String mode;

    public WeeklySummaryBackfillRunner(
        WeeklySummaryService summaryService,
        SavedWeeklySummaryRepository summaryRepository,
        UserRepository userRepository,
        AppProperties properties,
        @org.springframework.beans.factory.annotation.Value("${app.weekly-summary.backfill.mode}") String mode
    ) {
        this.summaryService = summaryService;
        this.summaryRepository = summaryRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.mode = mode;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!Arrays.asList("dry-run", "apply").contains(mode)) {
            throw new IllegalArgumentException("app.weekly-summary.backfill.mode must be dry-run or apply");
        }
        User owner = userRepository.findByEmail(properties.weeklySummary().ownerEmail())
            .orElseThrow(() -> new IllegalStateException("Weekly summary owner not found"));
        Optional<LocalDate> earliestDate = summaryService.earliestUnderlyingDate(owner);
        if (earliestDate.isEmpty()) {
            System.out.println("Weekly summary backfill skipped: no underlying recorded data.");
            return;
        }
        LocalDate firstFriday = DateTimes.startOfDashboardWeek(earliestDate.get()).plusDays(6);
        LocalDate latestFriday = summaryService.latestClosedOutcomeWeekEnd(LocalDate.now(DateTimes.USER_ZONE));
        int created = 0;
        int skipped = 0;
        for (LocalDate friday = firstFriday; !friday.isAfter(latestFriday); friday = friday.plusWeeks(1)) {
            boolean exists = summaryRepository.existsByUserAndFridayDate(owner, friday);
            if (mode.equals("apply")) {
                WeeklySummaryService.CreationResult result = summaryService.createForFridayIfMissing(owner, friday, LocalDate.now(DateTimes.USER_ZONE));
                if (!result.created()) {
                    skipped++;
                } else {
                    created++;
                }
                System.out.printf("%s %s%n", result.created() ? "Saved" : "Skipped existing", result.summary().getFridayDate());
                printWarnings(summaryService.readSnapshot(result.summary()).warnings());
            } else if (exists) {
                skipped++;
                System.out.printf("Skipped existing %s%n", friday);
                summaryRepository.findByUserAndFridayDate(owner, friday)
                    .ifPresent(summary -> printWarnings(summaryService.readSnapshot(summary).warnings()));
            } else {
                created++;
                System.out.printf("Would save %s%n", friday);
                printWarnings(summaryService.outcomeWarnings(owner, friday));
            }
        }
        System.out.printf("Weekly summary backfill %s: %d created, %d existing, %s through %s%n",
            mode, created, skipped, firstFriday, latestFriday);
    }

    private void printWarnings(java.util.List<String> warnings) {
        warnings.forEach(warning -> System.out.printf("  Warning: %s%n", warning));
    }
}
