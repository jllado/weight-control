package com.jllado.weightcontrol.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jllado.weightcontrol.domain.FastingPeriod;
import com.jllado.weightcontrol.domain.User;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class FastingPeriodRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FastingPeriodRepository repository;

    @Test
    void overlapUsesHalfOpenIntervalsAndStaysScopedToTheOwner() {
        User owner = persistUser("owner@example.com");
        User other = persistUser("other@example.com");
        OffsetDateTime start = OffsetDateTime.parse("2026-08-19T20:00:00+02:00");
        OffsetDateTime end = OffsetDateTime.parse("2026-08-20T12:00:00+02:00");
        persist(owner, start, end);
        entityManager.flush();

        assertTrue(repository.existsOverlapping(owner, start.plusHours(1), end.plusHours(1), null));
        assertFalse(repository.existsOverlapping(owner, end, end.plusHours(16), null));
        assertFalse(repository.existsOverlapping(other, start.plusHours(1), end.plusHours(1), null));
    }

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        return entityManager.persist(user);
    }

    private void persist(User user, OffsetDateTime start, OffsetDateTime end) {
        FastingPeriod period = new FastingPeriod();
        period.setUser(user);
        period.setStartTime(start);
        period.setEndTime(end);
        entityManager.persist(period);
    }
}
