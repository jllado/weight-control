package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jllado.weightcontrol.api.dto.MealDtos.MealDishRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.MealRequest;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MealRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;
import java.util.List;
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
class MealServicePersistenceTest {

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("weight_control_meal_service");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired
    private MealService mealService;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FastingPeriodService fastingPeriodService;

    @Test
    void replacesDishesAtExistingPositions() {
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        User user = new User();
        user.setEmail("meal-service@example.com");
        user = userRepository.save(user);

        var meal = mealService.create(user, request(date, "Old dish"));
        mealService.update(user, meal.getId(), request(date, "Replacement dish"));

        var stored = mealRepository.findById(meal.getId()).orElseThrow();
        assertEquals(30, stored.getDurationMinutes());
        assertEquals("Replacement dish", stored.getDishes().getFirst().getName());
        assertEquals(1, stored.getDishes().getFirst().getPosition());
    }

    @Test
    void mealMutationsRecalculateCompletedFastsFromPersistedDuration() {
        LocalDate date = LocalDate.of(2026, 8, 10);
        User user = new User();
        user.setEmail("meal-duration@example.com");
        user = userRepository.save(user);
        var dinner = mealService.create(user, new MealRequest(date, MealType.DINNER, 500, null, null, null,
            java.time.LocalTime.of(20, 0), null, 30));
        mealService.create(user, new MealRequest(date.plusDays(1), MealType.BREAKFAST, 500, null, null, null,
            java.time.LocalTime.of(8, 0), null, 30));
        var completed = fastingPeriodService.findAll(user).stream().filter(period -> period.getEndTime() != null).findFirst().orElseThrow();
        assertEquals(java.time.Duration.ofMinutes(690), java.time.Duration.between(completed.getStartTime(), completed.getEndTime()));
        mealService.update(user, dinner.getId(), new MealRequest(date, MealType.DINNER, 500, null, null, null,
            java.time.LocalTime.of(20, 0), null, 60));
        completed = fastingPeriodService.findAll(user).stream().filter(period -> period.getEndTime() != null).findFirst().orElseThrow();
        assertEquals(java.time.Duration.ofHours(11), java.time.Duration.between(completed.getStartTime(), completed.getEndTime()));
        mealService.delete(user, dinner.getId());
        assertEquals(0, fastingPeriodService.findAll(user).stream().filter(period -> period.getEndTime() != null).count());
    }

    private MealRequest request(LocalDate date, String dishName) {
        return new MealRequest(date, MealType.LUNCH, 0, null, null, null, null, null, List.of(
            new MealDishRequest(dishName, 500, null, null, null)
        ), 30);
    }

    @Test
    void persistsQuantityReferenceAcrossEditsAndIndependentReuse() {
        var user = new User();
        user.setEmail("dish-quantities@example.com");
        user = userRepository.save(user);
        var date = LocalDate.of(2026, 8, 12);
        var reference = new com.jllado.weightcontrol.api.dto.MealDtos.DishReference(new java.math.BigDecimal("100"), 101, new java.math.BigDecimal("1.01"), null, null);
        var dish = new MealDishRequest("Rice", 51, new java.math.BigDecimal("0.51"), null, null, new java.math.BigDecimal("50"), com.jllado.weightcontrol.domain.DishUnit.GRAM, reference);
        var request = new MealRequest(date, MealType.SNACK, 51, null, null, null, null, null, List.of(dish), null);
        var first = mealService.create(user, request);
        var copy = mealService.create(user, request);
        var stored = mealRepository.findById(first.getId()).orElseThrow().getDishes().getFirst();
        var restored = new MealDishRequest("Rice", 101, new java.math.BigDecimal("1.01"), null, null, new java.math.BigDecimal("100"), stored.getUnit(), com.jllado.weightcontrol.api.dto.MealDtos.DishReference.from(stored));
        mealService.update(user, first.getId(), new MealRequest(date, MealType.SNACK, 101, null, null, null, null, null, List.of(restored), null));
        assertEquals(101, mealRepository.findById(first.getId()).orElseThrow().getCalories());
        assertEquals(51, mealRepository.findById(copy.getId()).orElseThrow().getCalories());
    }
}
