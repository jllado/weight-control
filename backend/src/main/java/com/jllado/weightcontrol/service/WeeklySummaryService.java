package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineCheckin;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import com.jllado.weightcontrol.repository.DecisionOutcomeRepository;
import com.jllado.weightcontrol.repository.MoodRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.repository.SleepRepository;
import com.jllado.weightcontrol.repository.WeightRepository;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WeeklySummaryService {

    public static final DayOfWeek DELIVERY_DAY = DayOfWeek.SUNDAY;
    public static final LocalTime DELIVERY_TIME = LocalTime.of(8, 0);
    private static final int YEAR_COMPARISON_WEEKS = 52;
    private static final Logger LOG = LoggerFactory.getLogger(WeeklySummaryService.class);

    private final WeightRepository weightRepository;
    private final BloodPressureRepository bloodPressureRepository;
    private final MoodRepository moodRepository;
    private final SleepRepository sleepRepository;
    private final CalorieService calorieService;
    private final WorkoutRepository workoutRepository;
    private final DecisionOutcomeRepository decisionOutcomeRepository;
    private final RoutineRepository routineRepository;
    private final RoutineCheckinRepository routineCheckinRepository;
    private final DailyStatusSnapshotService snapshotService;
    private final WeeklyMetricsCalculator metricsCalculator;
    private final WeeklySummaryMailSender mailSender;
    private final AppProperties properties;

    public WeeklySummaryService(
        WeightRepository weightRepository,
        BloodPressureRepository bloodPressureRepository,
        MoodRepository moodRepository,
        SleepRepository sleepRepository,
        CalorieService calorieService,
        WorkoutRepository workoutRepository,
        DecisionOutcomeRepository decisionOutcomeRepository,
        RoutineRepository routineRepository,
        RoutineCheckinRepository routineCheckinRepository,
        DailyStatusSnapshotService snapshotService,
        WeeklyMetricsCalculator metricsCalculator,
        WeeklySummaryMailSender mailSender,
        AppProperties properties
    ) {
        this.weightRepository = weightRepository;
        this.bloodPressureRepository = bloodPressureRepository;
        this.moodRepository = moodRepository;
        this.sleepRepository = sleepRepository;
        this.calorieService = calorieService;
        this.workoutRepository = workoutRepository;
        this.decisionOutcomeRepository = decisionOutcomeRepository;
        this.routineRepository = routineRepository;
        this.routineCheckinRepository = routineCheckinRepository;
        this.snapshotService = snapshotService;
        this.metricsCalculator = metricsCalculator;
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Transactional
    public void send(User user) {
        requireEnabled();
        LocalDate periodEnd = latestCompletedWeekEnd(LocalDate.now(DateTimes.USER_ZONE));
        WeeklyMetrics.Progress progress = buildProgress(user, periodEnd);
        mailSender.send(user, progress, latestMeasurements(user, progress));
        LOG.info("Sent weekly summary for {}", periodEnd);
    }

    WeeklySummaryMeasurements latestMeasurements(User user, WeeklyMetrics.Progress progress) {
        return new WeeklySummaryMeasurements(
            latestMeasurements(user),
            latestMeasurementsAtPeriodEnd(user, progress.previousComparablePeriod().endDate()),
            latestMeasurementsAtPeriodEnd(user, progress.yearAgoComparablePeriod().endDate())
        );
    }

    private WeeklySummaryMeasurements.PeriodMeasurements latestMeasurements(User user) {
        return new WeeklySummaryMeasurements.PeriodMeasurements(
            weightRepository.findFirstByUserOrderByMeasuredAtDesc(user).orElse(null),
            bloodPressureRepository.findFirstByUserOrderByMeasuredAtDesc(user).orElse(null)
        );
    }

    private WeeklySummaryMeasurements.PeriodMeasurements latestMeasurementsAtPeriodEnd(User user, LocalDate periodEnd) {
        OffsetDateTime endExclusive = DateTimes.startOfDay(periodEnd.plusDays(1));
        return new WeeklySummaryMeasurements.PeriodMeasurements(
            weightRepository.findFirstByUserAndMeasuredAtLessThanOrderByMeasuredAtDesc(user, endExclusive).orElse(null),
            bloodPressureRepository.findFirstByUserAndMeasuredAtLessThanOrderByMeasuredAtDesc(user, endExclusive).orElse(null)
        );
    }

    WeeklyMetrics.Progress buildProgress(User user, LocalDate periodEnd) {
        LocalDate currentStart = DateTimes.startOfDashboardWeek(periodEnd);
        LocalDate dataStart = currentStart.minusWeeks(YEAR_COMPARISON_WEEKS);
        OffsetDateTime dataStartTime = DateTimes.startOfDay(dataStart);
        OffsetDateTime dataEndExclusive = DateTimes.startOfDay(periodEnd.plusDays(1));
        List<DailyStatus> currentStatuses = currentStart.datesUntil(periodEnd.plusDays(1))
            .map(date -> snapshotService.rebuild(user, date))
            .toList();
        List<DailyStatus> statuses = List.of(
                snapshotService.getFullWeek(user, periodEnd.minusWeeks(YEAR_COMPARISON_WEEKS)),
                snapshotService.getFullWeek(user, periodEnd.minusWeeks(1)),
                currentStatuses
            ).stream()
            .flatMap(List::stream)
            .sorted(Comparator.comparing(DailyStatus::getStatusDate))
            .toList();
        List<Routine> routines = routineRepository.findByUserOrderByStartDateAsc(user).stream()
            .filter(routine -> !DateTimes.toLocalDate(routine.getStartDate()).isAfter(periodEnd))
            .toList();
        List<RoutineCheckin> routineCheckins = routines.stream()
            .flatMap(routine -> routineCheckinRepository.findByRoutineAndCheckedAtBetweenOrderByCheckedAtAsc(routine, dataStartTime, dataEndExclusive).stream())
            .toList();
        WeeklyMetricsCalculator.Input input = new WeeklyMetricsCalculator.Input(
            statuses,
            List.of(),
            List.of(),
            moodRepository.findByUserAndMoodDateBetweenOrderByMoodDateAsc(user, dataStart, periodEnd),
            sleepRepository.findByUserAndSleepDateBetweenOrderBySleepDateAsc(user, dataStart, periodEnd),
            calorieService.findBetween(user, dataStart, periodEnd),
            workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, dataStart, periodEnd),
            List.of(),
            decisionOutcomeRepository.findByUserAndOutcomeDateBetweenOrderByOutcomeDateAscIdAsc(user, dataStart, periodEnd),
            routineCheckins
        );
        return metricsCalculator.progress(user, periodEnd, input);
    }

    LocalDate latestCompletedWeekEnd(LocalDate date) {
        return DateTimes.startOfDashboardWeek(date).minusDays(1);
    }

    private void requireEnabled() {
        if (!properties.weeklySummary().enabled()) {
            throw new BadRequestException("Weekly summary email is disabled");
        }
    }
}
