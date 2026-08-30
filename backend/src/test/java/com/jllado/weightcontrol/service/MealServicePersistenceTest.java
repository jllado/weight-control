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

    @Test
    void replacesDishesAtExistingPositions() {
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        User user = new User();
        user.setEmail("meal-service@example.com");
        user = userRepository.save(user);

        var meal = mealService.create(user, request(date, "Old dish"));
        mealService.update(user, meal.getId(), request(date, "Replacement dish"));

        var stored = mealRepository.findById(meal.getId()).orElseThrow();
        assertEquals("Replacement dish", stored.getDishes().getFirst().getName());
        assertEquals(1, stored.getDishes().getFirst().getPosition());
    }

    private MealRequest request(LocalDate date, String dishName) {
        return new MealRequest(date, MealType.LUNCH, 0, null, null, null, null, null, List.of(
            new MealDishRequest(dishName, 500, null, null, null)
        ));
    }
}
