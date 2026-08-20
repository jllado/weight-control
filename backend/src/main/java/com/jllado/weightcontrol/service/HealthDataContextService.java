package com.jllado.weightcontrol.service;

import static com.jllado.weightcontrol.api.dto.HealthDataContextDtos.*;

import com.jllado.weightcontrol.api.dto.CoachDtos;
import com.jllado.weightcontrol.domain.BackPainEpisode;
import com.jllado.weightcontrol.domain.BloodPressure;
import com.jllado.weightcontrol.domain.CoachDomain;
import com.jllado.weightcontrol.domain.DashboardReflection;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.DecisionOutcome;
import com.jllado.weightcontrol.domain.DecisionOutcomeType;
import com.jllado.weightcontrol.domain.Habit;
import com.jllado.weightcontrol.domain.HealthConstraint;
import com.jllado.weightcontrol.domain.LipidPanel;
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
import com.jllado.weightcontrol.repository.BackPainEpisodeRepository;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import com.jllado.weightcontrol.repository.DailyStatusRepository;
import com.jllado.weightcontrol.repository.DashboardReflectionRepository;
import com.jllado.weightcontrol.repository.DecisionOutcomeRepository;
import com.jllado.weightcontrol.repository.HabitRepository;
import com.jllado.weightcontrol.repository.HealthConstraintRepository;
import com.jllado.weightcontrol.repository.LipidPanelRepository;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class HealthDataContextService {

    static final int REFLECTION_CONTEXT_DAYS = 90;
    private static final int REFLECTION_DETAILED_DAYS = 30;
    private static final int YEAR_COMPARISON_WEEKS = 52;

    private final DashboardReflectionRepository reflectionRepository;
    private final DailyStatusRepository dailyStatusRepository;
    private final WeightRepository weightRepository;
    private final BloodPressureRepository bloodPressureRepository;
    private final LipidPanelRepository lipidPanelRepository;
    private final MoodRepository moodRepository;
    private final SleepRepository sleepRepository;
    private final CalorieService calorieService;
    private final WorkoutRepository workoutRepository;
    private final SicknessRepository sicknessRepository;
    private final BackPainEpisodeRepository backPainEpisodeRepository;
    private final DecisionOutcomeRepository decisionOutcomeRepository;
    private final HabitRepository habitRepository;
    private final HealthConstraintRepository healthConstraintRepository;
    private final RoutineRepository routineRepository;
    private final RoutineCheckinRepository routineCheckinRepository;
    private final DecisionOutcomeService decisionOutcomeService;
    private final WeeklyMetricsCalculator weeklyMetricsCalculator;

    public HealthDataContextService(
        DashboardReflectionRepository reflectionRepository,
        DailyStatusRepository dailyStatusRepository,
        WeightRepository weightRepository,
        BloodPressureRepository bloodPressureRepository,
        LipidPanelRepository lipidPanelRepository,
        MoodRepository moodRepository,
        SleepRepository sleepRepository,
        CalorieService calorieService,
        WorkoutRepository workoutRepository,
        SicknessRepository sicknessRepository,
        BackPainEpisodeRepository backPainEpisodeRepository,
        DecisionOutcomeRepository decisionOutcomeRepository,
        HabitRepository habitRepository,
        HealthConstraintRepository healthConstraintRepository,
        RoutineRepository routineRepository,
        RoutineCheckinRepository routineCheckinRepository,
        DecisionOutcomeService decisionOutcomeService,
        WeeklyMetricsCalculator weeklyMetricsCalculator
    ) {
        this.reflectionRepository = reflectionRepository;
        this.dailyStatusRepository = dailyStatusRepository;
        this.weightRepository = weightRepository;
        this.bloodPressureRepository = bloodPressureRepository;
        this.lipidPanelRepository = lipidPanelRepository;
        this.moodRepository = moodRepository;
        this.sleepRepository = sleepRepository;
        this.calorieService = calorieService;
        this.workoutRepository = workoutRepository;
        this.sicknessRepository = sicknessRepository;
        this.backPainEpisodeRepository = backPainEpisodeRepository;
        this.decisionOutcomeRepository = decisionOutcomeRepository;
        this.habitRepository = habitRepository;
        this.healthConstraintRepository = healthConstraintRepository;
        this.routineRepository = routineRepository;
        this.routineCheckinRepository = routineCheckinRepository;
        this.decisionOutcomeService = decisionOutcomeService;
        this.weeklyMetricsCalculator = weeklyMetricsCalculator;
    }

    public CoachDtos.CoachCatalogResponse getCoachCatalog(User user) {
        return getCoachCatalog(user, OffsetDateTime.now(DateTimes.USER_ZONE).withNano(0));
    }

    CoachDtos.CoachCatalogResponse getCoachCatalog(User user, OffsetDateTime currentLocalDateTime) {
        return new CoachDtos.CoachCatalogResponse(
            DateTimes.USER_ZONE.getId(),
            currentLocalDateTime,
            user.getLastCompletedDashboardDate(),
            EnumSet.allOf(CoachDomain.class).stream().map(domain -> getDomainAvailability(user, domain)).toList()
        );
    }

    public CoachDtos.CoachContextResponse getHealthContext(
        User user,
        LocalDate from,
        LocalDate to,
        Set<CoachDomain> domains
    ) {
        return getHealthContext(user, from, to, domains, OffsetDateTime.now(DateTimes.USER_ZONE).withNano(0));
    }

    CoachDtos.CoachContextResponse getHealthContext(
        User user,
        LocalDate from,
        LocalDate to,
        Set<CoachDomain> domains,
        OffsetDateTime currentLocalDateTime
    ) {
        validateCoachContextRange(from, to, domains, currentLocalDateTime.toLocalDate());
        Map<CoachDomain, Object> data = new LinkedHashMap<>();
        EnumSet.copyOf(domains).forEach(domain -> data.put(domain, getDomainContext(user, domain, from, to, currentLocalDateTime.toLocalDate())));
        LocalDate lastCompletedDate = user.getLastCompletedDashboardDate();
        return new CoachDtos.CoachContextResponse(
            DateTimes.USER_ZONE.getId(),
            currentLocalDateTime,
            from,
            to,
            lastCompletedDate,
            lastCompletedDate != null && !to.isAfter(lastCompletedDate),
            new CoachDtos.CoachDataSemantics(true, true, true),
            data
        );
    }

    private void validateCoachContextRange(LocalDate from, LocalDate to, Set<CoachDomain> domains, LocalDate currentDate) {
        if (domains.isEmpty()) {
            throw new BadRequestException("At least one Coach domain is required");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("Coach context start date must not be after the end date");
        }
        if (ChronoUnit.DAYS.between(from, to) >= REFLECTION_CONTEXT_DAYS) {
            throw new BadRequestException("Coach context cannot exceed 90 inclusive days");
        }
        if (to.isAfter(currentDate)) {
            throw new BadRequestException("Coach context end date cannot be in the future");
        }
    }

    private CoachDtos.DomainAvailability getDomainAvailability(User user, CoachDomain domain) {
        return switch (domain) {
            case PROFILE -> availability(domain, 1, null, null);
            case BODY -> availability(
                domain,
                weightRepository.countByUser(user),
                weightRepository.findFirstByUserOrderByMeasuredAtAsc(user)
                    .map(weight -> DateTimes.toLocalDate(weight.getMeasuredAt())).orElse(null),
                weightRepository.findFirstByUserOrderByMeasuredAtDesc(user)
                    .map(weight -> DateTimes.toLocalDate(weight.getMeasuredAt())).orElse(null)
            );
            case VITALS -> vitalsAvailability(user);
            case NUTRITION -> availability(
                domain,
                calorieService.countRecords(user),
                calorieService.findFirstRecordedDate(user).orElse(null),
                calorieService.findLastRecordedDate(user).orElse(null)
            );
            case TRAINING -> availability(
                domain,
                workoutRepository.countByUser(user),
                workoutRepository.findFirstByUserOrderByWorkoutDateAsc(user).map(Workout::getWorkoutDate).orElse(null),
                workoutRepository.findFirstByUserOrderByWorkoutDateDesc(user).map(Workout::getWorkoutDate).orElse(null)
            );
            case RECOVERY -> recoveryAvailability(user);
            case BEHAVIOR -> behaviorAvailability(user);
            case HEALTH_EVENTS -> healthEventsAvailability(user);
            case HEALTH_CONSTRAINTS -> availability(
                domain,
                healthConstraintRepository.countByUser(user),
                healthConstraintRepository.findFirstByUserOrderByStartDateAscIdAsc(user)
                    .map(HealthConstraint::getStartDate).orElse(null),
                healthConstraintRepository.findFirstByUserOrderByStartDateDescIdDesc(user)
                    .map(HealthConstraint::getStartDate).orElse(null)
            );
            case DECISIONS -> availability(
                domain,
                decisionOutcomeRepository.countByUser(user),
                decisionOutcomeRepository.findFirstByUserOrderByOutcomeDateAscIdAsc(user)
                    .map(DecisionOutcome::getOutcomeDate).orElse(null),
                decisionOutcomeRepository.findFirstByUserOrderByOutcomeDateDescIdDesc(user)
                    .map(DecisionOutcome::getOutcomeDate).orElse(null)
            );
            case REFLECTIONS -> availability(
                domain,
                reflectionRepository.countByUser(user),
                reflectionRepository.findFirstByUserOrderByReflectionDateAsc(user)
                    .map(DashboardReflection::getReflectionDate).orElse(null),
                reflectionRepository.findFirstByUserOrderByReflectionDateDesc(user)
                    .map(DashboardReflection::getReflectionDate).orElse(null)
            );
        };
    }

    private CoachDtos.DomainAvailability recoveryAvailability(User user) {
        LocalDate firstMoodDate = moodRepository.findFirstByUserOrderByMoodDateAsc(user).map(Mood::getMoodDate).orElse(null);
        LocalDate firstSleepDate = sleepRepository.findFirstByUserOrderBySleepDateAsc(user).map(Sleep::getSleepDate).orElse(null);
        LocalDate lastMoodDate = moodRepository.findFirstByUserOrderByMoodDateDesc(user).map(Mood::getMoodDate).orElse(null);
        LocalDate lastSleepDate = sleepRepository.findFirstByUserOrderBySleepDateDesc(user).map(Sleep::getSleepDate).orElse(null);
        return availability(
            CoachDomain.RECOVERY,
            moodRepository.countByUser(user) + sleepRepository.countByUser(user),
            earliest(firstMoodDate, firstSleepDate),
            latest(lastMoodDate, lastSleepDate)
        );
    }

    private CoachDtos.DomainAvailability vitalsAvailability(User user) {
        LocalDate firstBloodPressureDate = bloodPressureRepository.findFirstByUserOrderByMeasuredAtAsc(user)
            .map(bloodPressure -> DateTimes.toLocalDate(bloodPressure.getMeasuredAt())).orElse(null);
        LocalDate lastBloodPressureDate = bloodPressureRepository.findFirstByUserOrderByMeasuredAtDesc(user)
            .map(bloodPressure -> DateTimes.toLocalDate(bloodPressure.getMeasuredAt())).orElse(null);
        LocalDate firstLipidPanelDate = lipidPanelRepository.findFirstByUserOrderByPanelDateAsc(user)
            .map(LipidPanel::getPanelDate).orElse(null);
        LocalDate lastLipidPanelDate = lipidPanelRepository.findFirstByUserOrderByPanelDateDesc(user)
            .map(LipidPanel::getPanelDate).orElse(null);
        return availability(
            CoachDomain.VITALS,
            bloodPressureRepository.countByUser(user) + lipidPanelRepository.countByUser(user),
            earliest(firstBloodPressureDate, firstLipidPanelDate),
            latest(lastBloodPressureDate, lastLipidPanelDate)
        );
    }

    private CoachDtos.DomainAvailability behaviorAvailability(User user) {
        LocalDate firstStatusDate = dailyStatusRepository.findFirstByUserOrderByStatusDateAsc(user)
            .map(DailyStatus::getStatusDate).orElse(null);
        LocalDate firstHabitDate = habitRepository.findFirstByUserOrderByStartDateAsc(user)
            .map(habit -> DateTimes.toLocalDate(habit.getStartDate())).orElse(null);
        LocalDate firstRoutineDate = routineRepository.findFirstByUserOrderByStartDateAsc(user)
            .map(routine -> DateTimes.toLocalDate(routine.getStartDate())).orElse(null);
        LocalDate firstCheckinDate = routineCheckinRepository.findFirstByRoutineUserOrderByCheckedAtAsc(user)
            .map(checkin -> DateTimes.toLocalDate(checkin.getCheckedAt())).orElse(null);
        LocalDate lastStatusDate = dailyStatusRepository.findFirstByUserOrderByStatusDateDesc(user)
            .map(DailyStatus::getStatusDate).orElse(null);
        LocalDate lastHabitStartDate = habitRepository.findFirstByUserOrderByStartDateDesc(user)
            .map(habit -> DateTimes.toLocalDate(habit.getStartDate())).orElse(null);
        LocalDate lastHabitRecordedDate = habitRepository.findFirstByUserAndLastTimeDateIsNotNullOrderByLastTimeDateDesc(user)
            .map(habit -> DateTimes.toLocalDate(habit.getLastTimeDate())).orElse(null);
        LocalDate lastRoutineStartDate = routineRepository.findFirstByUserOrderByStartDateDesc(user)
            .map(routine -> DateTimes.toLocalDate(routine.getStartDate())).orElse(null);
        LocalDate lastRoutineRecordedDate = routineRepository.findFirstByUserAndLastTimeDateIsNotNullOrderByLastTimeDateDesc(user)
            .map(routine -> DateTimes.toLocalDate(routine.getLastTimeDate())).orElse(null);
        LocalDate lastCheckinDate = routineCheckinRepository.findFirstByRoutineUserOrderByCheckedAtDesc(user)
            .map(checkin -> DateTimes.toLocalDate(checkin.getCheckedAt())).orElse(null);
        long recordCount = dailyStatusRepository.countByUser(user)
            + habitRepository.countByUser(user)
            + routineRepository.countByUser(user)
            + routineCheckinRepository.countByRoutineUser(user);
        return availability(
            CoachDomain.BEHAVIOR,
            recordCount,
            earliest(firstStatusDate, firstHabitDate, firstRoutineDate, firstCheckinDate),
            latest(
                lastStatusDate,
                lastHabitStartDate,
                lastHabitRecordedDate,
                lastRoutineStartDate,
                lastRoutineRecordedDate,
                lastCheckinDate
            )
        );
    }

    private CoachDtos.DomainAvailability healthEventsAvailability(User user) {
        LocalDate firstSicknessDate = sicknessRepository.findFirstByUserOrderBySicknessDateAsc(user)
            .map(Sickness::getSicknessDate).orElse(null);
        LocalDate firstBackPainDate = backPainEpisodeRepository.findFirstByUserOrderByEpisodeDateAscEpisodeTimeAscIdAsc(user)
            .map(BackPainEpisode::getEpisodeDate).orElse(null);
        LocalDate lastSicknessDate = sicknessRepository.findFirstByUserOrderBySicknessDateDesc(user)
            .map(Sickness::getSicknessDate).orElse(null);
        LocalDate lastBackPainDate = backPainEpisodeRepository.findFirstByUserOrderByEpisodeDateDescEpisodeTimeDescIdDesc(user)
            .map(BackPainEpisode::getEpisodeDate).orElse(null);
        return availability(
            CoachDomain.HEALTH_EVENTS,
            sicknessRepository.countByUser(user) + backPainEpisodeRepository.countByUser(user),
            earliest(firstSicknessDate, firstBackPainDate),
            latest(lastSicknessDate, lastBackPainDate)
        );
    }

    private CoachDtos.DomainAvailability availability(
        CoachDomain domain,
        long recordCount,
        LocalDate firstDate,
        LocalDate lastDate
    ) {
        return new CoachDtos.DomainAvailability(domain, recordCount, firstDate, lastDate);
    }

    private LocalDate earliest(LocalDate... dates) {
        return java.util.Arrays.stream(dates).filter(java.util.Objects::nonNull).min(LocalDate::compareTo).orElse(null);
    }

    private LocalDate latest(LocalDate... dates) {
        return java.util.Arrays.stream(dates).filter(java.util.Objects::nonNull).max(LocalDate::compareTo).orElse(null);
    }

    private Object getDomainContext(
        User user,
        CoachDomain domain,
        LocalDate from,
        LocalDate to,
        LocalDate currentDate
    ) {
        return switch (domain) {
            case PROFILE -> toProfileData(user, currentDate);
            case BODY -> bodyContext(user, from, to);
            case VITALS -> vitalsContext(user, from, to);
            case NUTRITION -> nutritionContext(user, from, to);
            case TRAINING -> trainingContext(user, from, to);
            case RECOVERY -> recoveryContext(user, from, to);
            case BEHAVIOR -> behaviorContext(user, from, to);
            case HEALTH_EVENTS -> healthEventsContext(user, from, to);
            case HEALTH_CONSTRAINTS -> healthConstraintsContext(user, from, to);
            case DECISIONS -> decisionsContext(user, from, to);
            case REFLECTIONS -> reflectionsContext(user, from, to);
        };
    }

    private CoachDtos.BodyContext bodyContext(User user, LocalDate from, LocalDate to) {
        List<CoachDtos.BodyMeasurementData> measurements = weightRepository
            .findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(
                user,
                DateTimes.startOfDay(from),
                DateTimes.startOfDay(to).plusDays(1)
            ).stream()
            .map(this::toCoachBodyMeasurementData)
            .toList();
        return new CoachDtos.BodyContext(measurements);
    }

    private CoachDtos.VitalsContext vitalsContext(User user, LocalDate from, LocalDate to) {
        List<BloodPressureData> bloodPressures = bloodPressureRepository
            .findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(
                user,
                DateTimes.startOfDay(from),
                DateTimes.startOfDay(to).plusDays(1)
            ).stream()
            .map(this::toBloodPressureData)
            .toList();
        List<CoachDtos.LipidPanelData> lipidPanels = lipidPanelRepository
            .findByUserAndPanelDateBetweenOrderByPanelDateAsc(user, from, to).stream()
            .map(this::toCoachLipidPanelData)
            .toList();
        return new CoachDtos.VitalsContext(bloodPressures, lipidPanels);
    }

    private CoachDtos.LipidPanelData toCoachLipidPanelData(LipidPanel panel) {
        return new CoachDtos.LipidPanelData(
            panel.getPanelDate(),
            panel.getTotalCholesterol(),
            panel.getHdlCholesterol(),
            panel.getLdlCholesterol(),
            panel.getTriglycerides()
        );
    }

    private CoachDtos.NutritionContext nutritionContext(User user, LocalDate from, LocalDate to) {
        return new CoachDtos.NutritionContext(
            calorieService.findBetween(user, from, to).stream().map(this::toCalorieData).toList()
        );
    }

    private WorkoutContextData trainingContext(User user, LocalDate from, LocalDate to) {
        List<Workout> workouts = workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, from, to);
        return toWorkoutContextData(workouts);
    }

    private CoachDtos.RecoveryContext recoveryContext(User user, LocalDate from, LocalDate to) {
        List<MoodData> moods = moodRepository.findByUserAndMoodDateBetweenOrderByMoodDateAsc(user, from, to).stream()
            .map(this::toMoodData)
            .toList();
        List<SleepData> sleeps = sleepRepository.findByUserAndSleepDateBetweenOrderBySleepDateAsc(user, from, to).stream()
            .map(this::toSleepData)
            .toList();
        return new CoachDtos.RecoveryContext(moods, sleeps);
    }

    private CoachDtos.BehaviorContext behaviorContext(User user, LocalDate from, LocalDate to) {
        List<CoachDtos.CoachDailyStatusData> statuses = dailyStatusRepository
            .findByUserAndStatusDateBetweenOrderByStatusDateAsc(user, from, to).stream()
            .map(status -> toCoachDailyStatusData(status, user.getLastCompletedDashboardDate()))
            .toList();
        List<HabitData> habits = habitRepository.findByUserOrderByStartDateAsc(user).stream()
            .filter(habit -> !DateTimes.toLocalDate(habit.getStartDate()).isAfter(to))
            .map(habit -> toHabitData(habit, to))
            .toList();
        List<CoachDtos.CoachRoutineData> routines = routineRepository.findByUserOrderByStartDateAsc(user).stream()
            .filter(routine -> !DateTimes.toLocalDate(routine.getStartDate()).isAfter(to))
            .map(routine -> toCoachRoutineData(routine, from, to))
            .toList();
        return new CoachDtos.BehaviorContext(statuses, habits, routines);
    }

    private CoachDtos.HealthEventsContext healthEventsContext(User user, LocalDate from, LocalDate to) {
        List<SicknessData> sicknesses = sicknessRepository
            .findByUserAndSicknessDateBetweenOrderBySicknessDateAsc(user, from, to).stream()
            .map(this::toSicknessData)
            .toList();
        List<CoachDtos.BackPainEpisodeData> backPainEpisodes = backPainEpisodeRepository
            .findByUserAndEpisodeDateBetweenOrderByEpisodeDateAscEpisodeTimeAscIdAsc(user, from, to).stream()
            .map(this::toBackPainEpisodeData)
            .toList();
        return new CoachDtos.HealthEventsContext(sicknesses, backPainEpisodes);
    }

    private CoachDtos.HealthConstraintsContext healthConstraintsContext(User user, LocalDate from, LocalDate to) {
        return new CoachDtos.HealthConstraintsContext(
            healthConstraintRepository.findActiveOverlapping(user, from, to).stream()
                .map(constraint -> new CoachDtos.HealthConstraintData(
                    constraint.getType(),
                    constraint.getTitle(),
                    constraint.getDetails(),
                    constraint.getSource(),
                    constraint.getStartDate(),
                    constraint.getEndDate()
                ))
                .toList()
        );
    }

    private CoachDtos.DecisionsContext decisionsContext(User user, LocalDate from, LocalDate to) {
        List<DecisionOutcome> decisions = decisionOutcomeRepository
            .findByUserAndOutcomeDateBetweenOrderByOutcomeDateAscIdAsc(user, from, to);
        long wins = decisions.stream().filter(decision -> decision.getOutcome() == DecisionOutcomeType.WIN).count();
        long misses = decisions.size() - wins;
        BigDecimal winRate = decisions.isEmpty() ? null : Numbers.percentage(wins, decisions.size());
        int endingWinStreak = 0;
        for (int index = decisions.size() - 1; index >= 0 && decisions.get(index).getOutcome() == DecisionOutcomeType.WIN; index--) {
            endingWinStreak++;
        }
        List<CoachDtos.DecisionData> outcomes = decisions.stream()
            .map(decision -> new CoachDtos.DecisionData(decision.getOutcomeDate(), decision.getOutcome()))
            .toList();
        return new CoachDtos.DecisionsContext(
            outcomes,
            new CoachDtos.DecisionRangeSummary(wins, misses, winRate, endingWinStreak)
        );
    }

    private CoachDtos.ReflectionsContext reflectionsContext(User user, LocalDate from, LocalDate to) {
        return new CoachDtos.ReflectionsContext(
            reflectionRepository.findByUserAndReflectionDateBetweenOrderByReflectionDateAsc(user, from, to).stream()
                .map(this::toRecentReflectionData)
                .toList()
        );
    }

    public ReflectionContext getReflectionContext(User user, LocalDate selectedDate) {
        LocalDate contextStart = selectedDate.minusDays(REFLECTION_CONTEXT_DAYS - 1L);
        LocalDate detailedStart = selectedDate.minusDays(REFLECTION_DETAILED_DAYS - 1L);
        LocalDate baselineEnd = detailedStart.minusDays(1);
        LocalDate dataStart = DateTimes.startOfDashboardWeek(selectedDate).minusWeeks(YEAR_COMPARISON_WEEKS);
        OffsetDateTime dataStartTime = DateTimes.startOfDay(dataStart);
        OffsetDateTime selectedEndExclusive = DateTimes.startOfDay(selectedDate).plusDays(1);

        List<DailyStatus> statuses = dailyStatusRepository.findByUserAndStatusDateBetweenOrderByStatusDateAsc(user, dataStart, selectedDate);
        List<Weight> weights = weightRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(user, dataStartTime, selectedEndExclusive);
        List<BloodPressure> bloodPressures = bloodPressureRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(user, dataStartTime, selectedEndExclusive);
        List<Mood> moods = moodRepository.findByUserAndMoodDateBetweenOrderByMoodDateAsc(user, dataStart, selectedDate);
        List<Sleep> sleeps = sleepRepository.findByUserAndSleepDateBetweenOrderBySleepDateAsc(user, dataStart, selectedDate);
        List<CalorieService.DailyCalories> calories = calorieService.findBetween(user, dataStart, selectedDate);
        List<Workout> workouts = workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, dataStart, selectedDate);
        List<Sickness> sicknesses = sicknessRepository.findByUserAndSicknessDateBetweenOrderBySicknessDateAsc(user, dataStart, selectedDate);
        List<DecisionOutcome> decisions = decisionOutcomeRepository.findByUserAndOutcomeDateBetweenOrderByOutcomeDateAscIdAsc(user, dataStart, selectedDate);
        List<RecentReflectionData> recentReflections = reflectionRepository
            .findTop7ByUserAndReflectionDateBeforeOrderByReflectionDateDesc(user, selectedDate).stream()
            .map(this::toRecentReflectionData)
            .toList();
        List<Routine> routines = routineRepository.findByUserOrderByStartDateAsc(user).stream()
            .filter(routine -> !DateTimes.toLocalDate(routine.getStartDate()).isAfter(selectedDate))
            .toList();
        Map<Routine, List<RoutineCheckin>> checkins = routines.stream().collect(Collectors.toMap(
            Function.identity(),
            routine -> routineCheckinRepository.findByRoutineAndCheckedAtBetweenOrderByCheckedAtAsc(routine, dataStartTime, selectedEndExclusive)
        ));
        List<RoutineCheckin> routineCheckins = checkins.values().stream().flatMap(List::stream).toList();
        WeeklyMetricsCalculator.Input weeklyMetricsInput = new WeeklyMetricsCalculator.Input(
            statuses,
            weights,
            bloodPressures,
            moods,
            sleeps,
            calories,
            workouts,
            sicknesses,
            decisions,
            routineCheckins
        );

        return new ReflectionContext(
            selectedDate,
            contextStart,
            detailedStart,
            baselineEnd,
            toProfileData(user, selectedDate),
            new DataSemantics(true),
            recentReflections,
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
            detailed(calories, CalorieService.DailyCalories::date, detailedStart).stream().map(this::toCalorieData).toList(),
            toWorkoutContextData(detailed(workouts, Workout::getWorkoutDate, detailedStart)),
            detailed(sicknesses, Sickness::getSicknessDate, detailedStart).stream().map(this::toSicknessData).toList(),
            detailed(decisions, DecisionOutcome::getOutcomeDate, detailedStart).stream().map(this::toDecisionData).toList(),
            toDecisionSummary(decisionOutcomeService.summarize(user, selectedDate)),
            toWeekProgress(weeklyMetricsCalculator.progress(user, selectedDate, weeklyMetricsInput)),
            weeklyMetricsCalculator.baselineWeeks(user, contextStart, baselineEnd, weeklyMetricsInput).stream()
                .map(this::toWeeklySummary)
                .toList()
        );
    }

    private <T> List<T> detailed(List<T> values, Function<T, LocalDate> date, LocalDate detailedStart) {
        return values.stream().filter(value -> !date.apply(value).isBefore(detailedStart)).toList();
    }

    private WeekProgress toWeekProgress(WeeklyMetrics.Progress progress) {
        return new WeekProgress(
            progress.completeWeek(),
            toWeeklySummary(progress.currentPeriod()),
            toWeeklySummary(progress.previousComparablePeriod()),
            toWeeklySummary(progress.yearAgoComparablePeriod())
        );
    }

    private WeeklySummary toWeeklySummary(WeeklyMetrics.Summary summary) {
        return new WeeklySummary(
            summary.startDate(),
            summary.endDate(),
            toAverageStatus(summary.dashboard()),
            summary.routineCheckins(),
            toAverageWeight(summary.weight()),
            toAverageBloodPressure(summary.bloodPressure()),
            summary.moodAverage(),
            toAverageSleep(summary.sleep()),
            toCalorieSummary(summary.calories()),
            toWorkoutSummary(summary.workouts()),
            summary.sicknessesByType(),
            summary.sicknessesBySeverity(),
            new DecisionMetricsData(summary.decisions().wins(), summary.decisions().misses(), summary.decisions().winRate())
        );
    }

    private AverageStatus toAverageStatus(WeeklyMetrics.AverageStatus status) {
        return status == null ? null : new AverageStatus(
            status.routinesPercentage(),
            status.weightPercentage(),
            status.bloodPressurePercentage(),
            status.flexibilityPercentage(),
            status.mindPercentage()
        );
    }

    private AverageWeight toAverageWeight(WeeklyMetrics.AverageWeight weight) {
        return weight == null ? null : new AverageWeight(weight.weightKg(), weight.fatPercentage(), weight.musclePercentage());
    }

    private AverageBloodPressure toAverageBloodPressure(WeeklyMetrics.AverageBloodPressure bloodPressure) {
        return bloodPressure == null ? null : new AverageBloodPressure(bloodPressure.systolic(), bloodPressure.diastolic());
    }

    private AverageSleep toAverageSleep(WeeklyMetrics.AverageSleep sleep) {
        return sleep == null ? null : new AverageSleep(
            sleep.totalSleepSeconds(),
            sleep.deepSleepSeconds(),
            sleep.remSleepSeconds(),
            sleep.awakeSeconds(),
            sleep.averageHeartRate(),
            sleep.averageHrv()
        );
    }

    private CalorieSummary toCalorieSummary(WeeklyMetrics.CalorieSummary calories) {
        return new CalorieSummary(
            calories.entryCount(),
            calories.totalCalories(),
            calories.averageCalories(),
            calories.averageTargetCalories(),
            calories.averageDifferenceFromTarget()
        );
    }

    private WorkoutSummary toWorkoutSummary(WeeklyMetrics.WorkoutSummary workouts) {
        return new WorkoutSummary(
            workouts.workoutCount(),
            workouts.totalDurationSeconds(),
            workouts.totalDistanceKm(),
            workouts.totalCalories(),
            workouts.strengthVolumeKg()
        );
    }

    private RecentReflectionData toRecentReflectionData(DashboardReflection reflection) {
        return new RecentReflectionData(
            reflection.getReflectionDate(),
            reflection.getTitle(),
            reflection.getSummary(),
            reflection.getPositiveSignals(),
            reflection.getWatchouts(),
            reflection.getNextActions()
        );
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
        return new HabitData(habit.getName(), DateTimes.toLocalDate(habit.getStartDate()), habit.getDuration(), lastRecordedDate);
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

    private CoachDtos.CoachDailyStatusData toCoachDailyStatusData(DailyStatus status, LocalDate lastCompletedDate) {
        return new CoachDtos.CoachDailyStatusData(
            status.getStatusDate(),
            lastCompletedDate != null && !status.getStatusDate().isAfter(lastCompletedDate),
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

    private CoachDtos.BodyMeasurementData toCoachBodyMeasurementData(Weight weight) {
        return new CoachDtos.BodyMeasurementData(
            DateTimes.toLocalDate(weight.getMeasuredAt()),
            weight.getWeight(),
            weight.getFatPercentage(),
            weight.getFat(),
            weight.getMuscle(),
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
        return new MoodData(mood.getMoodDate(), mood.getPeriod(), mood.getValue(), mood.getNote());
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

    private CalorieData toCalorieData(CalorieService.DailyCalories calorie) {
        return new CalorieData(calorie.date(), calorie.calories());
    }

    private WorkoutContextData toWorkoutContextData(List<Workout> workouts) {
        Map<String, List<WorkoutLine>> linesByExercise = workouts.stream()
            .flatMap(workout -> workout.getLines().stream())
            .collect(Collectors.groupingBy(line -> line.getExercise().getName(), LinkedHashMap::new, Collectors.toList()));
        return new WorkoutContextData(
            workouts.stream().map(this::toWorkoutData).toList(),
            linesByExercise.entrySet().stream().map(entry -> toWorkoutExerciseData(entry.getKey(), entry.getValue())).toList()
        );
    }

    private WorkoutData toWorkoutData(Workout workout) {
        List<WorkoutSegment> segments = workout.getLines().stream().flatMap(line -> line.getSegments().stream()).toList();
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
        return new SicknessData(sickness.getSicknessDate(), sickness.getType(), sickness.getSeverity(), sickness.getNote());
    }

    private CoachDtos.CoachRoutineData toCoachRoutineData(Routine routine, LocalDate from, LocalDate to) {
        List<LocalDate> checkinDates = routineCheckinRepository
            .findByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThanOrderByCheckedAtAsc(
                routine,
                DateTimes.startOfDay(from),
                DateTimes.startOfDay(to).plusDays(1)
            ).stream()
            .map(checkin -> DateTimes.toLocalDate(checkin.getCheckedAt()))
            .toList();
        return new CoachDtos.CoachRoutineData(
            routine.getName(),
            DateTimes.toLocalDate(routine.getStartDate()),
            routine.getTypes().stream().map(Enum::name).toList(),
            checkinDates
        );
    }

    private CoachDtos.BackPainEpisodeData toBackPainEpisodeData(BackPainEpisode episode) {
        return new CoachDtos.BackPainEpisodeData(
            episode.getEpisodeDate(),
            episode.getEpisodeTime(),
            episode.getPeriod(),
            episode.getRegion(),
            episode.getSide(),
            episode.getSeverity(),
            episode.getNote()
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
}
