package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.MealDtos.CoachMealRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.CoachMealRatingRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.MealRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.CopyDayRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.DishSnapshotRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.MealDishRequest;
import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealDish;
import com.jllado.weightcontrol.domain.MealSource;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MealRepository;
import com.jllado.weightcontrol.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final MealRatingService ratingService;
    private final FastingPeriodService fastingPeriodService;
    private final CatalogFoodService catalogFoodService;

    public MealService(MealRepository repository, FastingPeriodService fastingPeriodService, CatalogFoodService catalogFoodService, UserRepository userRepository, MealRatingService ratingService) {
        this.ratingService = ratingService;
        this.userRepository = userRepository;
        this.repository = repository;
        this.catalogFoodService = catalogFoodService;
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
        return create(lockMutations(user), request, MealSource.MANUAL);
    }

    private Meal create(User user, MealRequest request, MealSource source) {
        validateDate(request.date());
        Meal meal = new Meal();
        meal.setUser(user);
        meal.setSource(source);
        applyIdentity(meal, user, request.date(), request.mealType());
        apply(meal, request);
        Meal saved = CoachActionTiming.time("mealSave", "mealPersistence", () -> repository.save(meal));
        ratingService.apply(user, List.of(meal));
        CoachActionTiming.time("mealSave", "fastingRecalculation", () -> {
            fastingPeriodService.recalculateAutomaticPeriods(user);
            return null;
        });
        return saved;
    }

    public Meal update(User user, Long id, MealRequest request) {
        User owner = lockMutations(user);
        validateDate(request.date());
        Meal meal = requireOwned(user, id);
        FoodSnapshotCompatibility.requireAwareReplacement(request.nutrientSchemaVersion(), meal.getDishes());
        if (!meal.getMealDate().equals(request.date()) || meal.getMealType() != request.mealType()) {
            applyIdentity(meal, user, request.date(), request.mealType());
        }
        if (!request.dishes().isEmpty() && CoachFoodNutrition.sameOrderedFoods(meal.getDishes(), request.dishes(), false)) {
            applyMetadata(meal, request);
        } else {
            meal.getDishes().clear();
            repository.flush();
            apply(meal, request);
        }
        Meal saved = repository.save(meal);
        ratingService.apply(owner, List.of(meal));
        fastingPeriodService.recalculateAutomaticPeriods(user);
        return saved;
    }

    public Meal rateConfirmed(User user, Long id, CoachMealRatingRequest request) {
        requireConfirmation(request.confirmed());
        return findForUser(user, id);
    }

    public Meal createConfirmed(User user, CoachMealRequest request) {
        requireConfirmation(request.confirmed());
        var owner = lockMutations(user);
        var draft = request.meal();
        var foods = LegacyCoachSaturatedFat.complete(owner, draft.nutrientSchemaVersion(), draft.dishes(), catalogFoodService);
        Meal meal = create(owner, draft.withDishes(foods), request.source());
        CoachActionTiming.time("mealSave", "foodCatalogRegistration", () -> {
            registerCoachFoods(user, meal, request);
            return null;
        });
        return meal;
    }

    public Meal updateConfirmed(User user, Long id, CoachMealRequest request) {
        User owner = lockMutations(user);
        requireConfirmation(request.confirmed());
        validateDate(request.date());
        Meal meal = requireOwned(user, id);
        boolean legacyV3 = Integer.valueOf(3).equals(request.nutrientSchemaVersion());
        boolean unchanged = !request.dishes().isEmpty() && CoachFoodNutrition.sameOrderedMealFoods(meal.getDishes(), request.dishes(), legacyV3);
        if (!(legacyV3 && unchanged)) FoodSnapshotCompatibility.requireAwareReplacement(request.nutrientSchemaVersion(), meal.getDishes());
        var mealRequest = request.meal();
        if (!meal.getMealDate().equals(request.date()) || meal.getMealType() != request.mealType()) {
            applyIdentity(meal, user, request.date(), request.mealType());
        }
        if (unchanged) {
            applyMetadata(meal, mealRequest);
        } else {
            replaceCoachFoods(meal, request, mealRequest);
        }
        meal.setSource(request.source());
        Meal saved = repository.save(meal);
        ratingService.apply(owner, List.of(meal));
        registerCoachFoods(user, meal, request);
        fastingPeriodService.recalculateAutomaticPeriods(user);
        return saved;
    }

    private void replaceCoachFoods(Meal meal, CoachMealRequest request, MealRequest mealRequest) {
        var previousDishes = List.copyOf(meal.getDishes());
        var matchedPreviousDishes = new boolean[previousDishes.size()];
        var dishes = java.util.stream.IntStream.range(0, request.dishes().size()).mapToObj(index -> {
            var dish = request.dishes().get(index);
            boolean fruit = Boolean.TRUE.equals(dish.fruit());
            if (dish.fruit() == null) {
                String name = dish.name().trim().toLowerCase(java.util.Locale.ROOT);
                int previousIndex = java.util.stream.IntStream.range(0, previousDishes.size())
                    .filter(candidate -> !matchedPreviousDishes[candidate])
                    .filter(candidate -> previousDishes.get(candidate).getName().trim().toLowerCase(java.util.Locale.ROOT).equals(name))
                    .findFirst()
                    .orElse(-1);
                if (previousIndex < 0 && index < previousDishes.size() && !matchedPreviousDishes[index]) {
                    previousIndex = index;
                }
                if (previousIndex >= 0) {
                    matchedPreviousDishes[previousIndex] = true;
                    fruit = previousDishes.get(previousIndex).isFruit();
                }
            }
            return new MealDishRequest(
            dish.name(), dish.calories(), dish.proteinGrams(), dish.carbohydrateGrams(), dish.fatGrams(), dish.quantity(), dish.unit(), dish.reference(),
            fruit, dish.vitaminDMicrograms(), dish.omega3Milligrams(), dish.magnesiumMilligrams(), dish.nutrientSource(), dish.nutrientsEstimated(),
            dish.epaDhaMilligrams(), dish.epaDhaSource(), dish.epaDhaEstimated(),
            dish.alaMilligrams(), dish.alaSource(), dish.alaEstimated(),
            dish.fiberGrams(), dish.fiberSource(), dish.fiberEstimated(), request.nutrientSchemaVersion(), dish.saltGrams(), dish.saltSource(), dish.saltEstimated(), dish.saturatedFatGrams(), dish.saturatedFatSource(), dish.saturatedFatEstimated()
            );
        }).toList();
        var foods = LegacyCoachSaturatedFat.complete(meal.getUser(), request.nutrientSchemaVersion(), dishes, catalogFoodService);
        meal.getDishes().clear();
        repository.flush();
        apply(meal, mealRequest.withDishes(foods));
    }

    private User lockMutations(User user) {
        return userRepository.findByIdForUpdate(user.getId()).orElseThrow();
    }

    public List<Meal> copyDay(User user, CopyDayRequest request) {
        User owner = lockMutations(user);
        validateDate(request.date());
        var identities = new java.util.HashSet<String>();
        for (var snapshot : request.meals()) {
            if (!identities.add(snapshot.mealType() + ":" + snapshot.mealSequence())) {
                throw new BadRequestException("Duplicate meal type or snack sequence");
            }
        }
        if (!repository.findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(user, request.date(), request.date()).isEmpty()) {
            throw new BadRequestException("Target date must be empty before copying meals");
        }
        var saved = request.meals().stream().map(snapshot -> {
            Meal meal = new Meal();
            meal.setUser(user);
            meal.setSource(MealSource.MANUAL);
            meal.setMealDate(request.date());
            meal.setMealType(snapshot.mealType());
            meal.setMealSequence(snapshot.mealSequence());
            meal.setCalories(snapshot.calories());
            meal.setProteinGrams(snapshot.proteinGrams());
            meal.setCarbohydrateGrams(snapshot.carbohydrateGrams());
            meal.setFatGrams(snapshot.fatGrams());
            meal.setEstimatedSaturatedFatGrams(snapshot.estimatedSaturatedFatGrams());
            meal.setSaturatedFatEstimateSource(snapshot.saturatedFatEstimateSource());
            meal.setMealTime(snapshot.mealTime());
            meal.setDurationMinutes(snapshot.durationMinutes());
            meal.setNotes(snapshot.notes());
            for (var snapshotDish : snapshot.dishes()) {
                MealDish dish = new MealDish();
                dish.setMeal(meal);
                dish.setPosition(meal.getDishes().size() + 1);
                applySnapshot(dish, snapshotDish);
                meal.getDishes().add(dish);
            }
            return repository.save(meal);
        }).toList();
        ratingService.apply(owner, saved);
        fastingPeriodService.recalculateAutomaticPeriods(user);
        return saved;
    }

    private void applySnapshot(MealDish dish, DishSnapshotRequest snapshot) {
        DishNutrition.requireValidSaturatedFat(snapshot.saturatedFatGrams(), snapshot.fatGrams());
        DishNutrition.requireValidSaturatedFat(snapshot.reference().saturatedFatGrams(), snapshot.reference().fatGrams());
        dish.setName(snapshot.name());
        dish.setFruit(snapshot.fruit());
        dish.setQuantity(snapshot.quantity());
        dish.setUnit(snapshot.unit());
        dish.setCalories(snapshot.calories());
        dish.setProteinGrams(snapshot.proteinGrams());
        dish.setCarbohydrateGrams(snapshot.carbohydrateGrams());
        dish.setFatGrams(snapshot.fatGrams());
        dish.setVitaminDMicrograms(snapshot.vitaminDMicrograms());
        dish.setOmega3Milligrams(snapshot.omega3Milligrams());
        dish.setMagnesiumMilligrams(snapshot.magnesiumMilligrams());
        dish.setNutrientSource(snapshot.nutrientSource());
        dish.setNutrientsEstimated(snapshot.nutrientsEstimated());
        dish.setEpaDhaMilligrams(snapshot.epaDhaMilligrams());
        dish.setEpaDhaSource(snapshot.epaDhaSource());
        dish.setEpaDhaEstimated(snapshot.epaDhaEstimated());
        dish.setAlaMilligrams(snapshot.alaMilligrams());
        dish.setAlaSource(snapshot.alaSource());
        dish.setAlaEstimated(snapshot.alaEstimated());
        dish.setFiberGrams(snapshot.fiberGrams());
        dish.setFiberSource(snapshot.fiberSource());
        dish.setFiberEstimated(snapshot.fiberEstimated());
        dish.setSaltGrams(snapshot.saltGrams());
        dish.setSaturatedFatGrams(snapshot.saturatedFatGrams());
        dish.setSaltSource(snapshot.saltSource());
        dish.setSaturatedFatSource(snapshot.saturatedFatSource());
        dish.setSaltEstimated(snapshot.saltEstimated());
        dish.setSaturatedFatEstimated(snapshot.saturatedFatEstimated());
        dish.setReferenceQuantity(snapshot.reference().quantity());
        dish.setReferenceCalories(snapshot.reference().calories());
        dish.setReferenceProteinGrams(snapshot.reference().proteinGrams());
        dish.setReferenceCarbohydrateGrams(snapshot.reference().carbohydrateGrams());
        dish.setReferenceFatGrams(snapshot.reference().fatGrams());
        dish.setReferenceVitaminDMicrograms(snapshot.reference().vitaminDMicrograms());
        dish.setReferenceOmega3Milligrams(snapshot.reference().omega3Milligrams());
        dish.setReferenceMagnesiumMilligrams(snapshot.reference().magnesiumMilligrams());
        dish.setReferenceEpaDhaMilligrams(snapshot.reference().epaDhaMilligrams());
        dish.setReferenceAlaMilligrams(snapshot.reference().alaMilligrams());
        dish.setReferenceFiberGrams(snapshot.reference().fiberGrams());
        dish.setReferenceSaltGrams(snapshot.reference().saltGrams());
        dish.setReferenceSaturatedFatGrams(snapshot.reference().saturatedFatGrams());
    }

    private void registerCoachFoods(User user, Meal meal, CoachMealRequest request) {
        var foods = java.util.stream.IntStream.range(0, request.dishes().size())
            .filter(index -> request.dishes().get(index).addToCatalog())
            .mapToObj(index -> meal.getDishes().get(index)).toList();
        catalogFoodService.register(user, foods);
    }

    public void deleteConfirmed(User user, Long id, boolean confirmed) {
        requireConfirmation(confirmed);
        delete(user, id);
    }

    public void delete(User user, Long id) {
        lockMutations(user);
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

    public Meal findForUser(User user, Long id) {
        return requireOwned(user, id);
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
        if (meal.getEstimatedSaturatedFatGrams() != null && (!request.dishes().isEmpty()
            || !java.util.Objects.equals(meal.getNotes(), request.notes()) || !meal.getCalories().equals(request.calories())
            || !sameGrams(meal.getProteinGrams(), request.proteinGrams())
            || !sameGrams(meal.getCarbohydrateGrams(), request.carbohydrateGrams())
            || !sameGrams(meal.getFatGrams(), request.fatGrams()))) {
            if (!Integer.valueOf(4).equals(request.nutrientSchemaVersion())) {
                throw new BadRequestException("Reload this record before replacing its saturated fat estimate");
            }
            meal.setEstimatedSaturatedFatGrams(null);
            meal.setSaturatedFatEstimateSource(null);
        }
        if (request.estimatedSaturatedFatGrams() != null) {
            meal.setEstimatedSaturatedFatGrams(request.estimatedSaturatedFatGrams());
            meal.setSaturatedFatEstimateSource(request.saturatedFatEstimateSource());
        }
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
                dish.setFruit(requestDish.fruit());
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
        applyMetadata(meal, request);
    }

    private void applyMetadata(Meal meal, MealRequest request) {
        meal.setMealTime(request.mealTime());
        meal.setDurationMinutes(request.durationMinutes());
        meal.setNotes(request.notes());
    }

    private boolean sameGrams(BigDecimal saved, BigDecimal next) {
        return saved == null ? next == null : next != null && saved.compareTo(next) == 0;
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
