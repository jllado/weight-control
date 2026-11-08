package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.ExerciseType;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineCadence;
import com.jllado.weightcontrol.domain.RoutineAutomaticEvidence;
import com.jllado.weightcontrol.domain.RoutineCheckin;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.FastingPeriodRepository;
import com.jllado.weightcontrol.repository.MealRepository;
import com.jllado.weightcontrol.repository.RoutineAutomaticEvidenceRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.repository.SleepRepository;
import com.jllado.weightcontrol.repository.WeightRepository;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import org.springframework.beans.factory.annotation.Autowired;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class RoutineAutomationService {

    private static final String MEAL = "MEAL";
    private static final String WORKOUT = "WORKOUT";
    private static final String FAST = "FAST";
    private static final String SLEEP = "SLEEP";

    private final RoutineRepository routineRepository;
    private final RoutineAutomaticEvidenceRepository evidenceRepository;
    private final RoutineCheckinRepository checkinRepository;
    private final MealRepository mealRepository;
    private final WorkoutRepository workoutRepository;
    private final FastingPeriodRepository fastingPeriodRepository;
    private final SleepRepository sleepRepository;

    private final WeightRepository weightRepository;
    private final BloodPressureRepository bloodPressureRepository;

    @Autowired
    public RoutineAutomationService(
        RoutineRepository routineRepository,
        RoutineAutomaticEvidenceRepository evidenceRepository,
        RoutineCheckinRepository checkinRepository,
        MealRepository mealRepository,
        WorkoutRepository workoutRepository,
        FastingPeriodRepository fastingPeriodRepository,
        SleepRepository sleepRepository,
        WeightRepository weightRepository,
        BloodPressureRepository bloodPressureRepository
    ) {
        this.routineRepository = routineRepository;
        this.evidenceRepository = evidenceRepository;
        this.checkinRepository = checkinRepository;
        this.mealRepository = mealRepository;
        this.workoutRepository = workoutRepository;
        this.fastingPeriodRepository = fastingPeriodRepository;
        this.sleepRepository = sleepRepository;
        this.weightRepository = weightRepository;
        this.bloodPressureRepository = bloodPressureRepository;
    }

    public RoutineAutomationService(RoutineRepository routines, RoutineAutomaticEvidenceRepository evidence, RoutineCheckinRepository checkins,
        MealRepository meals, WorkoutRepository workouts, FastingPeriodRepository fasts, SleepRepository sleeps) {
        this(routines, evidence, checkins, meals, workouts, fasts, sleeps, null, null);
    }

    public boolean hasEvidence(Routine routine, LocalDate date) {
        return evidenceRepository.findByRoutine(routine).stream().anyMatch(evidence -> evidence.getEventDate().equals(date));
    }

    public void reconcile(User user) {
        List<Routine> routines = routineRepository.findByUserOrderByStartDateAsc(user).stream()
            .filter(Routine::isAutomaticCompletionEnabled)
            .toList();
        if (routines.isEmpty()) return;

        var meals = mealRepository.findByUserOrderByMealDateDescIdAsc(user);
        var workouts = workoutRepository.findByUserOrderByWorkoutDateDesc(user);
        var fasts = CompletedFastingPeriods.merge(fastingPeriodRepository.findByUserOrderByStartTimeDescIdDesc(user));
        var sleeps = sleepRepository.findByUserOrderBySleepDateDesc(user).stream()
            .filter(sleep -> sleep.getBedtimeStart() != null && sleep.getBedtimeEnd() != null)
            .toList();
        for (Routine routine : routines) {
            Map<Source, LocalDate> expected = new HashMap<>();
            Map<LocalDate, OffsetDateTime> measurementTimes = new HashMap<>();
            LocalDate startDate = DateTimes.toLocalDate(routine.getStartDate());
            switch (routine.getAutomaticTrigger()) {
                case NONE -> { }
                case WEIGHT_MEASUREMENT -> {
                    var readings = weightRepository.findByUserOrderByMeasuredAtDesc(user);
                    backdateMeasurementStart(routine, readings.stream().map(weight -> DateTimes.toLocalDate(weight.getMeasuredAt())).toList());
                    readings.forEach(weight -> {
                        LocalDate date = DateTimes.toLocalDate(weight.getMeasuredAt());
                        expected.put(new Source("WEIGHT", weight.getId().toString()), date);
                        measurementTimes.merge(date, weight.getMeasuredAt(), (first, next) -> first.isBefore(next) ? first : next);
                    });
                }
                case BLOOD_PRESSURE_MEASUREMENT -> {
                    var readings = bloodPressureRepository.findByUserOrderByMeasuredAtDesc(user);
                    backdateMeasurementStart(routine, readings.stream().map(pressure -> DateTimes.toLocalDate(pressure.getMeasuredAt())).toList());
                    readings.forEach(pressure -> {
                        LocalDate date = DateTimes.toLocalDate(pressure.getMeasuredAt());
                        expected.put(new Source("BLOOD_PRESSURE", pressure.getId().toString()), date);
                        measurementTimes.merge(date, pressure.getMeasuredAt(), (first, next) -> first.isBefore(next) ? first : next);
                    });
                }
                case FRUIT_MEAL -> meals.stream()
                    .filter(meal -> meal.getDishes().stream().anyMatch(dish -> dish.isFruit()))
                    .filter(meal -> !meal.getMealDate().isBefore(startDate))
                    .forEach(meal -> expected.put(new Source(MEAL, meal.getId().toString()), meal.getMealDate()));
                case CARDIO_WORKOUT -> workouts.stream()
                    .filter(workout -> workout.getLines().stream().anyMatch(line -> line.getExercise().getTrackingMode() == ExerciseTrackingMode.CARDIO))
                    .filter(workout -> !workout.getWorkoutDate().isBefore(startDate))
                    .forEach(workout -> expected.put(new Source(WORKOUT, workout.getId().toString()), workout.getWorkoutDate()));
                case STRENGTH_WORKOUT -> workouts.stream()
                    .filter(workout -> workout.getLines().stream().anyMatch(line -> line.getExercise().getExerciseType() == ExerciseType.TRAINING
                        && line.getExercise().getTrackingMode() != ExerciseTrackingMode.CARDIO))
                    .filter(workout -> !workout.getWorkoutDate().isBefore(startDate))
                    .forEach(workout -> expected.put(new Source(WORKOUT, workout.getId().toString()), workout.getWorkoutDate()));
                case STRETCHING_WORKOUT -> workouts.stream()
                    .filter(workout -> workout.getLines().stream().anyMatch(line -> line.getExercise().getExerciseType() == ExerciseType.STRETCHING))
                    .filter(workout -> !workout.getWorkoutDate().isBefore(startDate))
                    .forEach(workout -> expected.put(new Source(WORKOUT, workout.getId().toString()), workout.getWorkoutDate()));
                case MCGILL_BIG_THREE -> workouts.stream()
                    .filter(workout -> workout.getLines().stream().anyMatch(line -> "mcgill-big-three".equals(line.getExercise().getBuiltInImageKey())))
                    .filter(workout -> !workout.getWorkoutDate().isBefore(startDate))
                    .forEach(workout -> expected.put(new Source(WORKOUT, workout.getId().toString()), workout.getWorkoutDate()));
                case FAST_OVER_12_HOURS -> fasts.stream()
                    .filter(fast -> Duration.between(fast.startTime(), fast.endTime()).compareTo(Duration.ofHours(12)) > 0)
                    .filter(fast -> !DateTimes.toLocalDate(fast.endTime()).isBefore(startDate))
                    .forEach(fast -> expected.put(new Source(FAST, fast.startTime().toInstant().toString()), DateTimes.toLocalDate(fast.endTime())));
                case SLEEP_BED_AT_LEAST_7_HOURS -> sleeps.stream()
                    .filter(sleep -> Duration.between(sleep.getBedtimeStart(), sleep.getBedtimeEnd()).compareTo(Duration.ofHours(7)) >= 0)
                    .filter(sleep -> !sleep.getSleepDate().isBefore(startDate))
                    .forEach(sleep -> expected.put(new Source(SLEEP, sleep.getId().toString()), sleep.getSleepDate()));
                case WAKE_BEFORE_7 -> sleeps.stream()
                    .filter(sleep -> sleep.getBedtimeEnd().atZoneSameInstant(DateTimes.USER_ZONE).toLocalTime().isBefore(LocalTime.of(7, 0)))
                    .filter(sleep -> !sleep.getSleepDate().isBefore(startDate))
                    .forEach(sleep -> expected.put(new Source(SLEEP, sleep.getId().toString()), sleep.getSleepDate()));
                case KIWI_MEAL -> meals.stream()
                    .filter(meal -> meal.getDishes().stream().anyMatch(food -> food.getName().trim().equalsIgnoreCase("Kiwi")))
                    .filter(meal -> !meal.getMealDate().isBefore(startDate))
                    .forEach(meal -> expected.put(new Source(MEAL, meal.getId().toString()), meal.getMealDate()));
                case NIGHT_STRETCHING_WORKOUT -> workouts.stream()
                    .filter(workout -> workout.getStartTime() != null && workout.getStartTime().isAfter(LocalTime.of(19, 0)))
                    .filter(workout -> workout.getLines().stream().anyMatch(line -> line.getExercise().getExerciseType() == ExerciseType.STRETCHING))
                    .filter(workout -> !workout.getWorkoutDate().isBefore(startDate))
                    .forEach(workout -> expected.put(new Source(WORKOUT, workout.getId().toString()), workout.getWorkoutDate()));
                case OUTDOOR_WALK_OVER_40_MINUTES -> workouts.stream()
                    .filter(workout -> !workout.getWorkoutDate().isBefore(startDate))
                    .filter(workout -> workout.getLines().stream()
                        .filter(line -> "outdoor-walking".equals(line.getExercise().getBuiltInImageKey()))
                        .flatMap(line -> line.getSegments().stream())
                        .filter(segment -> !segment.isSkipped())
                        .mapToLong(segment -> segment.getDurationSeconds()).sum() > 2400)
                    .forEach(workout -> expected.put(new Source(WORKOUT, workout.getId().toString()), workout.getWorkoutDate()));
                case SITTING_RISING_WORKOUT -> workouts.stream()
                    .filter(workout -> workout.getLines().stream().anyMatch(line -> line.getExercise().getName().trim().equalsIgnoreCase("Sitting-Rising")))
                    .filter(workout -> !workout.getWorkoutDate().isBefore(startDate))
                    .forEach(workout -> expected.put(new Source(WORKOUT, workout.getId().toString()), workout.getWorkoutDate()));
            }
            reconcile(routine, expected, measurementTimes);
        }
    }

    private void backdateMeasurementStart(Routine routine, List<LocalDate> dates) {
        if (routine.getCadence() != RoutineCadence.WEEKLY) return;
        dates.stream().min(LocalDate::compareTo).map(DateTimes::startOfDashboardWeek)
            .filter(start -> start.isBefore(DateTimes.toLocalDate(routine.getStartDate())))
            .ifPresent(start -> routine.setStartDate(DateTimes.startOfDay(start)));
    }

    private void reconcile(Routine routine, Map<Source, LocalDate> expected, Map<LocalDate, OffsetDateTime> measurementTimes) {
        Map<Source, RoutineAutomaticEvidence> existing = new HashMap<>();
        for (RoutineAutomaticEvidence evidence : evidenceRepository.findByRoutine(routine)) {
            existing.put(new Source(evidence.getSourceKind(), evidence.getSourceKey()), evidence);
        }
        for (var entry : existing.entrySet()) {
            if (!expected.containsKey(entry.getKey())) evidenceRepository.delete(entry.getValue());
        }
        for (var entry : expected.entrySet()) {
            RoutineAutomaticEvidence evidence = existing.get(entry.getKey());
            if (evidence == null) {
                evidence = new RoutineAutomaticEvidence();
                evidence.setRoutine(routine);
                evidence.setSourceKind(entry.getKey().kind());
                evidence.setSourceKey(entry.getKey().key());
            }
            evidence.setEventDate(entry.getValue());
            evidenceRepository.save(evidence);
        }

        Set<LocalDate> expectedDates = new HashSet<>(expected.values());
        Set<LocalDate> checkedDates = new HashSet<>();
        boolean checkinsChanged = false;
        for (RoutineCheckin checkin : checkinRepository.findByRoutineOrderByCheckedAtAsc(routine)) {
            LocalDate date = DateTimes.toLocalDate(checkin.getCheckedAt());
            if (!checkin.isManualCompletion() && !expectedDates.contains(date)) {
                checkinRepository.delete(checkin);
                checkinsChanged = true;
            } else {
                checkedDates.add(date);
            }
        }
        for (LocalDate date : expectedDates) {
            if (checkedDates.add(date)) {
                RoutineCheckin checkin = new RoutineCheckin();
                checkin.setRoutine(routine);
                checkin.setCheckedAt(measurementTimes.getOrDefault(date, DateTimes.startOfDay(date)));
                checkin.setManualCompletion(false);
                checkinRepository.save(checkin);
                clearReminderSnoozes(routine);
                checkinsChanged = true;
            }
        }
        if (checkinsChanged) {
            checkinRepository.flush();
            RoutineService.rebuildSummary(routine, checkinRepository.findByRoutineOrderByCheckedAtAsc(routine));
            routineRepository.save(routine);
        }
    }

    private record Source(String kind, String key) {
    }

    private void clearReminderSnoozes(Routine routine) {
        routine.getReminders().forEach(reminder -> reminder.setReminderSnoozedUntil(null));
    }
}
