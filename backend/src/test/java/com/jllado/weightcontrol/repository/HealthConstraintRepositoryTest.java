package com.jllado.weightcontrol.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jllado.weightcontrol.domain.HealthConstraint;
import com.jllado.weightcontrol.domain.HealthConstraintSource;
import com.jllado.weightcontrol.domain.HealthConstraintType;
import com.jllado.weightcontrol.domain.User;
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
class HealthConstraintRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HealthConstraintRepository repository;

    @Test
    void activeOverlapIncludesInclusiveBoundariesAndExcludesInactiveOrDisjointConstraints() {
        User user = new User();
        user.setEmail("owner@example.com");
        entityManager.persist(user);

        persist(user, "Ongoing", LocalDate.of(2026, 8, 1), null, true);
        persist(user, "Ends on start", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), true);
        persist(user, "Starts on end", LocalDate.of(2026, 8, 20), null, true);
        persist(user, "Inactive", LocalDate.of(2026, 8, 1), null, false);
        persist(user, "Expired", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 9), true);
        persist(user, "Future", LocalDate.of(2026, 8, 21), null, true);
        entityManager.flush();
        entityManager.clear();

        List<String> titles = repository.findActiveOverlapping(
            user,
            LocalDate.of(2026, 8, 10),
            LocalDate.of(2026, 8, 20)
        ).stream().map(HealthConstraint::getTitle).toList();

        assertEquals(List.of("Ongoing", "Ends on start", "Starts on end"), titles);
    }

    private void persist(User user, String title, LocalDate startDate, LocalDate endDate, boolean active) {
        HealthConstraint constraint = new HealthConstraint();
        constraint.setUser(user);
        constraint.setType(HealthConstraintType.OTHER);
        constraint.setTitle(title);
        constraint.setDetails(title);
        constraint.setSource(HealthConstraintSource.SELF_REPORTED);
        constraint.setStartDate(startDate);
        constraint.setEndDate(endDate);
        constraint.setActive(active);
        entityManager.persist(constraint);
    }
}
