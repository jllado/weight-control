package com.jllado.weightcontrol.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jllado.weightcontrol.domain.CoachingPlan;
import com.jllado.weightcontrol.domain.User;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CoachingPlanRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CoachingPlanRepository repository;

    @Test
    void listsRoundTripInOrderAndReadsStayScopedToTheOwner() {
        User owner = persistUser("owner@example.com");
        User other = persistUser("other@example.com");
        persistPlan(owner, "Owner plan");
        entityManager.flush();
        entityManager.clear();

        CoachingPlan stored = repository.findByUser(owner).orElseThrow();

        assertEquals(List.of("Safety", "Consistency"), stored.getPrinciples());
        assertEquals(List.of("Recovery", "Training"), stored.getPriorities());
        assertEquals(List.of("Walk daily", "Train three times"), stored.getActions());
        assertTrue(repository.findByUser(other).isEmpty());
    }

    @Test
    void databaseRejectsASecondPlanForTheSameUser() {
        User owner = persistUser("owner@example.com");
        persistPlan(owner, "First plan");
        entityManager.flush();

        assertThrows(PersistenceException.class, () -> {
            persistPlan(owner, "Second plan");
            entityManager.flush();
        });
    }

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        return entityManager.persist(user);
    }

    private void persistPlan(User user, String goal) {
        CoachingPlan plan = new CoachingPlan();
        plan.setUser(user);
        plan.setGoal(goal);
        plan.setPrinciples(List.of("Safety", "Consistency"));
        plan.setPriorities(List.of("Recovery", "Training"));
        plan.setActions(List.of("Walk daily", "Train three times"));
        plan.setStartDate(LocalDate.of(2026, 8, 10));
        entityManager.persist(plan);
    }
}
