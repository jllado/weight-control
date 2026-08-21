package com.jllado.weightcontrol.service;

import static com.jllado.weightcontrol.api.dto.PersonalRecordDtos.*;

import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.PersonalRecordSnapshotRepository;
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
    private final PersonalRecordCalculator calculator;
    private final WeightService weightService;
    private final WorkoutService workoutService;

    public PersonalRecordService(
        PersonalRecordSnapshotRepository repository,
        PersonalRecordCalculator calculator,
        WeightService weightService,
        WorkoutService workoutService
    ) {
        this.repository = repository;
        this.calculator = calculator;
        this.weightService = weightService;
        this.workoutService = workoutService;
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
        List<CurrentRecord> current = rebuildRecords(user);
        if (!celebrate) {
            return List.of();
        }
        return current.stream()
            .filter(record -> record.source().type() == sourceType && record.source().id().equals(sourceId))
            .filter(record -> isNewRecord(record, previousValues.get(record.series().key())))
            .map(record -> toAchievement(record, previousValues.get(record.series().key())))
            .toList();
    }

    public void rebuild(User user) {
        rebuildRecords(user);
    }

    private List<CurrentRecord> rebuildRecords(User user) {
        List<CurrentRecord> current = calculate(user).current();
        repository.deleteByUser(user);
        repository.flush();
        repository.saveAll(current.stream().map(record -> toSnapshot(user, record)).toList());
        return current;
    }

    private PersonalRecordCalculator.Calculation calculate(User user) {
        return calculator.calculate(weightService.findAll(user), workoutService.findAll(user));
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
            subject(snapshot.getExercise()),
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
            subject(event.series().exercise()),
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
            subject(record.series().exercise()),
            qualifier(record.series().loadKg()),
            source(record.source())
        );
    }

    private PersonalRecordSubjectResponse subject(Exercise exercise) {
        return exercise == null
            ? new PersonalRecordSubjectResponse("BODY", null, "Body")
            : new PersonalRecordSubjectResponse("EXERCISE", exercise.getId(), exercise.getName());
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
            .thenComparing(PersonalRecordSnapshot::getMetric)
            .thenComparing(PersonalRecordSnapshot::getLoadKg, Comparator.nullsFirst(Comparator.naturalOrder()));
    }
}
