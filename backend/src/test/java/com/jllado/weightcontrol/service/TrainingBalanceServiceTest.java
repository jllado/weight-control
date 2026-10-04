package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrainingBalanceServiceTest {
    private final WorkoutRepository repository = mock(WorkoutRepository.class);
    private final TrainingBalanceService service = new TrainingBalanceService(repository);

    @Test void countsEverySavedStrengthSegmentWithZeroGroupsAndCurrentClassification() {
        var owner = new User();
        var date = LocalDate.of(2026, 10, 4);
        var press = exercise(ExerciseType.TRAINING, ExerciseTrackingMode.REPS, PrimaryMuscleGroup.CHEST);
        var hold = exercise(ExerciseType.TRAINING, ExerciseTrackingMode.SECONDS, PrimaryMuscleGroup.CORE);
        var sessions = List.of(session(line(press, 3), line(hold, 2)), session(line(press, 4)), session(
            line(exercise(ExerciseType.WARM_UP, ExerciseTrackingMode.REPS, null), 5),
            line(exercise(ExerciseType.STRETCHING, ExerciseTrackingMode.SECONDS, null), 6),
            line(exercise(ExerciseType.TRAINING, ExerciseTrackingMode.CARDIO, null), 7)), session());
        when(repository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(owner, date.minusDays(1), date.plusDays(5))).thenReturn(sessions);
        var result = service.week(owner, date);
        assertEquals(date.minusDays(1), result.weekStart()); assertEquals(date.plusDays(5), result.weekEnd());
        assertEquals(9, result.totalSets()); assertEquals(11, result.groups().size());
        assertEquals(List.of(PrimaryMuscleGroup.values()), result.groups().stream().map(group -> group.muscleGroup()).toList());
        assertEquals(7, result.groups().get(0).sets()); assertEquals(2, result.groups().get(6).sets());
        assertEquals(0, result.groups().get(5).sets());
        press.setPrimaryMuscleGroup(PrimaryMuscleGroup.TRICEPS);
        result = service.week(owner, date);
        assertEquals(0, result.groups().get(0).sets()); assertEquals(7, result.groups().get(4).sets()); assertEquals(9, result.totalSets());
        verify(repository, times(2)).findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(owner, date.minusDays(1), date.plusDays(5));
    }

    @Test void selectsInclusiveSaturdayFridayWeeksAcrossYearAndDaylightSavingBoundaries() {
        var owner = new User();
        for (var date : List.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10))) {
            var result = service.week(owner, date);
            assertEquals(java.time.DayOfWeek.SATURDAY, result.weekStart().getDayOfWeek());
            assertEquals(java.time.DayOfWeek.FRIDAY, result.weekEnd().getDayOfWeek());
            assertFalse(date.isBefore(result.weekStart())); assertFalse(date.isAfter(result.weekEnd()));
            assertEquals(0, result.totalSets()); assertTrue(result.groups().stream().allMatch(group -> group.sets() == 0));
            verify(repository).findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(owner, result.weekStart(), result.weekEnd());
        }
    }
    private Exercise exercise(ExerciseType type, ExerciseTrackingMode mode, PrimaryMuscleGroup group) { var exercise = new Exercise(); exercise.setExerciseType(type); exercise.setTrackingMode(mode); exercise.setPrimaryMuscleGroup(group); return exercise; }
    private WorkoutLine line(Exercise exercise, int sets) { var line = new WorkoutLine(); line.setExercise(exercise); for (int i = 0; i < sets; i++) { var segment = new WorkoutSegment(); segment.setWeight(java.math.BigDecimal.valueOf(i * 7.5)); segment.setRepetitions(exercise.getTrackingMode() == ExerciseTrackingMode.REPS ? 10 : null); segment.setDurationSeconds(exercise.getTrackingMode() == ExerciseTrackingMode.SECONDS ? 30 : null); line.getSegments().add(segment); } return line; }
    private Workout session(WorkoutLine... lines) { var workout = new Workout(); workout.getLines().addAll(List.of(lines)); return workout; }
}
