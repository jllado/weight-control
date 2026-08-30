package com.jllado.weightcontrol.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.WorkoutAssessment;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class WorkoutAssessmentRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private WorkoutAssessmentRepository assessmentRepository;

    @Autowired
    private WorkoutRepository workoutRepository;

    @Test
    void databaseAllowsOnlyOneAssessmentPerWorkout() {
        Workout workout = persistWorkout("owner@example.com");
        assessmentRepository.save(assessment(workout, 7));
        entityManager.flush();

        assertThrows(DataIntegrityViolationException.class, () -> {
            assessmentRepository.save(assessment(workout, 8));
            entityManager.flush();
        });
    }

    @Test
    void deletingWorkoutCascadesToItsAssessment() {
        Workout workout = persistWorkout("owner@example.com");
        assessmentRepository.save(assessment(workout, 7));
        entityManager.flush();
        Long workoutId = workout.getId();
        entityManager.clear();

        workoutRepository.deleteById(workoutId);
        entityManager.flush();
        entityManager.clear();

        assertEquals(0, assessmentRepository.countByWorkoutId(workoutId));
    }

    private Workout persistWorkout(String email) {
        User user = new User();
        user.setEmail(email);
        entityManager.persist(user);
        Workout workout = new Workout();
        workout.setUser(user);
        workout.setWorkoutDate(LocalDate.of(2026, 8, 20));
        return entityManager.persist(workout);
    }

    private WorkoutAssessment assessment(Workout workout, int score) {
        Instant timestamp = Instant.parse("2026-08-20T18:00:00Z");
        WorkoutAssessment assessment = new WorkoutAssessment();
        assessment.setWorkout(workout);
        assessment.setGoalAlignmentScore(score);
        assessment.setEstimatedTrainingDemandScore(6);
        assessment.setRationale("Clear alignment with the active goal.");
        assessment.setStrength("Consistent compound work.");
        assessment.setImprovement("Add one pulling set.");
        assessment.setNextWorkoutAction("Repeat with controlled progression.");
        assessment.setGoalSnapshot("Improve upper-body strength");
        assessment.setPlanUpdatedAt(timestamp);
        return assessment;
    }
}
