package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.MealDtos.CoachMealRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.MealRequest;
import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealDish;
import com.jllado.weightcontrol.domain.MealSource;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MealRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class MealService {

    private static final Comparator<Meal> DISPLAY_ORDER = Comparator.comparing(Meal::getMealDate).reversed()
        .thenComparingInt(meal -> meal.getMealType().getOrder())
        .thenComparingInt(Meal::getMealSequence);

    private final MealRepository repository;
    private final FastingPeriodService fastingPeriodService;

    public MealService(MealRepository repository, FastingPeriodService fastingPeriodService) {
        this.repository = repository;
        this.fastingPeriodService = fastingPeriodService;
    }

    public List<Meal> findAll(User user) {
        return repository.findByUserOrderByMealDateDescIdAsc(user).stream().sorted(DISPLAY_ORDER).toList();
    }

    public List<Meal> findBetween(User user, LocalDate from, LocalDate to) {
        return repository.findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(user, from, to);
    }

    public long count(User user) {
        return repository.countByUser(user);
    }

    public java.util.Optional<LocalDate> findFirstRecordedDate(User user) {
        return repository.findFirstByUserOrderByMealDateAscIdAsc(user).map(Meal::getMealDate);
    }

    public java.util.Optional<LocalDate> findLastRecordedDate(User user) {
        return repository.findFirstByUserOrderByMealDateDescIdDesc(user).map(Meal::getMealDate);
    }

    public Meal create(User user, MealRequest request) {
        return create(user, request, MealSource.MANUAL);
    }

    private Meal create(User user, MealRequest request, MealSource source) {
        validateDate(request.date());
        Meal meal = new Meal();
        meal.setUser(user);
        meal.setSource(source);
        applyIdentity(meal, user, request.date(), request.mealType());
        apply(meal, request);
        Meal saved = repository.save(meal);
        fastingPeriodService.recalculateAutomaticPeriods(user);
        return saved;
    }

    public Meal update(User user, Long id, MealRequest request) {
        validateDate(request.date());
        Meal meal = requireOwned(user, id);
        if (!meal.getMealDate().equals(request.date()) || meal.getMealType() != request.mealType()) {
            applyIdentity(meal, user, request.date(), request.mealType());
        }
        meal.getDishes().clear();
        repository.flush();
        apply(meal, request);
        Meal saved = repository.save(meal);
        fastingPeriodService.recalculateAutomaticPeriods(user);
        return saved;
    }

    public Meal createConfirmed(User user, CoachMealRequest request) {
        requireConfirmation(request.confirmed());
        return create(user, request.meal(), request.source());
    }

    public Meal updateConfirmed(User user, Long id, CoachMealRequest request) {
        requireConfirmation(request.confirmed());
        validateDate(request.date());
        Meal meal = requireOwned(user, id);
        if (!meal.getMealDate().equals(request.date()) || meal.getMealType() != request.mealType()) {
            applyIdentity(meal, user, request.date(), request.mealType());
        }
        meal.getDishes().clear();
        repository.flush();
        apply(meal, request.meal());
        meal.setSource(request.source());
        Meal saved = repository.save(meal);
        fastingPeriodService.recalculateAutomaticPeriods(user);
        return saved;
    }

    public void deleteConfirmed(User user, Long id, boolean confirmed) {
        requireConfirmation(confirmed);
        delete(user, id);
    }

    public void delete(User user, Long id) {
        repository.delete(requireOwned(user, id));
        fastingPeriodService.recalculateAutomaticPeriods(user);
    }

    private Meal requireOwned(User user, Long id) {
        Meal meal = repository.findById(id).orElseThrow(() -> new NotFoundException("Meal not found"));
        if (!meal.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Meal not found");
        }
        return meal;
    }

    private void applyIdentity(Meal meal, User user, LocalDate date, MealType type) {
        meal.setMealDate(date);
        meal.setMealType(type);
        meal.setMealSequence(type == MealType.SNACK ? nextSnackSequence(user, date) : 1);
        if (type != MealType.SNACK) {
            repository.findByUserAndMealDateAndMealTypeAndMealSequence(user, date, type, 1)
                .filter(existing -> !existing.getId().equals(meal.getId()))
                .ifPresent(existing -> {
                    throw new BadRequestException("Meal already exists for this date");
                });
        }
    }

    private int nextSnackSequence(User user, LocalDate date) {
        int sequence = 1;
        for (Meal snack : repository.findByUserAndMealDateAndMealTypeOrderByMealSequenceAsc(user, date, MealType.SNACK)) {
            if (snack.getMealSequence() == sequence) {
                sequence++;
            }
        }
        return sequence;
    }

    private void apply(Meal meal, MealRequest request) {
        if (request.dishes().isEmpty()) {
            meal.setCalories(request.calories());
            meal.setProteinGrams(request.proteinGrams());
            meal.setCarbohydrateGrams(request.carbohydrateGrams());
            meal.setFatGrams(request.fatGrams());
        } else {
            meal.getDishes().clear();
            for (int index = 0; index < request.dishes().size(); index++) {
                var requestDish = request.dishes().get(index);
                MealDish dish = new MealDish();
                dish.setMeal(meal);
                dish.setPosition(index + 1);
                dish.setName(requestDish.name());
                DishNutrition.apply(dish, requestDish);
                meal.getDishes().add(dish);
            }
            meal.setCalories(sumCalories(meal.getDishes()));
            meal.setProteinGrams(sumMacro(meal.getDishes(), MealDish::getProteinGrams));
            meal.setCarbohydrateGrams(sumMacro(meal.getDishes(), MealDish::getCarbohydrateGrams));
            meal.setFatGrams(sumMacro(meal.getDishes(), MealDish::getFatGrams));
        }
        if (request.dishes().isEmpty()) {
            meal.getDishes().clear();
        }
        meal.setMealTime(request.mealTime());
        meal.setDurationMinutes(request.durationMinutes());
        meal.setNotes(request.notes());
    }

    private int sumCalories(List<MealDish> dishes) {
        long total = dishes.stream().mapToLong(MealDish::getCalories).sum();
        if (total > Integer.MAX_VALUE) throw new BadRequestException("Meal calories exceed the supported range");
        return (int) total;
    }

    private BigDecimal sumMacro(List<MealDish> dishes, java.util.function.Function<MealDish, BigDecimal> value) {
        BigDecimal total = dishes.stream().map(value).anyMatch(java.util.Objects::isNull)
            ? null
            : dishes.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total != null && total.compareTo(new BigDecimal("99999999.99")) > 0) throw new BadRequestException("Meal macros exceed the supported range");
        return total;
    }

    private void validateDate(LocalDate date) {
        if (date.isAfter(LocalDate.now(DateTimes.USER_ZONE))) {
            throw new BadRequestException("Meal date cannot be in the future");
        }
    }

    private void requireConfirmation(boolean confirmed) {
        if (!confirmed) {
            throw new BadRequestException("Meal write requires explicit confirmation");
        }
    }
}
