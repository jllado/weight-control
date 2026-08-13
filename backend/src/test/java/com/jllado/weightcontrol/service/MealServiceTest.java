package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.MealDtos.MealRequest;
import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MealRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MealServiceTest {

    @Mock
    private MealRepository repository;

    @InjectMocks
    private MealService service;

    @Test
    void createStoresMealNutrition() {
        User user = user(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        MealRequest request = new MealRequest(
            date,
            MealType.LUNCH,
            925,
            new BigDecimal("42.50"),
            new BigDecimal("80.25"),
            new BigDecimal("20.00")
        );
        when(repository.findByUserAndMealDateAndMealTypeAndMealSequence(user, date, MealType.LUNCH, 1)).thenReturn(Optional.empty());

        service.create(user, request);

        ArgumentCaptor<Meal> meal = ArgumentCaptor.forClass(Meal.class);
        verify(repository).save(meal.capture());
        assertEquals(date, meal.getValue().getMealDate());
        assertEquals(MealType.LUNCH, meal.getValue().getMealType());
        assertEquals(1, meal.getValue().getMealSequence());
        assertEquals(925, meal.getValue().getCalories());
        assertEquals(new BigDecimal("42.50"), meal.getValue().getProteinGrams());
        assertEquals(new BigDecimal("80.25"), meal.getValue().getCarbohydrateGrams());
        assertEquals(new BigDecimal("20.00"), meal.getValue().getFatGrams());
    }

    @Test
    void createRejectsDuplicateStandardMeal() {
        User user = user(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        when(repository.findByUserAndMealDateAndMealTypeAndMealSequence(user, date, MealType.DINNER, 1))
            .thenReturn(Optional.of(meal(10L, user, date, MealType.DINNER, 1)));

        assertThrows(BadRequestException.class, () -> service.create(user, request(date, MealType.DINNER, 1000)));
    }

    @Test
    void createAssignsLowestAvailableSnackSequence() {
        User user = user(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        when(repository.findByUserAndMealDateAndMealTypeOrderByMealSequenceAsc(user, date, MealType.SNACK)).thenReturn(List.of(
            meal(10L, user, date, MealType.SNACK, 1),
            meal(12L, user, date, MealType.SNACK, 3)
        ));

        service.create(user, request(date, MealType.SNACK, 200));

        ArgumentCaptor<Meal> meal = ArgumentCaptor.forClass(Meal.class);
        verify(repository).save(meal.capture());
        assertEquals(2, meal.getValue().getMealSequence());
    }

    @Test
    void updateKeepsSnackSequenceWhenIdentityDoesNotChange() {
        User user = user(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        Meal snack = meal(10L, user, date, MealType.SNACK, 2);
        when(repository.findById(10L)).thenReturn(Optional.of(snack));

        service.update(user, 10L, request(date, MealType.SNACK, 250));

        assertEquals(2, snack.getMealSequence());
        assertEquals(250, snack.getCalories());
    }

    @Test
    void updateRejectsOccupiedStandardMeal() {
        User user = user(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        Meal snack = meal(10L, user, date, MealType.SNACK, 1);
        when(repository.findById(10L)).thenReturn(Optional.of(snack));
        when(repository.findByUserAndMealDateAndMealTypeAndMealSequence(user, date, MealType.LUNCH, 1))
            .thenReturn(Optional.of(meal(11L, user, date, MealType.LUNCH, 1)));

        assertThrows(BadRequestException.class, () -> service.update(user, 10L, request(date, MealType.LUNCH, 900)));
    }

    @Test
    void createAllowsZeroCaloriesAndMissingMacros() {
        User user = user(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        when(repository.findByUserAndMealDateAndMealTypeAndMealSequence(user, date, MealType.BREAKFAST, 1)).thenReturn(Optional.empty());

        service.create(user, request(date, MealType.BREAKFAST, 0));

        ArgumentCaptor<Meal> meal = ArgumentCaptor.forClass(Meal.class);
        verify(repository).save(meal.capture());
        assertEquals(0, meal.getValue().getCalories());
        assertEquals(null, meal.getValue().getProteinGrams());
    }

    @Test
    void createRejectsFutureDate() {
        User user = user(1L);
        LocalDate tomorrow = LocalDate.now(DateTimes.USER_ZONE).plusDays(1);

        assertThrows(BadRequestException.class, () -> service.create(user, request(tomorrow, MealType.BREAKFAST, 500)));
    }

    @Test
    void deleteRejectsForeignMeal() {
        User user = user(1L);
        Meal foreignMeal = meal(10L, user(2L), LocalDate.now(DateTimes.USER_ZONE), MealType.LUNCH, 1);
        when(repository.findById(10L)).thenReturn(Optional.of(foreignMeal));

        assertThrows(NotFoundException.class, () -> service.delete(user, 10L));
    }

    @Test
    void findAllUsesMealDisplayOrder() {
        User user = user(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        Meal dinner = meal(1L, user, date, MealType.DINNER, 1);
        Meal breakfast = meal(2L, user, date, MealType.BREAKFAST, 1);
        Meal snack = meal(3L, user, date, MealType.SNACK, 1);
        Meal lunch = meal(4L, user, date, MealType.LUNCH, 1);
        when(repository.findByUserOrderByMealDateDescIdAsc(user)).thenReturn(List.of(dinner, breakfast, snack, lunch));

        assertEquals(List.of(breakfast, lunch, dinner, snack), service.findAll(user));
    }

    private MealRequest request(LocalDate date, MealType type, int calories) {
        return new MealRequest(date, type, calories, null, null, null);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Meal meal(Long id, User user, LocalDate date, MealType type, int sequence) {
        Meal meal = new Meal();
        meal.setId(id);
        meal.setUser(user);
        meal.setMealDate(date);
        meal.setMealType(type);
        meal.setMealSequence(sequence);
        return meal;
    }
}
