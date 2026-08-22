package com.jllado.weightcontrol.service;

import static com.jllado.weightcontrol.api.dto.PersonalRecordDtos.*;

import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.PersonalRecordSnapshotRepository;
import com.jllado.weightcontrol.repository.PersonalRecordSettingRepository;
import com.jllado.weightcontrol.service.PersonalRecordCalculator.CurrentRecord;
import com.jllado.weightcontrol.service.PersonalRecordCalculator.HistoryEvent;
import com.jllado.weightcontrol.service.PersonalRecordCalculator.Source;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class PersonalRecordService {

    private final PersonalRecordSnapshotRepository repository;
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
    private final DecisionOutcomeService decisionOutcomeService;

    public PersonalRecordService(
        PersonalRecordSnapshotRepository repository,
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
        DecisionOutcomeService decisionOutcomeService
    ) {
        this.repository = repository;
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
        this.decisionOutcomeService = decisionOutcomeService;
    }

    public List<CurrentRecordResponse> current(User user, PersonalRecordDomain domain, PersonalRecordMetric metric, Long exerciseId) {
        return repository.findByUser(user).stream()
            .filter(snapshot -> domain == null || snapshot.getDomain() == domain)
            .filter(snapshot -> metric == null || snapshot.getMetric() == metric)
            .filter(snapshot -> exerciseId == null || snapshot.getExercise() != null && snapshot.getExercise().getId().equals(exerciseId))
            .sorted(snapshotComparator())
            .map(this::toCurrentResponse)
            .toList();
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
        int page,
        int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("History page must be non-negative and size must be between 1 and 100");
        }
        List<HistoryEventResponse> filtered = calculate(user).history().stream()
            .filter(event -> domain == null || event.series().metric().getDomain() == domain)
            .filter(event -> metric == null || event.series().metric() == metric)
            .filter(event -> exerciseId == null || event.series().exercise() != null && event.series().exercise().getId().equals(exerciseId))
            .filter(event -> workoutIds.isEmpty() || event.source().type() == PersonalRecordSourceType.WORKOUT && workoutIds.contains(event.source().id()))
            .map(this::toHistoryResponse)
            .toList();
        int from = Math.min(page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (filtered.size() + size - 1) / size;
        return new HistoryPageResponse(filtered.subList(from, to), page, size, filtered.size(), totalPages);
    }

    public Map<String, BigDecimal> captureCurrentValues(User user) {
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
            .filter(record -> record.source().type() == sourceType && Objects.equals(record.source().id(), sourceId)
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

    private List<CurrentRecord> rebuildRecords(User user) {
        List<CurrentRecord> current = calculate(user).current();
        repository.deleteByUser(user);
        repository.flush();
        repository.saveAll(current.stream().map(record -> toSnapshot(user, record)).toList());
        return current;
    }

    private PersonalRecordCalculator.Calculation calculate(User user) {
        return calculator.calculate(new PersonalRecordCalculator.Sources(
            weightService.findAll(user),
            workoutService.findAll(user),
            bloodPressureService.findAll(user),
            lipidPanelService.findAll(user),
            moodService.findAll(user),
            sleepService.findAll(user),
            mealService.findAll(user),
            habitService.findAll(user).stream().map(habit -> new PersonalRecordCalculator.HabitSource(habit, habitService.getBaseline(habit), habitService.getCheckins(habit))).toList(),
            routineService.findAll(user).stream().map(routine -> new PersonalRecordCalculator.RoutineSource(routine, routineService.getCheckinEntities(routine))).toList(),
            decisionOutcomeService.findAll(user)
        ), overrides(user));
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

    private RecordAchievementResponse toAchievement(CurrentRecord record, BigDecimal previousValue) {
        PersonalRecordMetric metric = record.series().metric();
        return new RecordAchievementResponse(
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
}
