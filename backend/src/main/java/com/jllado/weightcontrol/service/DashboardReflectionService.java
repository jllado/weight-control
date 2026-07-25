package com.jllado.weightcontrol.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.ReflectionOverviewResponse;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.ReflectionSummaryResponse;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.SaveReflectionRequest;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.BloodPressure;
import com.jllado.weightcontrol.domain.Calorie;
import com.jllado.weightcontrol.domain.DashboardReflection;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.DecisionOutcome;
import com.jllado.weightcontrol.domain.DecisionOutcomeType;
import com.jllado.weightcontrol.domain.Habit;
import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineCheckin;
import com.jllado.weightcontrol.domain.Sickness;
import com.jllado.weightcontrol.domain.Sleep;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.WorkoutLine;
import com.jllado.weightcontrol.domain.WorkoutSegment;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import com.jllado.weightcontrol.repository.CalorieRepository;
import com.jllado.weightcontrol.repository.DailyStatusRepository;
import com.jllado.weightcontrol.repository.DashboardReflectionRepository;
import com.jllado.weightcontrol.repository.DecisionOutcomeRepository;
import com.jllado.weightcontrol.repository.HabitRepository;
import com.jllado.weightcontrol.repository.MoodRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.repository.SicknessRepository;
import com.jllado.weightcontrol.repository.SleepRepository;
import com.jllado.weightcontrol.repository.WeightRepository;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import com.jllado.weightcontrol.util.DateTimes;
import com.jllado.weightcontrol.util.Numbers;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class DashboardReflectionService {

    private static final int CONTEXT_DAYS = 90;
    private static final int DETAILED_DAYS = 30;

    private final DashboardReflectionRepository reflectionRepository;
    private final DailyStatusRepository dailyStatusRepository;
    private final WeightRepository weightRepository;
    private final BloodPressureRepository bloodPressureRepository;
    private final MoodRepository moodRepository;
    private final SleepRepository sleepRepository;
    private final CalorieRepository calorieRepository;
    private final WorkoutRepository workoutRepository;
    private final SicknessRepository sicknessRepository;
    private final DecisionOutcomeRepository decisionOutcomeRepository;
    private final HabitRepository habitRepository;
    private final RoutineRepository routineRepository;
    private final RoutineCheckinRepository routineCheckinRepository;
    private final DailyStatusSnapshotService snapshotService;
    private final DecisionOutcomeService decisionOutcomeService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public DashboardReflectionService(
        DashboardReflectionRepository reflectionRepository,
        DailyStatusRepository dailyStatusRepository,
        WeightRepository weightRepository,
        BloodPressureRepository bloodPressureRepository,
        MoodRepository moodRepository,
        SleepRepository sleepRepository,
        CalorieRepository calorieRepository,
        WorkoutRepository workoutRepository,
        SicknessRepository sicknessRepository,
        DecisionOutcomeRepository decisionOutcomeRepository,
        HabitRepository habitRepository,
        RoutineRepository routineRepository,
        RoutineCheckinRepository routineCheckinRepository,
        DailyStatusSnapshotService snapshotService,
        DecisionOutcomeService decisionOutcomeService,
        AppProperties properties,
        ObjectMapper objectMapper
    ) {
        this.reflectionRepository = reflectionRepository;
        this.dailyStatusRepository = dailyStatusRepository;
        this.weightRepository = weightRepository;
        this.bloodPressureRepository = bloodPressureRepository;
        this.moodRepository = moodRepository;
        this.sleepRepository = sleepRepository;
        this.calorieRepository = calorieRepository;
        this.workoutRepository = workoutRepository;
        this.sicknessRepository = sicknessRepository;
        this.decisionOutcomeRepository = decisionOutcomeRepository;
        this.habitRepository = habitRepository;
        this.routineRepository = routineRepository;
        this.routineCheckinRepository = routineCheckinRepository;
        this.snapshotService = snapshotService;
        this.decisionOutcomeService = decisionOutcomeService;
        this.properties = properties;
        this.objectMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public ReflectionOverviewResponse getOverview(User user) {
        LocalDate firstTrackedDate = dailyStatusRepository.findFirstByUserOrderByStatusDateAsc(user)
            .map(DailyStatus::getStatusDate)
            .orElse(null);
        List<ReflectionSummaryResponse> summaries = reflectionRepository.findByUserOrderByReflectionDateDesc(user).stream()
            .map(ReflectionSummaryResponse::from)
            .toList();
        return new ReflectionOverviewResponse(
            firstTrackedDate,
            user.getLastCompletedDashboardDate(),
            !properties.chatGptActions().token().isBlank(),
            summaries
        );
    }

    public Optional<DashboardReflection> find(User user, LocalDate reflectionDate) {
        return reflectionRepository.findByUserAndReflectionDate(user, reflectionDate);
    }

    public JsonNode getContext(User user, LocalDate reflectionDate) {
        validateEligibleDate(user, reflectionDate);
        snapshotService.getOrBuild(user, reflectionDate);
        return objectMapper.valueToTree(buildInput(user, reflectionDate));
    }

    public DashboardReflection save(User user, LocalDate reflectionDate, SaveReflectionRequest request) {
        validateEligibleDate(user, reflectionDate);
        DashboardReflection reflection = reflectionRepository.findByUserAndReflectionDate(user, reflectionDate)
            .orElseGet(() -> {
                DashboardReflection created = new DashboardReflection();
                created.setUser(user);
                return created;
            });
        reflection.setReflectionDate(reflectionDate);
        reflection.setWindowStart(reflectionDate.minusDays(CONTEXT_DAYS - 1L));
        reflection.setWindowEnd(reflectionDate);
        reflection.setGeneratedAt(Instant.now());
        reflection.setModel("ChatGPT");
        reflection.setTitle(request.title());
        reflection.setSummary(request.summary());
        reflection.setPositiveSignals(request.positiveSignals());
        reflection.setWatchouts(request.watchouts());
        reflection.setNextActions(request.nextActions());
        return reflectionRepository.save(reflection);
    }

    private void validateEligibleDate(User user, LocalDate reflectionDate) {
        LocalDate firstTrackedDate = dailyStatusRepository.findFirstByUserOrderByStatusDateAsc(user)
            .map(DailyStatus::getStatusDate)
            .orElseThrow(() -> new BadRequestException("No tracked dashboard dates are available"));
        LocalDate lastCompletedDate = user.getLastCompletedDashboardDate();
        if (lastCompletedDate == null || reflectionDate.isBefore(firstTrackedDate) || reflectionDate.isAfter(lastCompletedDate)) {
            throw new BadRequestException("Reflections can only be generated for completed tracked dates");
        }
    }

    private ReflectionInput buildInput(User user, LocalDate selectedDate) {
        LocalDate contextStart = selectedDate.minusDays(CONTEXT_DAYS - 1L);
        LocalDate detailedStart = selectedDate.minusDays(DETAILED_DAYS - 1L);
        LocalDate baselineEnd = detailedStart.minusDays(1);
        OffsetDateTime contextStartTime = DateTimes.startOfDay(contextStart);
        OffsetDateTime selectedEndExclusive = DateTimes.startOfDay(selectedDate).plusDays(1);

        List<DailyStatus> statuses = dailyStatusRepository.findByUserAndStatusDateBetweenOrderByStatusDateAsc(user, contextStart, selectedDate);
        List<Weight> weights = weightRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(user, contextStartTime, selectedEndExclusive);
        List<BloodPressure> bloodPressures = bloodPressureRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(user, contextStartTime, selectedEndExclusive);
        List<Mood> moods = moodRepository.findByUserAndMoodDateBetweenOrderByMoodDateAsc(user, contextStart, selectedDate);
        List<Sleep> sleeps = sleepRepository.findByUserAndSleepDateBetweenOrderBySleepDateAsc(user, contextStart, selectedDate);
        List<Calorie> calories = calorieRepository.findByUserAndCalorieDateBetweenOrderByCalorieDateAsc(user, contextStart, selectedDate);
        List<Workout> workouts = workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, contextStart, selectedDate);
        List<Sickness> sicknesses = sicknessRepository.findByUserAndSicknessDateBetweenOrderBySicknessDateAsc(user, contextStart, selectedDate);
        List<DecisionOutcome> decisions = decisionOutcomeRepository.findByUserAndOutcomeDateBetweenOrderByOutcomeDateAscIdAsc(user, contextStart, selectedDate);
        List<Routine> routines = routineRepository.findByUserOrderByStartDateAsc(user).stream()
            .filter(routine -> !DateTimes.toLocalDate(routine.getStartDate()).isAfter(selectedDate))
            .toList();
        Map<Routine, List<RoutineCheckin>> checkins = routines.stream().collect(Collectors.toMap(
            Function.identity(),
            routine -> routineCheckinRepository.findByRoutineAndCheckedAtBetweenOrderByCheckedAtAsc(routine, contextStartTime, selectedEndExclusive)
        ));

        return new ReflectionInput(
            selectedDate,
            contextStart,
            detailedStart,
            baselineEnd,
            toProfileData(user, selectedDate),
            detailed(statuses, DailyStatus::getStatusDate, detailedStart).stream().map(this::toDailyStatusData).toList(),
            habitRepository.findByUserOrderByStartDateAsc(user).stream()
                .filter(habit -> !DateTimes.toLocalDate(habit.getStartDate()).isAfter(selectedDate))
                .map(habit -> toHabitData(habit, selectedDate))
                .toList(),
            routines.stream().map(routine -> toRoutineData(routine, checkins.get(routine), detailedStart)).toList(),
            detailed(weights, weight -> DateTimes.toLocalDate(weight.getMeasuredAt()), detailedStart).stream().map(this::toWeightData).toList(),
            detailed(bloodPressures, bloodPressure -> DateTimes.toLocalDate(bloodPressure.getMeasuredAt()), detailedStart).stream().map(this::toBloodPressureData).toList(),
            detailed(moods, Mood::getMoodDate, detailedStart).stream().map(this::toMoodData).toList(),
            detailed(sleeps, Sleep::getSleepDate, detailedStart).stream().map(this::toSleepData).toList(),
            detailed(calories, Calorie::getCalorieDate, detailedStart).stream().map(this::toCalorieData).toList(),
            toWorkoutContextData(detailed(workouts, Workout::getWorkoutDate, detailedStart)),
            detailed(sicknesses, Sickness::getSicknessDate, detailedStart).stream().map(this::toSicknessData).toList(),
            detailed(decisions, DecisionOutcome::getOutcomeDate, detailedStart).stream().map(this::toDecisionData).toList(),
            toDecisionSummary(decisionOutcomeService.summarize(user, selectedDate)),
            buildBaselineWeeks(
                user,
                contextStart,
                baselineEnd,
                statuses,
                weights,
                bloodPressures,
                moods,
                sleeps,
                calories,
                workouts,
                sicknesses,
                decisions,
                checkins.values().stream().flatMap(List::stream).toList()
            )
        );
    }

    private <T> List<T> detailed(List<T> values, Function<T, LocalDate> date, LocalDate detailedStart) {
        return values.stream().filter(value -> !date.apply(value).isBefore(detailedStart)).toList();
    }

    private List<WeeklySummary> buildBaselineWeeks(
        User user,
        LocalDate contextStart,
        LocalDate baselineEnd,
        List<DailyStatus> statuses,
        List<Weight> weights,
        List<BloodPressure> bloodPressures,
        List<Mood> moods,
        List<Sleep> sleeps,
        List<Calorie> calories,
        List<Workout> workouts,
        List<Sickness> sicknesses,
        List<DecisionOutcome> decisions,
        List<RoutineCheckin> checkins
    ) {
        List<WeeklySummary> summaries = new ArrayList<>();
        for (LocalDate weekStart = contextStart; !weekStart.isAfter(baselineEnd); weekStart = weekStart.plusDays(7)) {
            LocalDate weekEnd = weekStart.plusDays(6).isAfter(baselineEnd) ? baselineEnd : weekStart.plusDays(6);
            summaries.add(new WeeklySummary(
                weekStart,
                weekEnd,
                averageStatus(inRange(statuses, DailyStatus::getStatusDate, weekStart, weekEnd)),
                inRange(checkins, checkin -> DateTimes.toLocalDate(checkin.getCheckedAt()), weekStart, weekEnd).size(),
                averageWeight(inRange(weights, weight -> DateTimes.toLocalDate(weight.getMeasuredAt()), weekStart, weekEnd)),
                averageBloodPressure(inRange(bloodPressures, bloodPressure -> DateTimes.toLocalDate(bloodPressure.getMeasuredAt()), weekStart, weekEnd)),
                averageInteger(inRange(moods, Mood::getMoodDate, weekStart, weekEnd).stream().map(Mood::getValue).toList()),
                averageSleep(inRange(sleeps, Sleep::getSleepDate, weekStart, weekEnd)),
                summarizeCalories(user, weekStart, weekEnd, inRange(calories, Calorie::getCalorieDate, weekStart, weekEnd)),
                summarizeWorkouts(inRange(workouts, Workout::getWorkoutDate, weekStart, weekEnd)),
                counts(inRange(sicknesses, Sickness::getSicknessDate, weekStart, weekEnd), sickness -> sickness.getType().name()),
                counts(inRange(sicknesses, Sickness::getSicknessDate, weekStart, weekEnd), sickness -> sickness.getSeverity().name()),
                summarizeDecisions(inRange(decisions, DecisionOutcome::getOutcomeDate, weekStart, weekEnd))
            ));
        }
        return summaries;
    }

    private <T> List<T> inRange(List<T> values, Function<T, LocalDate> date, LocalDate start, LocalDate end) {
        return values.stream()
            .filter(value -> !date.apply(value).isBefore(start) && !date.apply(value).isAfter(end))
            .toList();
    }

    private <T> Map<String, Long> counts(List<T> values, Function<T, String> classifier) {
        return values.stream().collect(Collectors.groupingBy(classifier, Collectors.counting()));
    }

    private ProfileData toProfileData(User user, LocalDate selectedDate) {
        Integer age = user.getBirthDate() == null ? null : Period.between(user.getBirthDate(), selectedDate).getYears();
        return new ProfileData(
            age,
            user.getHeightCm(),
            user.getSex(),
            user.getFitnessLevel(),
            user.isTakesMedication(),
            user.getWeeklyAverageCalorieMaximum(),
            Map.of(
                "SATURDAY", user.getTypicalCaloriesSaturday(),
                "SUNDAY", user.getTypicalCaloriesSunday(),
                "MONDAY", user.getTypicalCaloriesMonday(),
                "TUESDAY", user.getTypicalCaloriesTuesday(),
                "WEDNESDAY", user.getTypicalCaloriesWednesday(),
                "THURSDAY", user.getTypicalCaloriesThursday(),
                "FRIDAY", user.getTypicalCaloriesFriday()
            )
        );
    }

    private HabitData toHabitData(Habit habit, LocalDate selectedDate) {
        LocalDate lastRecordedDate = habit.getLastTimeDate() == null ? null : DateTimes.toLocalDate(habit.getLastTimeDate());
        if (lastRecordedDate != null && lastRecordedDate.isAfter(selectedDate)) {
            lastRecordedDate = null;
        }
        return new HabitData(
            habit.getName(),
            DateTimes.toLocalDate(habit.getStartDate()),
            habit.getDuration(),
            lastRecordedDate
        );
    }

    private RoutineData toRoutineData(Routine routine, List<RoutineCheckin> checkins, LocalDate detailedStart) {
        List<LocalDate> detailedCheckinDates = checkins.stream()
            .map(checkin -> DateTimes.toLocalDate(checkin.getCheckedAt()))
            .filter(date -> !date.isBefore(detailedStart))
            .toList();
        return new RoutineData(
            routine.getName(),
            DateTimes.toLocalDate(routine.getStartDate()),
            routine.getTypes().stream().map(Enum::name).toList(),
            detailedCheckinDates.size(),
            detailedCheckinDates.isEmpty() ? null : detailedCheckinDates.getLast()
        );
    }

    private DailyStatusData toDailyStatusData(DailyStatus status) {
        return new DailyStatusData(
            status.getStatusDate(),
            status.getRoutinesPercentage(),
            status.getWeightPercentage(),
            status.getBloodPressurePercentage(),
            status.getFlexibilityPercentage(),
            status.getMindPercentage(),
            status.getRoutinesStatus(),
            status.getWeightStatus(),
            status.getBloodPressureStatus(),
            status.getFlexibilityStatus(),
            status.getMindStatus()
        );
    }

    private WeightData toWeightData(Weight weight) {
        return new WeightData(
            DateTimes.toLocalDate(weight.getMeasuredAt()),
            weight.getWeight(),
            weight.getFatPercentage(),
            weight.getMusclePercentage(),
            weight.getLostWeight(),
            weight.getLostFat(),
            weight.getLostMuscle()
        );
    }

    private BloodPressureData toBloodPressureData(BloodPressure bloodPressure) {
        return new BloodPressureData(
            DateTimes.toLocalDate(bloodPressure.getMeasuredAt()),
            bloodPressure.getUpper(),
            bloodPressure.getLower(),
            bloodPressure.getLostUpper(),
            bloodPressure.getLostLower()
        );
    }

    private MoodData toMoodData(Mood mood) {
        return new MoodData(mood.getMoodDate(), mood.getValue(), mood.getNote());
    }

    private SleepData toSleepData(Sleep sleep) {
        return new SleepData(
            sleep.getSleepDate(),
            sleep.getBedtimeStart(),
            sleep.getBedtimeEnd(),
            sleep.getTotalSleepDuration(),
            sleep.getDeepSleepDuration(),
            sleep.getRemSleepDuration(),
            sleep.getLightSleepDuration(),
            sleep.getAwakeTime(),
            sleep.getAverageHeartRate(),
            sleep.getAverageHrv()
        );
    }

    private CalorieData toCalorieData(Calorie calorie) {
        return new CalorieData(calorie.getCalorieDate(), calorie.getCalories());
    }

    private WorkoutContextData toWorkoutContextData(List<Workout> workouts) {
        Map<String, List<WorkoutLine>> linesByExercise = workouts.stream()
            .flatMap(workout -> workout.getLines().stream())
            .collect(Collectors.groupingBy(
                line -> line.getExercise().getName(),
                LinkedHashMap::new,
                Collectors.toList()
            ));
        return new WorkoutContextData(
            workouts.stream().map(this::toWorkoutData).toList(),
            linesByExercise.entrySet().stream()
                .map(entry -> toWorkoutExerciseData(entry.getKey(), entry.getValue()))
                .toList()
        );
    }

    private WorkoutData toWorkoutData(Workout workout) {
        List<WorkoutSegment> segments = workout.getLines().stream()
            .flatMap(line -> line.getSegments().stream())
            .toList();
        return new WorkoutData(
            workout.getWorkoutDate(),
            workout.getNote(),
            workout.getLines().stream().map(line -> line.getExercise().getName()).toList(),
            sumIntegerOrNull(segments.stream().map(WorkoutSegment::getDurationSeconds).toList()),
            sumDecimalOrNull(segments.stream().map(WorkoutSegment::getDistanceKm).toList()),
            sumIntegerOrNull(workout.getLines().stream().map(WorkoutLine::getCalories).toList()),
            strengthVolume(segments)
        );
    }

    private WorkoutExerciseData toWorkoutExerciseData(String exercise, List<WorkoutLine> lines) {
        List<WorkoutSegment> segments = lines.stream().flatMap(line -> line.getSegments().stream()).toList();
        return new WorkoutExerciseData(
            exercise,
            lines.getFirst().getExercise().getTrackingMode(),
            lines.size(),
            segments.size(),
            sumIntegerOrNull(segments.stream().map(WorkoutSegment::getRepetitions).toList()),
            sumIntegerOrNull(segments.stream().map(WorkoutSegment::getDurationSeconds).toList()),
            maximumDecimal(segments.stream().map(WorkoutSegment::getWeight).toList()),
            strengthVolume(segments),
            sumDecimalOrNull(segments.stream().map(WorkoutSegment::getDistanceKm).toList()),
            maximumDecimal(segments.stream().map(WorkoutSegment::getSpeedKph).toList()),
            maximumDecimal(segments.stream().map(WorkoutSegment::getInclinePercent).toList()),
            maximumInteger(segments.stream().map(WorkoutSegment::getResistanceLevel).toList()),
            sumIntegerOrNull(lines.stream().map(WorkoutLine::getCalories).toList()),
            averageInteger(lines.stream().map(WorkoutLine::getAverageHeartRate).toList())
        );
    }

    private BigDecimal strengthVolume(List<WorkoutSegment> segments) {
        List<BigDecimal> volumes = segments.stream()
            .filter(segment -> segment.getWeight() != null && segment.getRepetitions() != null)
            .map(segment -> segment.getWeight().multiply(BigDecimal.valueOf(segment.getRepetitions())))
            .toList();
        return volumes.isEmpty() ? null : sumDecimal(volumes);
    }

    private SicknessData toSicknessData(Sickness sickness) {
        return new SicknessData(
            sickness.getSicknessDate(),
            sickness.getType(),
            sickness.getSeverity(),
            sickness.getNote()
        );
    }

    private DecisionData toDecisionData(DecisionOutcome decision) {
        return new DecisionData(decision.getOutcomeDate(), decision.getOutcome());
    }

    private DecisionSummaryData toDecisionSummary(DecisionOutcomeService.Summary summary) {
        return new DecisionSummaryData(
            toDecisionMetrics(summary.selectedDate()),
            toDecisionMetrics(summary.rolling30Days()),
            toDecisionMetrics(summary.previous30Days()),
            toDecisionMetrics(summary.allTime()),
            summary.winRateChange(),
            summary.currentWinStreak()
        );
    }

    private DecisionMetricsData toDecisionMetrics(DecisionOutcomeService.Metrics metrics) {
        return new DecisionMetricsData(metrics.wins(), metrics.misses(), metrics.winRate());
    }

    private AverageStatus averageStatus(List<DailyStatus> statuses) {
        if (statuses.isEmpty()) {
            return null;
        }
        return new AverageStatus(
            averageDecimal(statuses.stream().map(DailyStatus::getRoutinesPercentage).toList()),
            averageDecimal(statuses.stream().map(DailyStatus::getWeightPercentage).toList()),
            averageDecimal(statuses.stream().map(DailyStatus::getBloodPressurePercentage).toList()),
            averageDecimal(statuses.stream().map(DailyStatus::getFlexibilityPercentage).toList()),
            averageDecimal(statuses.stream().map(DailyStatus::getMindPercentage).toList())
        );
    }

    private AverageWeight averageWeight(List<Weight> weights) {
        if (weights.isEmpty()) {
            return null;
        }
        return new AverageWeight(
            averageDecimal(weights.stream().map(Weight::getWeight).toList()),
            averageDecimal(weights.stream().map(Weight::getFatPercentage).toList()),
            averageDecimal(weights.stream().map(Weight::getMusclePercentage).toList())
        );
    }

    private AverageBloodPressure averageBloodPressure(List<BloodPressure> bloodPressures) {
        if (bloodPressures.isEmpty()) {
            return null;
        }
        return new AverageBloodPressure(
            averageInteger(bloodPressures.stream().map(BloodPressure::getUpper).toList()),
            averageInteger(bloodPressures.stream().map(BloodPressure::getLower).toList())
        );
    }

    private AverageSleep averageSleep(List<Sleep> sleeps) {
        if (sleeps.isEmpty()) {
            return null;
        }
        return new AverageSleep(
            averageInteger(sleeps.stream().map(Sleep::getTotalSleepDuration).toList()),
            averageInteger(sleeps.stream().map(Sleep::getDeepSleepDuration).toList()),
            averageInteger(sleeps.stream().map(Sleep::getRemSleepDuration).toList()),
            averageInteger(sleeps.stream().map(Sleep::getAwakeTime).toList()),
            averageDecimal(sleeps.stream().map(Sleep::getAverageHeartRate).toList()),
            averageInteger(sleeps.stream().map(Sleep::getAverageHrv).toList())
        );
    }

    private CalorieSummary summarizeCalories(User user, LocalDate start, LocalDate end, List<Calorie> calories) {
        int targetTotal = start.datesUntil(end.plusDays(1)).mapToInt(date -> targetCalories(user, date.getDayOfWeek())).sum();
        int total = calories.stream().mapToInt(Calorie::getCalories).sum();
        BigDecimal average = calories.isEmpty() ? null : BigDecimal.valueOf(total)
            .divide(BigDecimal.valueOf(calories.size()), 2, RoundingMode.HALF_UP);
        long days = start.datesUntil(end.plusDays(1)).count();
        BigDecimal targetAverage = BigDecimal.valueOf(targetTotal).divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        return new CalorieSummary(calories.size(), total, average, targetAverage, average == null ? null : average.subtract(targetAverage));
    }

    private int targetCalories(User user, DayOfWeek day) {
        return switch (day) {
            case SATURDAY -> user.getTypicalCaloriesSaturday();
            case SUNDAY -> user.getTypicalCaloriesSunday();
            case MONDAY -> user.getTypicalCaloriesMonday();
            case TUESDAY -> user.getTypicalCaloriesTuesday();
            case WEDNESDAY -> user.getTypicalCaloriesWednesday();
            case THURSDAY -> user.getTypicalCaloriesThursday();
            case FRIDAY -> user.getTypicalCaloriesFriday();
        };
    }

    private WorkoutSummary summarizeWorkouts(List<Workout> workouts) {
        int totalDurationSeconds = workouts.stream()
            .flatMap(workout -> workout.getLines().stream())
            .flatMap(line -> line.getSegments().stream())
            .map(WorkoutSegment::getDurationSeconds)
            .filter(java.util.Objects::nonNull)
            .mapToInt(Integer::intValue)
            .sum();
        BigDecimal totalDistanceKm = sumDecimal(workouts.stream()
            .flatMap(workout -> workout.getLines().stream())
            .flatMap(line -> line.getSegments().stream())
            .map(WorkoutSegment::getDistanceKm)
            .toList());
        int totalCalories = workouts.stream()
            .flatMap(workout -> workout.getLines().stream())
            .map(WorkoutLine::getCalories)
            .filter(java.util.Objects::nonNull)
            .mapToInt(Integer::intValue)
            .sum();
        BigDecimal strengthVolumeKg = workouts.stream()
            .flatMap(workout -> workout.getLines().stream())
            .flatMap(line -> line.getSegments().stream())
            .filter(segment -> segment.getWeight() != null && segment.getRepetitions() != null)
            .map(segment -> segment.getWeight().multiply(BigDecimal.valueOf(segment.getRepetitions())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new WorkoutSummary(workouts.size(), totalDurationSeconds, totalDistanceKm, totalCalories, strengthVolumeKg);
    }

    private DecisionMetricsData summarizeDecisions(List<DecisionOutcome> decisions) {
        long wins = decisions.stream().filter(decision -> decision.getOutcome() == DecisionOutcomeType.WIN).count();
        long misses = decisions.size() - wins;
        BigDecimal winRate = decisions.isEmpty() ? null : Numbers.percentage(wins, decisions.size());
        return new DecisionMetricsData(wins, misses, winRate);
    }

    private BigDecimal averageDecimal(List<BigDecimal> values) {
        List<BigDecimal> present = values.stream().filter(java.util.Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        return present.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(present.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal averageInteger(List<Integer> values) {
        List<Integer> present = values.stream().filter(java.util.Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        return BigDecimal.valueOf(present.stream().mapToInt(Integer::intValue).sum())
            .divide(BigDecimal.valueOf(present.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumDecimal(List<BigDecimal> values) {
        return values.stream().filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumDecimalOrNull(List<BigDecimal> values) {
        List<BigDecimal> present = values.stream().filter(java.util.Objects::nonNull).toList();
        return present.isEmpty() ? null : sumDecimal(present);
    }

    private Integer sumIntegerOrNull(List<Integer> values) {
        List<Integer> present = values.stream().filter(java.util.Objects::nonNull).toList();
        return present.isEmpty() ? null : present.stream().mapToInt(Integer::intValue).sum();
    }

    private BigDecimal maximumDecimal(List<BigDecimal> values) {
        return values.stream().filter(java.util.Objects::nonNull).max(BigDecimal::compareTo).orElse(null);
    }

    private Integer maximumInteger(List<Integer> values) {
        return values.stream().filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(null);
    }

    private record ReflectionInput(
        LocalDate selectedDate,
        LocalDate contextStart,
        LocalDate detailedStart,
        LocalDate baselineEnd,
        ProfileData profile,
        List<DailyStatusData> dailyStatuses,
        List<HabitData> habits,
        List<RoutineData> routines,
        List<WeightData> weights,
        List<BloodPressureData> bloodPressures,
        List<MoodData> moods,
        List<SleepData> sleeps,
        List<CalorieData> calories,
        WorkoutContextData workouts,
        List<SicknessData> sicknesses,
        List<DecisionData> decisions,
        DecisionSummaryData decisionSummary,
        List<WeeklySummary> baselineWeeks
    ) {
    }

    private record ProfileData(
        Integer age,
        Integer heightCm,
        Object sex,
        Object fitnessLevel,
        boolean takesMedication,
        int weeklyAverageCalorieMaximum,
        Map<String, Integer> typicalCaloriesByWeekday
    ) {
    }

    private record HabitData(String name, LocalDate startDate, Integer targetDays, LocalDate lastRecordedDate) {
    }

    private record RoutineData(String name, LocalDate startDate, List<String> types, int checkinCount, LocalDate lastCheckinDate) {
    }

    private record DailyStatusData(
        LocalDate date,
        BigDecimal routinesPercentage,
        BigDecimal weightPercentage,
        BigDecimal bloodPressurePercentage,
        BigDecimal flexibilityPercentage,
        BigDecimal mindPercentage,
        BigDecimal routinesStatus,
        BigDecimal weightStatus,
        BigDecimal bloodPressureStatus,
        BigDecimal flexibilityStatus,
        BigDecimal mindStatus
    ) {
    }

    private record WeightData(
        LocalDate date,
        BigDecimal weightKg,
        BigDecimal fatPercentage,
        BigDecimal musclePercentage,
        BigDecimal weightChangeKg,
        BigDecimal fatChangeKg,
        BigDecimal muscleChangeKg
    ) {
    }

    private record BloodPressureData(LocalDate date, Integer systolic, Integer diastolic, Integer systolicChange, Integer diastolicChange) {
    }

    private record MoodData(LocalDate date, Integer value, String note) {
    }

    private record SleepData(
        LocalDate date,
        OffsetDateTime bedtimeStart,
        OffsetDateTime bedtimeEnd,
        Integer totalSleepSeconds,
        Integer deepSleepSeconds,
        Integer remSleepSeconds,
        Integer lightSleepSeconds,
        Integer awakeSeconds,
        BigDecimal averageHeartRate,
        Integer averageHrv
    ) {
    }

    private record CalorieData(LocalDate date, Integer calories) {
    }

    private record WorkoutContextData(List<WorkoutData> days, List<WorkoutExerciseData> exerciseSummaries) {
    }

    private record WorkoutData(
        LocalDate date,
        String note,
        List<String> exercises,
        Integer totalDurationSeconds,
        BigDecimal totalDistanceKm,
        Integer totalCalories,
        BigDecimal strengthVolumeKg
    ) {
    }

    private record WorkoutExerciseData(
        String exercise,
        Object trackingMode,
        int sessionCount,
        int segmentCount,
        Integer totalRepetitions,
        Integer totalDurationSeconds,
        BigDecimal maximumWeightKg,
        BigDecimal strengthVolumeKg,
        BigDecimal totalDistanceKm,
        BigDecimal maximumSpeedKph,
        BigDecimal maximumInclinePercent,
        Integer maximumResistanceLevel,
        Integer totalCalories,
        BigDecimal averageHeartRate
    ) {
    }

    private record SicknessData(LocalDate date, Object type, Object severity, String note) {
    }

    private record DecisionData(LocalDate date, Object outcome) {
    }

    private record DecisionMetricsData(long wins, long misses, BigDecimal winRate) {
    }

    private record DecisionSummaryData(
        DecisionMetricsData selectedDate,
        DecisionMetricsData rolling30Days,
        DecisionMetricsData previous30Days,
        DecisionMetricsData allTime,
        BigDecimal winRateChange,
        int currentWinStreak
    ) {
    }

    private record WeeklySummary(
        LocalDate startDate,
        LocalDate endDate,
        AverageStatus dashboard,
        int routineCheckins,
        AverageWeight weight,
        AverageBloodPressure bloodPressure,
        BigDecimal moodAverage,
        AverageSleep sleep,
        CalorieSummary calories,
        WorkoutSummary workouts,
        Map<String, Long> sicknessesByType,
        Map<String, Long> sicknessesBySeverity,
        DecisionMetricsData decisions
    ) {
    }

    private record AverageStatus(
        BigDecimal routinesPercentage,
        BigDecimal weightPercentage,
        BigDecimal bloodPressurePercentage,
        BigDecimal flexibilityPercentage,
        BigDecimal mindPercentage
    ) {
    }

    private record AverageWeight(BigDecimal weightKg, BigDecimal fatPercentage, BigDecimal musclePercentage) {
    }

    private record AverageBloodPressure(BigDecimal systolic, BigDecimal diastolic) {
    }

    private record AverageSleep(
        BigDecimal totalSleepSeconds,
        BigDecimal deepSleepSeconds,
        BigDecimal remSleepSeconds,
        BigDecimal awakeSeconds,
        BigDecimal averageHeartRate,
        BigDecimal averageHrv
    ) {
    }

    private record CalorieSummary(
        int entryCount,
        int totalCalories,
        BigDecimal averageCalories,
        BigDecimal averageTargetCalories,
        BigDecimal averageDifferenceFromTarget
    ) {
    }

    private record WorkoutSummary(
        int workoutCount,
        int totalDurationSeconds,
        BigDecimal totalDistanceKm,
        int totalCalories,
        BigDecimal strengthVolumeKg
    ) {
    }
}
