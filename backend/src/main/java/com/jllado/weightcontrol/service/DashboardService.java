package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.DashboardDtos;
import com.jllado.weightcontrol.api.dto.DashboardDtos.DailyStatusResponse;
import com.jllado.weightcontrol.api.dto.DashboardDtos.MoodDaySummary;
import com.jllado.weightcontrol.api.dto.DashboardDtos.OutcomeMetricsResponse;
import com.jllado.weightcontrol.api.dto.DashboardDtos.WeekStatusResponse;
import com.jllado.weightcontrol.api.dto.DashboardDtos.WinsAndMissesStatusResponse;
import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.FastingPeriodResponse;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.repository.WorkoutPlanRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import static java.time.DayOfWeek.*;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class DashboardService {

    private final DailyStatusSnapshotService snapshotService;
    private final UserRepository userRepository;
    private final MoodService moodService;
    private final DecisionOutcomeService decisionOutcomeService;
    private final FastingPeriodService fastingPeriodService;
    private final WorkoutPlanRepository workoutPlans;

    public DashboardService(
        DailyStatusSnapshotService snapshotService,
        UserRepository userRepository,
        MoodService moodService,
        DecisionOutcomeService decisionOutcomeService,
        FastingPeriodService fastingPeriodService,
        WorkoutPlanRepository workoutPlans
    ) {
        this.snapshotService = snapshotService;
        this.userRepository = userRepository;
        this.moodService = moodService;
        this.decisionOutcomeService = decisionOutcomeService;
        this.fastingPeriodService = fastingPeriodService;
        this.workoutPlans = workoutPlans;
    }

    public DashboardDtos.DashboardResponse getDashboard(User user) {
        return getDashboard(user, requireAnchorDate(user));
    }

    public DashboardDtos.DashboardResponse getDashboard(User user, LocalDate selectedDate) {
        DailyStatus dailyStatus = snapshotService.getOrBuild(user, selectedDate);
        DailyStatus lastWeek = snapshotService.getLastWeekDailyStatus(user, selectedDate);
        Map<LocalDate, List<Mood>> moods = moodService.findByDateRange(user, selectedDate.minusDays(37), selectedDate);
        return new DashboardDtos.DashboardResponse(
            selectedDate,
            user.getLastCompletedDashboardDate(),
            fastingPeriodService.findActiveAutomaticPeriod(user).map(FastingPeriodResponse::from).orElse(null),
            toDailyStatusResponse(dailyStatus, moods),
            toDailyStatusResponse(lastWeek, moods),
            toWeek(user, snapshotService.getWeek(user, selectedDate), moods),
            toWeek(user, snapshotService.getFullWeek(user, WeeklyMetricsCalculator.comparisonPeriods(user, selectedDate).previous().end()), moods),
            toWinsAndMissesStatusResponse(decisionOutcomeService.summarize(user, selectedDate)),
            workoutPlans.findDueReviewDate(user, LocalDate.now(com.jllado.weightcontrol.util.DateTimes.USER_ZONE)).orElse(null)
        );
    }

    public DashboardDtos.DashboardResponse advance(User user) {
        LocalDate today = LocalDate.now(com.jllado.weightcontrol.util.DateTimes.USER_ZONE);
        LocalDate anchorDate = requireAnchorDate(user);
        if (anchorDate.isBefore(today)) {
            user.setDashboardAnchorDate(anchorDate.plusDays(1));
            userRepository.save(user);
        }
        snapshotService.getOrBuild(user, user.getDashboardAnchorDate());
        return getDashboard(user);
    }

    public DashboardDtos.DashboardResponse retreat(User user) {
        LocalDate anchorDate = requireAnchorDate(user);
        user.setDashboardAnchorDate(anchorDate.minusDays(1));
        userRepository.save(user);
        snapshotService.getOrBuild(user, user.getDashboardAnchorDate());
        return getDashboard(user);
    }

    public DashboardDtos.DashboardResponse refresh(User user) {
        snapshotService.rebuild(user, requireAnchorDate(user));
        return getDashboard(user);
    }

    public DashboardDtos.DashboardResponse setDashboardCompletion(User user, boolean completed) {
        LocalDate anchorDate = requireAnchorDate(user);
        if (completed) {
            user.setLastCompletedDashboardDate(anchorDate);
        } else if (anchorDate.equals(user.getLastCompletedDashboardDate())) {
            user.setLastCompletedDashboardDate(anchorDate.minusDays(1));
        }
        userRepository.save(user);
        return getDashboard(user);
    }

    public void refreshCurrentStatus(User user) {
        if (user.getDashboardAnchorDate() != null) {
            snapshotService.rebuild(user, user.getDashboardAnchorDate());
        }
    }

    private LocalDate requireAnchorDate(User user) {
        if (user.getDashboardAnchorDate() == null) {
            throw new BadRequestException("Dashboard anchor date not set");
        }
        return user.getDashboardAnchorDate();
    }

    private DailyStatusResponse toDailyStatusResponse(DailyStatus status, Map<LocalDate, List<Mood>> moods) {
        List<Mood> dailyMoods = moods.getOrDefault(status.getStatusDate(), List.of());
        return DailyStatusResponse.from(
            status,
            MoodDaySummary.from(dailyMoods, moodService.average(dailyMoods)),
            moodAverage(moods, status.getStatusDate().minusDays(29), status.getStatusDate())
        );
    }

    private WeekStatusResponse toWeek(User user, List<DailyStatus> statuses, Map<LocalDate, List<Mood>> moods) {
        var days = new java.util.EnumMap<java.time.DayOfWeek, DailyStatusResponse>(java.time.DayOfWeek.class);
        for (DailyStatus status : statuses) {
            days.put(status.getStatusDate().getDayOfWeek(), toDailyStatusResponse(status, moods));
        }
        BigDecimal moodAverage = null;
        if (!statuses.isEmpty()) {
            var period = PlanningWeekService.period(user, statuses.getFirst().getStatusDate());
            moodAverage = moodService.getAverage(user, period.start(), period.end());
        }
        return new WeekStatusResponse(
            days.get(SATURDAY), days.get(SUNDAY), days.get(MONDAY), days.get(TUESDAY), days.get(WEDNESDAY), days.get(THURSDAY), days.get(FRIDAY),
            average(statuses, DailyStatus::getRoutinesPercentage),
            average(statuses, DailyStatus::getWeightPercentage),
            average(statuses, DailyStatus::getBloodPressurePercentage),
            average(statuses, DailyStatus::getFlexibilityPercentage),
            average(statuses, DailyStatus::getMindPercentage),
            moodAverage
        );
    }

    private BigDecimal moodAverage(Map<LocalDate, List<Mood>> moods, LocalDate startDate, LocalDate endDate) {
        return moodService.average(moods.entrySet().stream()
            .filter(entry -> !entry.getKey().isBefore(startDate) && !entry.getKey().isAfter(endDate))
            .flatMap(entry -> entry.getValue().stream())
            .toList());
    }

    private BigDecimal average(List<DailyStatus> statuses, java.util.function.Function<DailyStatus, BigDecimal> extractor) {
        if (statuses.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return statuses.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(statuses.size()), 2, RoundingMode.HALF_UP);
    }

    private WinsAndMissesStatusResponse toWinsAndMissesStatusResponse(DecisionOutcomeService.Summary summary) {
        return new WinsAndMissesStatusResponse(
            toOutcomeMetricsResponse(summary.selectedDate()),
            toOutcomeMetricsResponse(summary.rolling30Days()),
            toOutcomeMetricsResponse(summary.previous30Days()),
            toOutcomeMetricsResponse(summary.allTime()),
            summary.winRateChange(),
            summary.currentWinStreak()
        );
    }

    private OutcomeMetricsResponse toOutcomeMetricsResponse(DecisionOutcomeService.Metrics metrics) {
        return new OutcomeMetricsResponse(metrics.wins(), metrics.misses(), metrics.winRate());
    }
}
