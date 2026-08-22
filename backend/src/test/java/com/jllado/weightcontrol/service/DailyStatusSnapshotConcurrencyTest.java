package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
    "app.auth.google-client-id=test-client-id",
    "app.chat-gpt-actions.public-base-url=https://test.example",
    "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"
})
class DailyStatusSnapshotConcurrencyTest {

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("weight_control");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoutineRepository routineRepository;

    @Autowired
    private RoutineCheckinRepository checkinRepository;

    @Autowired
    private RoutineService routineService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private DailyStatusSnapshotService snapshotService;

    @Test
    void concurrentRoutineCheckinsRebuildTheSharedDailyStatus() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 22);
        User user = userRepository.save(user(date));
        Routine first = routineRepository.save(routine(user, "First", date));
        Routine second = routineRepository.save(routine(user, "Second", date));
        snapshotService.rebuild(user, date);

        CyclicBarrier start = new CyclicBarrier(2);
        OffsetDateTime checkedAt = DateTimes.startOfDay(date).plusHours(12);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstCheckin = executor.submit(() -> checkin(start, user, first.getId(), checkedAt));
            var secondCheckin = executor.submit(() -> checkin(start, user, second.getId(), checkedAt));

            firstCheckin.get(10, TimeUnit.SECONDS);
            secondCheckin.get(10, TimeUnit.SECONDS);
        }

        var status = snapshotService.rebuild(user, date);

        assertEquals(2, checkinRepository.countByRoutineUser(user));
        assertEquals(2, status.getRoutinesDone());
    }

    private void checkin(CyclicBarrier start, User user, Long routineId, OffsetDateTime checkedAt) {
        try {
            start.await(10, TimeUnit.SECONDS);
            routineService.checkin(user, routineId, checkedAt);
            dashboardService.refreshCurrentStatus(user);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private User user(LocalDate date) {
        User user = new User();
        user.setEmail("concurrent-routines@example.com");
        user.setDashboardAnchorDate(date);
        return user;
    }

    private Routine routine(User user, String name, LocalDate date) {
        Routine routine = new Routine();
        routine.setUser(user);
        routine.setName(name);
        routine.setStartDate(DateTimes.startOfDay(date));
        routine.setCurrentStrike(0);
        routine.setBestStrike(0);
        return routine;
    }
}
