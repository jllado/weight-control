package com.jllado.weightcontrol.service;

import static com.jllado.weightcontrol.api.dto.PersonalRecordDtos.*;

import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.PersonalRecordSnapshotRepository;
import com.jllado.weightcontrol.repository.PersonalRecordEventRepository;
import com.jllado.weightcontrol.repository.PersonalRecordSettingRepository;
import com.jllado.weightcontrol.repository.DailyStatusRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.service.PersonalRecordCalculator.CurrentRecord;
import com.jllado.weightcontrol.service.PersonalRecordCalculator.BehaviorSubject;
import com.jllado.weightcontrol.service.PersonalRecordCalculator.HistoryEvent;
import com.jllado.weightcontrol.service.PersonalRecordCalculator.Series;
import com.jllado.weightcontrol.service.PersonalRecordCalculator.Source;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class PersonalRecordService {

    private final PersonalRecordSnapshotRepository repository;
    private final PersonalRecordEventRepository eventRepository;
    private final PersonalRecordSettingRepository settingRepository;
    private final PersonalRecordCalculator calculator;
    private final WeightService weightService;
    private final WorkoutService workoutService;
    private final BloodPressureService bloodPressureService;
    private final LipidPanelService lipidPanelService;
    private final MoodService moodService;
    private final SleepService sleepService;
    private final MealService mealService;
    private final HabitService habitService;
    private final RoutineService routineService;
    private final DailyStatusRepository dailyStatusRepository;
    private final UserRepository userRepository;

    public PersonalRecordService(
        PersonalRecordSnapshotRepository repository,
        PersonalRecordEventRepository eventRepository,
        PersonalRecordSettingRepository settingRepository,
        PersonalRecordCalculator calculator,
        WeightService weightService,
        WorkoutService workoutService,
        BloodPressureService bloodPressureService,
        LipidPanelService lipidPanelService,
        MoodService moodService,
        SleepService sleepService,
        MealService mealService,
        HabitService habitService,
        RoutineService routineService,
        DailyStatusRepository dailyStatusRepository,
        UserRepository userRepository
    ) {
        this.repository = repository;
        this.eventRepository = eventRepository;
        this.settingRepository = settingRepository;
        this.calculator = calculator;
        this.weightService = weightService;
        this.workoutService = workoutService;
        this.bloodPressureService = bloodPressureService;
        this.lipidPanelService = lipidPanelService;
        this.moodService = moodService;
        this.sleepService = sleepService;
        this.mealService = mealService;
        this.habitService = habitService;
        this.routineService = routineService;
        this.dailyStatusRepository = dailyStatusRepository;
        this.userRepository = userRepository;
    }

    public List<CurrentRecordResponse> current(User user, PersonalRecordDomain domain, PersonalRecordMetric metric, Long exerciseId) {
        List<CurrentRecordResponse> records = new ArrayList<>(repository.findByUser(user).stream()
            .filter(snapshot -> domain == null || snapshot.getDomain() == domain)
            .filter(snapshot -> metric == null || snapshot.getMetric() == metric)
            .filter(snapshot -> exerciseId == null || snapshot.getExercise() != null && snapshot.getExercise().getId().equals(exerciseId))
            .sorted(snapshotComparator())
            .map(this::toCurrentResponse)
            .toList());
        calculateRoutines(user).current().stream()
            .filter(record -> domain == null || record.series().metric().getDomain() == domain)
            .filter(record -> metric == null || record.series().metric() == metric)
            .filter(record -> exerciseId == null)
            .map(record -> toCurrentResponse(toSnapshot(user, record)))
            .forEach(records::add);
        records.sort(currentResponseComparator());
        return List.copyOf(records);
    }

    public List<HistoryEventResponse> workoutHistory(User user, Set<Long> workoutIds) {
        if (workoutIds.isEmpty()) {
            return List.of();
        }
        return eventRepository.findByUserAndSourceTypeAndSourceIdIn(user, PersonalRecordSourceType.WORKOUT, workoutIds).stream()
            .sorted(eventComparator())
            .map(this::toHistoryResponse)
            .toList();
    }

    public CoachRecordAvailability coachAvailability(User user) {
        List<PersonalRecordEvent> events = eventRepository.findByUser(user);
        List<java.time.LocalDate> dates = events.stream().map(PersonalRecordEvent::getRecordDate).toList();
        return new CoachRecordAvailability(
            current(user, null, null, null).size(),
            dates.stream().min(java.time.LocalDate::compareTo).orElse(null),
            dates.stream().max(java.time.LocalDate::compareTo).orElse(null)
        );
    }

    public com.jllado.weightcontrol.api.dto.CoachDtos.RecordsContext coachContext(
        User user,
        java.time.LocalDate from,
        java.time.LocalDate to,
        int page,
        int pageSize
    ) {
        PersonalRecordCalculator.Calculation calculation = calculate(user);
        List<com.jllado.weightcontrol.api.dto.CoachDtos.CoachRecordData> current = calculation.current().stream().map(record -> {
            var response = toCurrentResponse(toSnapshot(user, record));
            return new com.jllado.weightcontrol.api.dto.CoachDtos.CoachRecordData(
                response.metric(), response.metricLabel(), response.domain(), response.direction(), response.value(), response.unit(),
                response.recordDate(), response.subject().type(), response.subject().label(), response.qualifier() == null ? null : response.qualifier().label()
            );
        }).toList();
        List<com.jllado.weightcontrol.api.dto.CoachDtos.CoachRecordEventData> progression = eventRepository.findByUser(user).stream()
            .filter(event -> !event.getRecordDate().isBefore(from) && !event.getRecordDate().isAfter(to))
            .sorted(eventComparator())
            .map(this::toHistoryResponse)
            .map(response -> new com.jllado.weightcontrol.api.dto.CoachDtos.CoachRecordEventData(
                response.metric(), response.metricLabel(), response.domain(), response.direction(), response.kind(), response.value(), response.previousValue(),
                response.unit(), response.recordDate(), response.currentRecord(), response.subject().type(), response.subject().label(),
                response.qualifier() == null ? null : response.qualifier().label()
            )).toList();
        long offset = (long) page * pageSize;
        return new com.jllado.weightcontrol.api.dto.CoachDtos.RecordsContext(
            current.stream().skip(offset).limit(pageSize).toList(),
            progression.stream().skip(offset).limit(pageSize).toList(),
            page,
            pageSize,
            current.size(),
            progression.size(),
            offset + pageSize < current.size() || offset + pageSize < progression.size()
        );
    }

    public List<CatalogMetricResponse> catalog(User user) {
        Map<PersonalRecordCatalogMetric, PersonalRecordMode> overrides = overrides(user);
        return Arrays.stream(PersonalRecordCatalogMetric.values()).map(metric -> new CatalogMetricResponse(
            metric,
            metric.getLabel(),
            metric.getDomain(),
            metric.getUnit(),
            metric.getPrecision(),
            metric.getDefaultMode(),
            overrides.getOrDefault(metric, metric.getDefaultMode()),
            Arrays.stream(PersonalRecordDirection.values())
                .map(direction -> PersonalRecordMetric.forDirection(metric, direction))
                .map(recordMetric -> new CatalogDirectionResponse(recordMetric.getDirection(), recordMetric, recordMetric.getLabel()))
                .toList()
        )).toList();
    }

    public List<CatalogMetricResponse> replaceSettings(User user, SettingsRequest request) {
        lockUser(user);
        Set<PersonalRecordCatalogMetric> metrics = new HashSet<>();
        request.overrides().forEach(override -> {
            if (!metrics.add(override.metric())) {
                throw new BadRequestException("Personal record setting metrics must be unique");
            }
        });
        settingRepository.deleteByUser(user);
        settingRepository.flush();
        settingRepository.saveAll(request.overrides().stream()
            .filter(override -> override.mode() != override.metric().getDefaultMode())
            .map(override -> setting(user, override))
            .toList());
        rebuildRecords(user);
        return catalog(user);
    }

    public HistoryPageResponse history(
        User user,
        PersonalRecordDomain domain,
        PersonalRecordMetric metric,
        Long exerciseId,
        Set<Long> workoutIds,
        String eventKey,
        int page,
        int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("History page must be non-negative and size must be between 1 and 100");
        }
        List<HistoryEventResponse> filtered = eventRepository.findByUser(user).stream()
            .filter(event -> domain == null || event.getDomain() == domain)
            .filter(event -> metric == null || event.getMetric() == metric)
            .filter(event -> exerciseId == null || event.getExercise() != null && event.getExercise().getId().equals(exerciseId))
            .filter(event -> workoutIds.isEmpty() || event.getSourceType() == PersonalRecordSourceType.WORKOUT && workoutIds.contains(event.getSourceId()))
            .sorted(eventComparator())
            .map(this::toHistoryResponse)
            .filter(event -> eventKey == null || event.eventKey().equals(eventKey))
            .toList();
        int from = Math.min(page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (filtered.size() + size - 1) / size;
        return new HistoryPageResponse(filtered.subList(from, to), page, size, filtered.size(), totalPages);
    }

    public List<HistoryEventResponse> improvedHistoryBetween(User user, java.time.LocalDate from, java.time.LocalDate to) {
        return eventRepository.findByUser(user).stream()
            .filter(event -> event.getKind() == PersonalRecordEventKind.IMPROVED)
            .filter(event -> !event.getRecordDate().isBefore(from) && !event.getRecordDate().isAfter(to))
            .sorted(eventComparator())
            .map(this::toHistoryResponse)
            .toList();
    }

    public Map<String, BigDecimal> captureCurrentValues(User user) {
        lockUser(user);
        Map<String, BigDecimal> values = new HashMap<>();
        repository.findByUser(user).forEach(snapshot -> values.put(snapshot.getSeriesKey(), snapshot.getValue()));
        return values;
    }

    public List<RecordAchievementResponse> rebuildAndFindAchievements(
        User user,
        Map<String, BigDecimal> previousValues,
        PersonalRecordSourceType sourceType,
        Long sourceId,
        boolean celebrate
    ) {
        return rebuildAndFindAchievements(user, previousValues, sourceType, sourceId, null, false, celebrate);
    }

    public List<RecordAchievementResponse> rebuildAndFindAchievements(
        User user,
        Map<String, BigDecimal> previousValues,
        PersonalRecordSourceType sourceType,
        Long sourceId,
        java.time.LocalDate affectedDate,
        boolean includeNutritionDay,
        boolean celebrate
    ) {
        List<CurrentRecord> current = rebuildRecords(user);
        if (!celebrate) {
            return List.of();
        }
        return current.stream()
            .filter(record -> record.source().contributes(sourceType, sourceId)
                || includeNutritionDay && record.source().type() == PersonalRecordSourceType.NUTRITION_DAY && record.date().equals(affectedDate))
            .filter(record -> isNewRecord(record, previousValues.get(record.series().key())))
            .map(record -> toAchievement(record, previousValues.get(record.series().key())))
            .toList();
    }

    public void rebuild(User user) {
        rebuildRecords(user);
    }

    public List<RecordAchievementResponse> rebuildAndFindBehaviorAchievements(User user, Map<String, BigDecimal> previousValues, String subjectType, Long subjectId) {
        return rebuildRecords(user).stream()
            .filter(record -> record.series().metric().getDomain() == PersonalRecordDomain.BEHAVIOR)
            .filter(record -> record.series().behaviorSubject() != null && record.series().behaviorSubject().type().equals(subjectType))
            .filter(record -> Objects.equals(record.series().behaviorSubject().id(), subjectId))
            .filter(record -> isNewRecord(record, previousValues.get(record.series().key())))
            .map(record -> toAchievement(record, previousValues.get(record.series().key())))
            .toList();
    }

    public List<RecordAchievementResponse> routineMilestoneAchievement(User user, RoutineService.RoutineCheckinResult result) {
        if (result.checkin() == null || result.routine().getBestStrike() <= result.previousBestStreak() || !RoutineStreakMilestones.isMilestone(result.routine().getBestStrike())) {
            return List.of();
        }
        PersonalRecordMode mode = overrides(user).getOrDefault(PersonalRecordCatalogMetric.ROUTINE_BEST_STREAK, PersonalRecordCatalogMetric.ROUTINE_BEST_STREAK.getDefaultMode());
        if (!mode.directions().contains(PersonalRecordDirection.MAXIMUM)) {
            return List.of();
        }
        int days = result.routine().getBestStrike();
        Integer previous = RoutineStreakMilestones.previousMilestone(days);
        Series series = new Series(
            PersonalRecordMetric.ROUTINE_BEST_STREAK_MAXIMUM,
            null,
            null,
            new BehaviorSubject("ROUTINE", result.routine().getId(), result.routine().getName())
        );
        CurrentRecord record = new CurrentRecord(
            series,
            BigDecimal.valueOf(days),
            com.jllado.weightcontrol.util.DateTimes.toLocalDate(result.checkin().getCheckedAt()),
            new Source(PersonalRecordSourceType.ROUTINE_CHECKIN, result.checkin().getId(), null, null)
        );
        return List.of(toAchievement(record, previous == null ? null : BigDecimal.valueOf(previous)));
    }

    private List<CurrentRecord> rebuildRecords(User user) {
        lockUser(user);
        PersonalRecordCalculator.Calculation calculation = calculate(user);
        List<CurrentRecord> current = calculation.current();
        repository.deleteByUser(user);
        eventRepository.deleteByUser(user);
        repository.flush();
        repository.saveAll(current.stream()
            .filter(record -> record.series().behaviorSubject() == null || !record.series().behaviorSubject().type().equals("ROUTINE"))
            .map(record -> toSnapshot(user, record))
            .toList());
        eventRepository.saveAll(calculation.history().stream().map(event -> toEvent(user, event)).toList());
        return current;
    }

    private PersonalRecordCalculator.Calculation calculate(User user) {
        return calculate(user, true);
    }

    private PersonalRecordCalculator.Calculation calculate(User user, boolean includeRoutines) {
        return calculator.calculate(new PersonalRecordCalculator.Sources(
            user,
            weightService.findAll(user),
            workoutService.findAll(user),
            bloodPressureService.findAll(user),
            lipidPanelService.findAll(user),
            moodService.findAll(user),
            sleepService.findAll(user),
            mealService.findAll(user),
            habitService.findAll(user).stream().map(habit -> new PersonalRecordCalculator.HabitSource(habit, habitService.getBaseline(habit), habitService.getCheckins(habit))).toList(),
            includeRoutines
                ? routineService.findAll(user).stream().map(routine -> new PersonalRecordCalculator.RoutineSource(routine, routineService.getCheckinEntities(routine))).toList()
                : List.of(),
            user.getLastCompletedDashboardDate() == null
                ? List.of()
                : dailyStatusRepository.findByUserAndStatusDateBetweenOrderByStatusDateAsc(user, java.time.LocalDate.of(1970, 1, 1), user.getLastCompletedDashboardDate())
        ), overrides(user));
    }

    private PersonalRecordCalculator.Calculation calculateRoutines(User user) {
        return calculator.calculateRoutines(
            routineService.findAll(user).stream().map(routine -> new PersonalRecordCalculator.RoutineSource(routine, routineService.getCheckinEntities(routine))).toList(),
            overrides(user)
        );
    }

    private Map<PersonalRecordCatalogMetric, PersonalRecordMode> overrides(User user) {
        Map<PersonalRecordCatalogMetric, PersonalRecordMode> overrides = new EnumMap<>(PersonalRecordCatalogMetric.class);
        settingRepository.findByUser(user).forEach(setting -> overrides.put(setting.getMetric(), setting.getMode()));
        return overrides;
    }

    private PersonalRecordSetting setting(User user, SettingOverrideRequest override) {
        PersonalRecordSetting setting = new PersonalRecordSetting();
        setting.setUser(user);
        setting.setMetric(override.metric());
        setting.setMode(override.mode());
        return setting;
    }

    private boolean isNewRecord(CurrentRecord record, BigDecimal previous) {
        if (previous == null) {
            return true;
        }
        int comparison = record.value().compareTo(previous);
        return record.series().metric().getDirection() == PersonalRecordDirection.MINIMUM ? comparison < 0 : comparison > 0;
    }

    private PersonalRecordSnapshot toSnapshot(User user, CurrentRecord record) {
        PersonalRecordSnapshot snapshot = new PersonalRecordSnapshot();
        snapshot.setUser(user);
        snapshot.setSeriesKey(record.series().key());
        snapshot.setDomain(record.series().metric().getDomain());
        snapshot.setMetric(record.series().metric());
        snapshot.setDirection(record.series().metric().getDirection());
        snapshot.setExercise(record.series().exercise());
        if (record.series().behaviorSubject() != null) {
            snapshot.setSubjectType(record.series().behaviorSubject().type());
            snapshot.setSubjectId(record.series().behaviorSubject().id());
            snapshot.setSubjectLabel(record.series().behaviorSubject().label());
        }
        snapshot.setLoadKg(record.series().loadKg());
        snapshot.setValue(record.value());
        snapshot.setRecordDate(record.date());
        snapshot.setSourceType(record.source().type());
        snapshot.setSourceId(record.source().id());
        snapshot.setLinePosition(record.source().linePosition());
        snapshot.setSegmentPosition(record.source().segmentPosition());
        return snapshot;
    }

    private CurrentRecordResponse toCurrentResponse(PersonalRecordSnapshot snapshot) {
        PersonalRecordMetric metric = snapshot.getMetric();
        return new CurrentRecordResponse(
            metric,
            metric.getLabel(),
            snapshot.getDomain(),
            snapshot.getDirection(),
            snapshot.getValue(),
            metric.getUnit(),
            snapshot.getRecordDate(),
            subject(metric, snapshot.getExercise(), snapshot.getSubjectType(), snapshot.getSubjectId(), snapshot.getSubjectLabel()),
            qualifier(snapshot.getLoadKg()),
            source(snapshot.getSourceType(), snapshot.getSourceId(), snapshot.getLinePosition(), snapshot.getSegmentPosition())
        );
    }

    private HistoryEventResponse toHistoryResponse(HistoryEvent event) {
        PersonalRecordMetric metric = event.series().metric();
        return new HistoryEventResponse(
            eventKey(event.series(), event.date(), event.value(), event.source()),
            metric,
            metric.getLabel(),
            metric.getDomain(),
            metric.getDirection(),
            event.kind(),
            event.value(),
            event.previousValue(),
            metric.getUnit(),
            event.date(),
            event.currentRecord(),
            subject(metric, event.series().exercise(), event.series().behaviorSubject()),
            qualifier(event.series().loadKg()),
            source(event.source())
        );
    }

    private HistoryEventResponse toHistoryResponse(PersonalRecordEvent event) {
        PersonalRecordMetric metric = event.getMetric();
        return new HistoryEventResponse(
            event.getEventKey(), metric, metric.getLabel(), event.getDomain(), event.getDirection(), event.getKind(), event.getValue(), event.getPreviousValue(),
            metric.getUnit(), event.getRecordDate(), event.isCurrentRecord(),
            subject(metric, event.getExercise(), event.getSubjectType(), event.getSubjectId(), event.getSubjectLabel()), qualifier(event.getLoadKg()),
            source(event.getSourceType(), event.getSourceId(), event.getLinePosition(), event.getSegmentPosition())
        );
    }

    private PersonalRecordEvent toEvent(User user, HistoryEvent event) {
        PersonalRecordEvent persisted = new PersonalRecordEvent();
        persisted.setUser(user);
        persisted.setEventKey(eventKey(event.series(), event.date(), event.value(), event.source()));
        persisted.setDomain(event.series().metric().getDomain());
        persisted.setMetric(event.series().metric());
        persisted.setDirection(event.series().metric().getDirection());
        persisted.setKind(event.kind());
        persisted.setValue(event.value());
        persisted.setPreviousValue(event.previousValue());
        persisted.setRecordDate(event.date());
        persisted.setCurrentRecord(event.currentRecord());
        persisted.setExercise(event.series().exercise());
        if (event.series().behaviorSubject() != null) {
            persisted.setSubjectType(event.series().behaviorSubject().type());
            persisted.setSubjectId(event.series().behaviorSubject().id());
            persisted.setSubjectLabel(event.series().behaviorSubject().label());
        }
        persisted.setLoadKg(event.series().loadKg());
        persisted.setSourceType(event.source().type());
        persisted.setSourceId(event.source().id());
        persisted.setLinePosition(event.source().linePosition());
        persisted.setSegmentPosition(event.source().segmentPosition());
        return persisted;
    }

    private RecordAchievementResponse toAchievement(CurrentRecord record, BigDecimal previousValue) {
        PersonalRecordMetric metric = record.series().metric();
        return new RecordAchievementResponse(
            eventKey(record.series(), record.date(), record.value(), record.source()),
            metric,
            metric.getLabel(),
            metric.getDomain(),
            metric.getDirection(),
            previousValue == null ? PersonalRecordEventKind.FIRST : PersonalRecordEventKind.IMPROVED,
            record.value(),
            previousValue,
            metric.getUnit(),
            record.date(),
            subject(metric, record.series().exercise(), record.series().behaviorSubject()),
            qualifier(record.series().loadKg()),
            source(record.source())
        );
    }

    private PersonalRecordSubjectResponse subject(PersonalRecordMetric metric, Exercise exercise) {
        return exercise == null
            ? new PersonalRecordSubjectResponse(metric.getDomain().name(), null, metric.getSubjectLabel())
            : new PersonalRecordSubjectResponse("EXERCISE", exercise.getId(), exercise.getName());
    }

    private PersonalRecordSubjectResponse subject(PersonalRecordMetric metric, Exercise exercise, PersonalRecordCalculator.BehaviorSubject behaviorSubject) {
        return behaviorSubject == null
            ? subject(metric, exercise)
            : new PersonalRecordSubjectResponse(behaviorSubject.type(), behaviorSubject.id(), behaviorSubject.label());
    }

    private PersonalRecordSubjectResponse subject(PersonalRecordMetric metric, Exercise exercise, String subjectType, Long subjectId, String subjectLabel) {
        return subjectType == null ? subject(metric, exercise) : new PersonalRecordSubjectResponse(subjectType, subjectId, subjectLabel);
    }

    private PersonalRecordQualifierResponse qualifier(BigDecimal loadKg) {
        if (loadKg == null) {
            return null;
        }
        String label = loadKg.signum() == 0 ? "No added load" : loadKg.stripTrailingZeros().toPlainString() + " kg";
        return new PersonalRecordQualifierResponse(loadKg, label);
    }

    private PersonalRecordSourceResponse source(Source source) {
        return source(source.type(), source.id(), source.linePosition(), source.segmentPosition());
    }

    private PersonalRecordSourceResponse source(PersonalRecordSourceType type, Long id, Integer linePosition, Integer segmentPosition) {
        return new PersonalRecordSourceResponse(type, id, linePosition, segmentPosition);
    }

    private Comparator<PersonalRecordSnapshot> snapshotComparator() {
        return Comparator.comparing(PersonalRecordSnapshot::getDomain)
            .thenComparing(snapshot -> snapshot.getExercise() == null ? "" : snapshot.getExercise().getName())
            .thenComparing(snapshot -> snapshot.getSubjectLabel() == null ? "" : snapshot.getSubjectLabel())
            .thenComparing(PersonalRecordSnapshot::getMetric)
            .thenComparing(PersonalRecordSnapshot::getLoadKg, Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    private Comparator<CurrentRecordResponse> currentResponseComparator() {
        return Comparator.comparing(CurrentRecordResponse::domain)
            .thenComparing(record -> record.subject().label())
            .thenComparing(CurrentRecordResponse::metric)
            .thenComparing(record -> record.qualifier() == null ? null : record.qualifier().loadKg(), Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    private Comparator<PersonalRecordEvent> eventComparator() {
        return Comparator.comparing(PersonalRecordEvent::getRecordDate, Comparator.reverseOrder())
            .thenComparing(PersonalRecordEvent::getSourceType)
            .thenComparing(PersonalRecordEvent::getSourceId, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(PersonalRecordEvent::getLinePosition, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(PersonalRecordEvent::getSegmentPosition, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(PersonalRecordEvent::getMetric);
    }

    private void lockUser(User user) {
        userRepository.findByIdForUpdate(user.getId()).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private String eventKey(Series series, java.time.LocalDate date, BigDecimal value, Source source) {
        String sourceValue = source.type() + ":" + source.id() + ":" + source.linePosition() + ":" + source.segmentPosition();
        String valueToHash = series.key() + "|" + date + "|" + value.stripTrailingZeros().toPlainString() + "|" + sourceValue;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valueToHash.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record CoachRecordAvailability(long recordCount, java.time.LocalDate firstDate, java.time.LocalDate lastDate) {
    }
}
