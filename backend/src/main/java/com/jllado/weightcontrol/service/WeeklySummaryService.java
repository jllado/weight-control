package com.jllado.weightcontrol.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.HistoryEventResponse;
import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.WeeklyReflectionResponse;
import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryArchiveResponse;
import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryDetailResponse;
import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryListItem;
import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryPreviewResponse;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.BloodPressure;
import com.jllado.weightcontrol.domain.CoachingPlan;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineCheckin;
import com.jllado.weightcontrol.domain.SavedWeeklySummary;
import com.jllado.weightcontrol.domain.Sickness;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.WeeklyReflection;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import com.jllado.weightcontrol.repository.CoachingPlanRepository;
import com.jllado.weightcontrol.repository.DecisionOutcomeRepository;
import com.jllado.weightcontrol.repository.MoodRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.repository.SavedWeeklySummaryRepository;
import com.jllado.weightcontrol.repository.SicknessRepository;
import com.jllado.weightcontrol.repository.SleepRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.repository.WeeklyReflectionRepository;
import com.jllado.weightcontrol.repository.WeightRepository;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import com.jllado.weightcontrol.service.WeeklySummarySnapshot.BloodPressureMeasurement;
import com.jllado.weightcontrol.service.WeeklySummarySnapshot.GoalEvidence;
import com.jllado.weightcontrol.service.WeeklySummarySnapshot.OutcomeMeasurements;
import com.jllado.weightcontrol.service.WeeklySummarySnapshot.PersonalRecordSnapshot;
import com.jllado.weightcontrol.service.WeeklySummarySnapshot.RoutineProgress;
import com.jllado.weightcontrol.service.WeeklySummarySnapshot.WeightMeasurement;
import com.jllado.weightcontrol.util.DateTimes;
import com.jllado.weightcontrol.util.Numbers;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WeeklySummaryService {

    public static final DayOfWeek DELIVERY_DAY = DayOfWeek.MONDAY;
    public static final LocalTime DELIVERY_TIME = LocalTime.of(8, 0);
    private static final int YEAR_COMPARISON_WEEKS = 52;
    private static final int SNAPSHOT_SCHEMA_VERSION = 1;
    private static final Logger LOG = LoggerFactory.getLogger(WeeklySummaryService.class);

    private final WeightRepository weightRepository;
    private final BloodPressureRepository bloodPressureRepository;
    private final MoodRepository moodRepository;
    private final SleepRepository sleepRepository;
    private final SicknessRepository sicknessRepository;
    private final CalorieService calorieService;
    private final WorkoutRepository workoutRepository;
    private final DecisionOutcomeRepository decisionOutcomeRepository;
    private final RoutineRepository routineRepository;
    private final RoutineCheckinRepository routineCheckinRepository;
    private final DailyStatusSnapshotService snapshotService;
    private final PersonalRecordService personalRecordService;
    private final WeeklyMetricsCalculator metricsCalculator;
    private final WeeklySummaryMailSender mailSender;
    private final UserRepository userRepository;
    private final SavedWeeklySummaryRepository savedSummaryRepository;
    private final WeeklyReflectionRepository weeklyReflectionRepository;
    private final CoachingPlanRepository coachingPlanRepository;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public WeeklySummaryService(
        WeightRepository weightRepository,
        BloodPressureRepository bloodPressureRepository,
        MoodRepository moodRepository,
        SleepRepository sleepRepository,
        SicknessRepository sicknessRepository,
        CalorieService calorieService,
        WorkoutRepository workoutRepository,
        DecisionOutcomeRepository decisionOutcomeRepository,
        RoutineRepository routineRepository,
        RoutineCheckinRepository routineCheckinRepository,
        DailyStatusSnapshotService snapshotService,
        PersonalRecordService personalRecordService,
        WeeklyMetricsCalculator metricsCalculator,
        WeeklySummaryMailSender mailSender,
        UserRepository userRepository,
        SavedWeeklySummaryRepository savedSummaryRepository,
        WeeklyReflectionRepository weeklyReflectionRepository,
        CoachingPlanRepository coachingPlanRepository,
        ObjectMapper objectMapper,
        AppProperties properties
    ) {
        this.weightRepository = weightRepository;
        this.bloodPressureRepository = bloodPressureRepository;
        this.moodRepository = moodRepository;
        this.sleepRepository = sleepRepository;
        this.sicknessRepository = sicknessRepository;
        this.calorieService = calorieService;
        this.workoutRepository = workoutRepository;
        this.decisionOutcomeRepository = decisionOutcomeRepository;
        this.routineRepository = routineRepository;
        this.routineCheckinRepository = routineCheckinRepository;
        this.snapshotService = snapshotService;
        this.personalRecordService = personalRecordService;
        this.metricsCalculator = metricsCalculator;
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.savedSummaryRepository = savedSummaryRepository;
        this.weeklyReflectionRepository = weeklyReflectionRepository;
        this.coachingPlanRepository = coachingPlanRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public void send(User user) {
        requireOwner(user);
        requireEnabled();
        LocalDate fridayDate = latestClosedOutcomeWeekEnd(LocalDate.now(DateTimes.USER_ZONE));
        SavedWeeklySummary saved = createForFriday(user, fridayDate);
        WeeklySummarySnapshot snapshot = readSnapshot(saved);
        WeeklyReflection reflection = weeklyReflectionRepository.findByWeeklySummary(saved).orElse(null);
        mailSender.send(user, snapshot, reflection);
        LOG.info("Sent weekly summary for {}", fridayDate);
    }

    @Transactional
    public WeeklySummaryArchiveResponse archive(User user) {
        List<SavedWeeklySummary> summaries = savedSummaryRepository.findByUserOrderByFridayDateDesc(user);
        Set<Long> summaryIdsWithReflections = summaries.isEmpty() ? Set.of() : weeklyReflectionRepository.findByWeeklySummaryIn(summaries).stream()
            .map(reflection -> reflection.getWeeklySummary().getId())
            .collect(java.util.stream.Collectors.toSet());
        return new WeeklySummaryArchiveResponse(
            latestClosedOutcomeWeekEnd(LocalDate.now(DateTimes.USER_ZONE)),
            !properties.chatGptActions().token().isBlank(),
            summaries.stream().map(summary -> new WeeklySummaryListItem(
                DateTimes.startOfDashboardWeek(summary.getFridayDate()),
                summary.getFridayDate(),
                summary.getCreatedAt(),
                summaryIdsWithReflections.contains(summary.getId())
            )).toList()
        );
    }

    @Transactional
    public WeeklySummaryPreviewResponse preview(User user, LocalDate today) {
        LocalDate fridayDate = latestClosedOutcomeWeekEnd(today);
        Optional<SavedWeeklySummary> saved = savedSummaryRepository.findByUserAndFridayDate(user, fridayDate);
        WeeklySummarySnapshot snapshot = saved.map(this::readSnapshot).orElseGet(() -> buildSnapshot(user, fridayDate));
        boolean canCreate = saved.isEmpty();
        return new WeeklySummaryPreviewResponse(
            snapshot.periodStart(),
            fridayDate,
            canCreate,
            saved.isPresent(),
            snapshot
        );
    }

    @Transactional
    public WeeklySummaryDetailResponse createLatest(User user, LocalDate today) {
        LocalDate fridayDate = latestClosedOutcomeWeekEnd(today);
        return detail(createForFriday(user, fridayDate, today));
    }

    @Transactional
    public Optional<WeeklySummaryDetailResponse> detail(User user, LocalDate fridayDate) {
        return savedSummaryRepository.findByUserAndFridayDate(user, fridayDate).map(this::detail);
    }

    @Transactional
    public SavedWeeklySummary createForFriday(User user, LocalDate fridayDate) {
        return createForFriday(user, fridayDate, LocalDate.now(DateTimes.USER_ZONE));
    }

    @Transactional
    public SavedWeeklySummary createForFriday(User user, LocalDate fridayDate, LocalDate today) {
        return createForFridayIfMissing(user, fridayDate, today).summary();
    }

    @Transactional
    public CreationResult createForFridayIfMissing(User user, LocalDate fridayDate, LocalDate today) {
        if (fridayDate.getDayOfWeek() != DayOfWeek.FRIDAY) {
            throw new BadRequestException("Weekly summaries must end on a Friday");
        }
        if (!today.isAfter(fridayDate.plusDays(2))) {
            throw new BadRequestException("Weekly summaries can only be saved after the following Sunday has ended");
        }
        User lockedUser = userRepository.findByIdForUpdate(user.getId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Optional<SavedWeeklySummary> existing = savedSummaryRepository.findByUserAndFridayDateForUpdate(lockedUser, fridayDate);
        if (existing.isPresent()) {
            return new CreationResult(existing.get(), false);
        }
        SavedWeeklySummary saved = new SavedWeeklySummary();
        saved.setUser(lockedUser);
        saved.setFridayDate(fridayDate);
        saved.setSnapshotJson(writeSnapshot(buildSnapshot(lockedUser, fridayDate)));
        saved.setCreatedAt(Instant.now());
        return new CreationResult(savedSummaryRepository.save(saved), true);
    }

    public record CreationResult(SavedWeeklySummary summary, boolean created) {
    }

    public List<String> outcomeWarnings(User user, LocalDate fridayDate) {
        OutcomeMeasurements outcomes = outcomeMeasurements(user, fridayDate);
        List<String> warnings = new java.util.ArrayList<>();
        if (outcomes.weight() == null) warnings.add("No weight measurement was recorded Friday, Saturday, or Sunday.");
        if (outcomes.bloodPressure() == null) warnings.add("No blood pressure measurement was recorded Friday, Saturday, or Sunday.");
        return List.copyOf(warnings);
    }

    @Transactional
    public WeeklySummarySnapshot readSnapshot(SavedWeeklySummary saved) {
        try {
            return objectMapper.readValue(saved.getSnapshotJson(), WeeklySummarySnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Saved weekly summary snapshot is invalid", exception);
        }
    }

    public Optional<LocalDate> earliestUnderlyingDate(User user) {
        return Stream.of(
                weightRepository.findFirstByUserOrderByMeasuredAtAsc(user).map(weight -> DateTimes.toLocalDate(weight.getMeasuredAt())),
                bloodPressureRepository.findFirstByUserOrderByMeasuredAtAsc(user).map(value -> DateTimes.toLocalDate(value.getMeasuredAt())),
                moodRepository.findFirstByUserOrderByMoodDateAsc(user).map(value -> value.getMoodDate()),
                sleepRepository.findFirstByUserOrderBySleepDateAsc(user).map(value -> value.getSleepDate()),
                calorieService.findFirstRecordedDate(user),
                workoutRepository.findFirstByUserOrderByWorkoutDateAsc(user).map(value -> value.getWorkoutDate()),
                sicknessRepository.findFirstByUserOrderBySicknessDateAsc(user).map(Sickness::getSicknessDate),
                decisionOutcomeRepository.findFirstByUserOrderByOutcomeDateAscIdAsc(user).map(value -> value.getOutcomeDate()),
                routineCheckinRepository.findFirstByRoutineUserOrderByCheckedAtAsc(user).map(value -> DateTimes.toLocalDate(value.getCheckedAt())),
                routineRepository.findFirstByUserOrderByStartDateAsc(user).map(value -> DateTimes.toLocalDate(value.getStartDate()))
            )
            .flatMap(Optional::stream)
            .min(Comparator.naturalOrder());
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
            .map(date -> snapshotService.getReadOnly(user, date))
            .toList();
        List<DailyStatus> statuses = List.of(
                readOnlyWeek(user, periodEnd.minusWeeks(YEAR_COMPARISON_WEEKS)),
                readOnlyWeek(user, periodEnd.minusWeeks(1)),
                currentStatuses
            ).stream()
            .flatMap(List::stream)
            .sorted(Comparator.comparing(DailyStatus::getStatusDate))
            .toList();
        List<Routine> routines = routineRepository.findByUserOrderByStartDateAsc(user).stream()
            .filter(routine -> !DateTimes.toLocalDate(routine.getStartDate()).isAfter(periodEnd))
            .toList();
        List<RoutineCheckin> routineCheckins = routines.stream()
            .flatMap(routine -> routineCheckinRepository.findByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThanOrderByCheckedAtAsc(routine, dataStartTime, dataEndExclusive).stream())
            .toList();
        WeeklyMetricsCalculator.Input input = new WeeklyMetricsCalculator.Input(
            statuses,
            weightRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(user, dataStartTime, dataEndExclusive),
            bloodPressureRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(user, dataStartTime, dataEndExclusive),
            moodRepository.findByUserAndMoodDateBetweenOrderByMoodDateAsc(user, dataStart, periodEnd),
            sleepRepository.findByUserAndSleepDateBetweenOrderBySleepDateAsc(user, dataStart, periodEnd),
            calorieService.findBetween(user, dataStart, periodEnd),
            workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, dataStart, periodEnd),
            sicknessRepository.findByUserAndSicknessDateBetweenOrderBySicknessDateAsc(user, dataStart, periodEnd),
            decisionOutcomeRepository.findByUserAndOutcomeDateBetweenOrderByOutcomeDateAscIdAsc(user, dataStart, periodEnd),
            routineCheckins
        );
        return metricsCalculator.progress(user, periodEnd, input);
    }

    private List<DailyStatus> readOnlyWeek(User user, LocalDate periodEnd) {
        return DateTimes.startOfDashboardWeek(periodEnd).datesUntil(periodEnd.plusDays(1))
            .map(date -> snapshotService.getReadOnly(user, date)).toList();
    }

    private WeeklySummarySnapshot buildSnapshot(User user, LocalDate fridayDate) {
        LocalDate periodStart = DateTimes.startOfDashboardWeek(fridayDate);
        WeeklyMetrics.Progress progress = buildProgress(user, fridayDate);
        OutcomeMeasurements outcomes = outcomeMeasurements(user, fridayDate);
        return new WeeklySummarySnapshot(
            SNAPSHOT_SCHEMA_VERSION,
            periodStart,
            fridayDate,
            progress,
            outcomes,
            routineProgress(user, periodStart, fridayDate),
            goalEvidence(user, fridayDate),
            personalRecordService.improvedHistoryBetween(user, periodStart, fridayDate).stream()
                .map(WeeklySummaryService::personalRecordSnapshot)
                .toList(),
            outcomeWarnings(outcomes)
        );
    }

    private List<String> outcomeWarnings(OutcomeMeasurements outcomes) {
        List<String> warnings = new java.util.ArrayList<>();
        if (outcomes.weight() == null) warnings.add("No weight measurement was recorded Friday, Saturday, or Sunday.");
        if (outcomes.bloodPressure() == null) warnings.add("No blood pressure measurement was recorded Friday, Saturday, or Sunday.");
        return List.copyOf(warnings);
    }

    OutcomeMeasurements outcomeMeasurements(User user, LocalDate fridayDate) {
        OffsetDateTime start = DateTimes.startOfDay(fridayDate);
        OffsetDateTime end = DateTimes.startOfDay(fridayDate.plusDays(3));
        List<Weight> weights = weightRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(user, start, end);
        List<BloodPressure> bloodPressures = bloodPressureRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(user, start, end);
        return new OutcomeMeasurements(selectWeight(weights, fridayDate), selectBloodPressure(bloodPressures, fridayDate));
    }

    private WeightMeasurement selectWeight(List<Weight> weights, LocalDate fridayDate) {
        return Stream.of(fridayDate, fridayDate.plusDays(1), fridayDate.plusDays(2))
            .map(date -> weights.stream().filter(weight -> DateTimes.toLocalDate(weight.getMeasuredAt()).equals(date)).max(Comparator.comparing(Weight::getMeasuredAt)))
            .flatMap(Optional::stream)
            .findFirst()
            .map(weight -> new WeightMeasurement(
                DateTimes.toLocalDate(weight.getMeasuredAt()),
                weight.getWeight(),
                weight.getFatPercentage(),
                weight.getFat(),
                weight.getMusclePercentage(),
                weight.getMuscle()
            ))
            .orElse(null);
    }

    private BloodPressureMeasurement selectBloodPressure(List<BloodPressure> readings, LocalDate fridayDate) {
        return Stream.of(fridayDate, fridayDate.plusDays(1), fridayDate.plusDays(2))
            .map(date -> readings.stream().filter(value -> DateTimes.toLocalDate(value.getMeasuredAt()).equals(date)).max(Comparator.comparing(BloodPressure::getMeasuredAt)))
            .flatMap(Optional::stream)
            .findFirst()
            .map(value -> new BloodPressureMeasurement(DateTimes.toLocalDate(value.getMeasuredAt()), value.getUpper(), value.getLower()))
            .orElse(null);
    }

    List<RoutineProgress> routineProgress(User user, LocalDate periodStart, LocalDate periodEnd) {
        return routineRepository.findByUserOrderByStartDateAsc(user).stream()
            .map(routine -> {
                LocalDate routineStart = DateTimes.toLocalDate(routine.getStartDate());
                LocalDate eligibleStart = routineStart.isAfter(periodStart) ? routineStart : periodStart;
                int eligibleDays = eligibleStart.isAfter(periodEnd) ? 0 : (int) (periodEnd.toEpochDay() - eligibleStart.toEpochDay() + 1);
                if (eligibleDays == 0) {
                    return null;
                }
                Set<LocalDate> completedDates = routineCheckinRepository.findByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThanOrderByCheckedAtAsc(
                        routine,
                        DateTimes.startOfDay(eligibleStart),
                        DateTimes.startOfDay(periodEnd.plusDays(1))
                    ).stream()
                    .map(checkin -> DateTimes.toLocalDate(checkin.getCheckedAt()))
                    .collect(java.util.stream.Collectors.toSet());
                return new RoutineProgress(
                    routine.getName(),
                    completedDates.size(),
                    eligibleDays,
                    Numbers.percentage(completedDates.size(), eligibleDays)
                );
            })
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    private GoalEvidence goalEvidence(User user, LocalDate fridayDate) {
        Optional<CoachingPlan> plan = coachingPlanRepository.findByUser(user);
        if (plan.isEmpty()) {
            return new GoalEvidence(false, null, null, null, "No coaching plan is saved.");
        }
        CoachingPlan current = plan.get();
        LocalDate updatedDate = current.getUpdatedAt().atZone(DateTimes.USER_ZONE).toLocalDate();
        if (current.getStartDate().isAfter(fridayDate) || updatedDate.isAfter(fridayDate)) {
            return new GoalEvidence(false, null, null, null, "The coaching plan version applicable to this week is not available.");
        }
        return new GoalEvidence(true, current.getGoal(), current.getStartDate(), current.getReviewDate(), null);
    }

    private static PersonalRecordSnapshot personalRecordSnapshot(HistoryEventResponse record) {
        return new PersonalRecordSnapshot(record.metricLabel(), record.value(), record.unit(), record.recordDate());
    }

    private WeeklySummaryDetailResponse detail(SavedWeeklySummary saved) {
        WeeklyReflection reflection = weeklyReflectionRepository.findByWeeklySummary(saved).orElse(null);
        return new WeeklySummaryDetailResponse(
            DateTimes.startOfDashboardWeek(saved.getFridayDate()),
            saved.getFridayDate(),
            saved.getCreatedAt(),
            readSnapshot(saved),
            reflection == null ? null : WeeklyReflectionResponse.from(reflection)
        );
    }

    private String writeSnapshot(WeeklySummarySnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize weekly summary snapshot", exception);
        }
    }

    public LocalDate latestCompletedWeekEnd(LocalDate date) {
        return DateTimes.startOfDashboardWeek(date).minusDays(1);
    }

    public LocalDate latestClosedOutcomeWeekEnd(LocalDate date) {
        LocalDate friday = latestCompletedWeekEnd(date);
        return date.isBefore(friday.plusDays(3)) ? friday.minusWeeks(1) : friday;
    }

    public void requireOwner(User user) {
        if (!user.getEmail().equalsIgnoreCase(properties.weeklySummary().ownerEmail())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        }
    }

    private void requireEnabled() {
        if (!properties.weeklySummary().enabled()) {
            throw new BadRequestException("Weekly summary email is disabled");
        }
    }
}
