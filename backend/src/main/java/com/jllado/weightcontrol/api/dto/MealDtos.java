package com.jllado.weightcontrol.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.CatalogFood;
import com.jllado.weightcontrol.domain.DishUnit;
import com.jllado.weightcontrol.domain.MealSource;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class MealDtos {

    private MealDtos() {
    }

    private static boolean hasNewNutrientEvidence(BigDecimal value, String source, Boolean estimated) {
        return value == null ? source == null && estimated == null : source != null && !source.isBlank() && estimated != null;
    }

    public static final class WholeNumberDeserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.isExpectedNumberIntToken()) {
                return (Integer) context.handleUnexpectedToken(Integer.class, parser);
            }
            return parser.getIntValue();
        }
    }

    public static final class RequiredWholeNumberDeserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.isExpectedNumberIntToken()) {
                return (Integer) context.handleUnexpectedToken(Integer.class, parser);
            }
            return parser.getIntValue();
        }

        @Override
        public Integer getAbsentValue(DeserializationContext context) {
            return null;
        }

        @Override
        public Integer getNullValue(DeserializationContext context) throws JsonMappingException {
            throw JsonMappingException.from(context.getParser(), "rating must be an integer from 1 to 10 when provided");
        }
    }

    public record MealRequest(
        @NotNull LocalDate date,
        @NotNull MealType mealType,
        @NotNull @DecimalMin("0") Integer calories,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal proteinGrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal carbohydrateGrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal fatGrams,
        LocalTime mealTime,
        String notes,
        List<@Valid MealDishRequest> dishes,
        @Positive @JsonDeserialize(using = WholeNumberDeserializer.class) Integer durationMinutes,
        @Min(2) @Max(2) Integer nutrientSchemaVersion
    ) {
        @JsonIgnore
        @AssertTrue(message = "Duration is required when a meal has a start time")
        public boolean isDurationValid() {
            return mealTime == null || durationMinutes != null;
        }

        public MealRequest {
            dishes = dishes == null ? List.of() : dishes;
        }
        public MealRequest(LocalDate date, MealType mealType, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, LocalTime mealTime, String notes, Integer durationMinutes) {
            this(date, mealType, calories, proteinGrams, carbohydrateGrams, fatGrams, mealTime, notes, List.of(), durationMinutes, null);
        }
        public MealRequest(LocalDate date, MealType mealType, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, LocalTime mealTime, String notes, List<MealDishRequest> dishes, Integer durationMinutes) {
            this(date, mealType, calories, proteinGrams, carbohydrateGrams, fatGrams, mealTime, notes, dishes, durationMinutes, null);
        }
    }

    public record MealRatingRequest(@NotNull @Min(1) @Max(10) @JsonDeserialize(using = WholeNumberDeserializer.class) Integer rating) {
    }

    public record CoachMealRatingRequest(@NotNull @Min(1) @Max(10) @JsonDeserialize(using = WholeNumberDeserializer.class) Integer rating, @AssertTrue boolean confirmed) {
    }

    public record CatalogFoodResponse(Long id, String name, Integer calories, BigDecimal proteinGrams,
        BigDecimal carbohydrateGrams, BigDecimal fatGrams, BigDecimal quantity, boolean fruit,
        DishUnit unit, DishReference reference,
        BigDecimal vitaminDMicrograms,
        BigDecimal omega3Milligrams,
        BigDecimal magnesiumMilligrams,
        String nutrientSource,
        Boolean nutrientsEstimated, BigDecimal epaDhaMilligrams, String epaDhaSource, Boolean epaDhaEstimated,
        BigDecimal alaMilligrams, String alaSource, Boolean alaEstimated,
        BigDecimal fiberGrams, String fiberSource, Boolean fiberEstimated) {
        public CatalogFoodResponse(Long id, String name, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, BigDecimal quantity, boolean fruit, DishUnit unit, DishReference reference) {
            this(id, name, calories, proteinGrams, carbohydrateGrams, fatGrams, quantity, fruit, unit, reference, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        public CatalogFoodResponse(Long id, String name, Integer calories, BigDecimal proteinGrams,
            BigDecimal carbohydrateGrams, BigDecimal fatGrams, BigDecimal quantity, DishUnit unit, DishReference reference) {
            this(id, name, calories, proteinGrams, carbohydrateGrams, fatGrams, quantity, false, unit, reference);
        }
        public static CatalogFoodResponse from(CatalogFood food) {
            return new CatalogFoodResponse(food.getId(), food.getName(), food.getCalories(), food.getProteinGrams(),
                food.getCarbohydrateGrams(), food.getFatGrams(), food.getQuantity(), food.isFruit(), food.getUnit(), DishReference.from(food), food.getVitaminDMicrograms(), food.getOmega3Milligrams(), food.getMagnesiumMilligrams(), food.getNutrientSource(), food.getNutrientsEstimated(), food.getEpaDhaMilligrams(), food.getEpaDhaSource(), food.getEpaDhaEstimated(), food.getAlaMilligrams(), food.getAlaSource(), food.getAlaEstimated(), food.getFiberGrams(), food.getFiberSource(), food.getFiberEstimated());
        }
    }

    public record MealDishRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @DecimalMin("0") Integer calories,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal proteinGrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal carbohydrateGrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal fatGrams,
        @Positive @Digits(integer = 8, fraction = 3) BigDecimal quantity,
        DishUnit unit,
        @Valid DishReference reference,
        boolean fruit,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal vitaminDMicrograms,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal omega3Milligrams,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal magnesiumMilligrams,
        @NotBlank @Size(max = 500) String nutrientSource,
        @NotNull Boolean nutrientsEstimated,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal epaDhaMilligrams,
        @Size(max = 500) String epaDhaSource, Boolean epaDhaEstimated,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal alaMilligrams,
        @Size(max = 500) String alaSource, Boolean alaEstimated,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal fiberGrams,
        @Size(max = 500) String fiberSource, Boolean fiberEstimated,
        @Min(2) @Max(2) Integer nutrientSchemaVersion) {
        @JsonIgnore
        @AssertTrue(message = "Recorded EPA+DHA, ALA, and fiber need their own source and estimate flag")
        public boolean isNewNutrientEvidenceComplete() {
            return hasNewNutrientEvidence(epaDhaMilligrams, epaDhaSource, epaDhaEstimated)
                && hasNewNutrientEvidence(alaMilligrams, alaSource, alaEstimated)
                && hasNewNutrientEvidence(fiberGrams, fiberSource, fiberEstimated);
        }
        public MealDishRequest(String name, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, BigDecimal quantity, DishUnit unit, DishReference reference, boolean fruit,
            BigDecimal vitaminDMicrograms, BigDecimal omega3Milligrams, BigDecimal magnesiumMilligrams, String nutrientSource, Boolean nutrientsEstimated,
            BigDecimal epaDhaMilligrams, String epaDhaSource, Boolean epaDhaEstimated, Integer nutrientSchemaVersion) {
            this(name, calories, proteinGrams, carbohydrateGrams, fatGrams, quantity, unit, reference, fruit,
                vitaminDMicrograms, omega3Milligrams, magnesiumMilligrams, nutrientSource, nutrientsEstimated,
                epaDhaMilligrams, epaDhaSource, epaDhaEstimated, null, null, null, null, null, null, nutrientSchemaVersion);
        }
        public MealDishRequest(String name, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, BigDecimal quantity, DishUnit unit, DishReference reference, boolean fruit,
            BigDecimal vitaminDMicrograms, BigDecimal omega3Milligrams, BigDecimal magnesiumMilligrams, String nutrientSource, Boolean nutrientsEstimated) {
            this(name, calories, proteinGrams, carbohydrateGrams, fatGrams, quantity, unit, reference, fruit, vitaminDMicrograms, omega3Milligrams, magnesiumMilligrams, nutrientSource, nutrientsEstimated, null, null, null, null, null, null, null, null, null, null);
        }
        public MealDishRequest(String name, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, BigDecimal quantity, DishUnit unit, DishReference reference, boolean fruit) {
            this(name, calories, proteinGrams, carbohydrateGrams, fatGrams, quantity, unit, reference, fruit, null, null, null, null, null);
        }

        public MealDishRequest(String name, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams) {
            this(name, calories, proteinGrams, carbohydrateGrams, fatGrams, null, null, null, false);
        }
        public MealDishRequest(String name, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, BigDecimal quantity, DishUnit unit, DishReference reference) {
            this(name, calories, proteinGrams, carbohydrateGrams, fatGrams, quantity, unit, reference, false);
        }
    }

    public record DishReference(
        @NotNull @Positive @Digits(integer = 8, fraction = 3) BigDecimal quantity,
        @NotNull @DecimalMin("0") Integer calories,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal proteinGrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal carbohydrateGrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal fatGrams,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal vitaminDMicrograms,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal omega3Milligrams,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal magnesiumMilligrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal epaDhaMilligrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal alaMilligrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal fiberGrams) {
        public DishReference(BigDecimal quantity, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams,
            BigDecimal vitaminDMicrograms, BigDecimal omega3Milligrams, BigDecimal magnesiumMilligrams, BigDecimal epaDhaMilligrams) {
            this(quantity, calories, proteinGrams, carbohydrateGrams, fatGrams, vitaminDMicrograms, omega3Milligrams, magnesiumMilligrams, epaDhaMilligrams, null, null);
        }
        public DishReference(BigDecimal quantity, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams,
            BigDecimal vitaminDMicrograms, BigDecimal omega3Milligrams, BigDecimal magnesiumMilligrams) {
            this(quantity, calories, proteinGrams, carbohydrateGrams, fatGrams, vitaminDMicrograms, omega3Milligrams, magnesiumMilligrams, null);
        }
        public DishReference(BigDecimal quantity, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams) {
            this(quantity, calories, proteinGrams, carbohydrateGrams, fatGrams, null, null, null);
        }

        public static DishReference from(com.jllado.weightcontrol.domain.FoodPortion dish) {
            return new DishReference(dish.getReferenceQuantity(), dish.getReferenceCalories(), dish.getReferenceProteinGrams(), dish.getReferenceCarbohydrateGrams(), dish.getReferenceFatGrams(), dish.getReferenceVitaminDMicrograms(), dish.getReferenceOmega3Milligrams(), dish.getReferenceMagnesiumMilligrams(), dish.getReferenceEpaDhaMilligrams(), dish.getReferenceAlaMilligrams(), dish.getReferenceFiberGrams());
        }
    }

    public record CoachMealRequest(
        @NotNull LocalDate date,
        @NotNull MealType mealType,
        @NotNull @DecimalMin("0") Integer calories,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal proteinGrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal carbohydrateGrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal fatGrams,
        @NotNull LocalTime mealTime,
        String notes,
        @NotNull MealSource source,
        @AssertTrue boolean confirmed,
        @NotEmpty List<@Valid CoachMealDishRequest> dishes,
        @Positive @JsonDeserialize(using = WholeNumberDeserializer.class) Integer durationMinutes,
        @Min(1) @Max(10) @JsonDeserialize(using = RequiredWholeNumberDeserializer.class) Integer rating,
        @Min(2) @Max(2) Integer nutrientSchemaVersion
    ) {
        public CoachMealRequest {
            dishes = dishes == null ? List.of() : dishes;
            durationMinutes = durationMinutes == null ? 30 : durationMinutes;
        }
        public CoachMealRequest(LocalDate date, MealType mealType, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, LocalTime mealTime, String notes, MealSource source, boolean confirmed, Integer durationMinutes) {
            this(date, mealType, calories, proteinGrams, carbohydrateGrams, fatGrams, mealTime, notes, source, confirmed, List.of(), durationMinutes, null, null);
        }
        public CoachMealRequest(LocalDate date, MealType mealType, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, LocalTime mealTime, String notes, MealSource source, boolean confirmed, List<CoachMealDishRequest> dishes, Integer durationMinutes) {
            this(date, mealType, calories, proteinGrams, carbohydrateGrams, fatGrams, mealTime, notes, source, confirmed, dishes, durationMinutes, null, null);
        }
        public CoachMealRequest(LocalDate date, MealType mealType, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, LocalTime mealTime, String notes, MealSource source, boolean confirmed, List<CoachMealDishRequest> dishes, Integer durationMinutes, Integer rating) {
            this(date, mealType, calories, proteinGrams, carbohydrateGrams, fatGrams, mealTime, notes, source, confirmed, dishes, durationMinutes, rating, null);
        }
        public MealRequest meal() {
            return new MealRequest(
                date,
                mealType,
                calories,
                proteinGrams,
                carbohydrateGrams,
                fatGrams,
                mealTime,
                notes,
                dishes.stream().map(CoachMealDishRequest::meal).toList(),
                durationMinutes,
                nutrientSchemaVersion
            );
        }
    }

    public record CoachMealDishRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @DecimalMin("0") Integer calories,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal proteinGrams,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal carbohydrateGrams,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal fatGrams,
        @Positive @Digits(integer = 8, fraction = 3) BigDecimal quantity,
        DishUnit unit,
        @NotNull @Valid DishReference reference,
        boolean addToCatalog,
        Boolean fruit,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal vitaminDMicrograms,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal omega3Milligrams,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal magnesiumMilligrams,
        @NotBlank @Size(max = 500) String nutrientSource,
        @NotNull Boolean nutrientsEstimated,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal epaDhaMilligrams,
        @Size(max = 500) String epaDhaSource, Boolean epaDhaEstimated,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal alaMilligrams,
        @Size(max = 500) String alaSource, Boolean alaEstimated,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal fiberGrams,
        @NotBlank @Size(max = 500) String fiberSource, @NotNull Boolean fiberEstimated) {
        @JsonIgnore
        @AssertTrue(message = "Recorded EPA+DHA, ALA, and fiber need their own source and estimate flag")
        public boolean isNewNutrientEvidenceComplete() {
            return hasNewNutrientEvidence(epaDhaMilligrams, epaDhaSource, epaDhaEstimated)
                && hasNewNutrientEvidence(alaMilligrams, alaSource, alaEstimated)
                && hasNewNutrientEvidence(fiberGrams, fiberSource, fiberEstimated);
        }
        public CoachMealDishRequest(String name, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, BigDecimal quantity, DishUnit unit, DishReference reference, boolean addToCatalog, Boolean fruit,
            BigDecimal vitaminDMicrograms, BigDecimal omega3Milligrams, BigDecimal magnesiumMilligrams, String nutrientSource, Boolean nutrientsEstimated,
            BigDecimal epaDhaMilligrams, String epaDhaSource, Boolean epaDhaEstimated) {
            this(name, calories, proteinGrams, carbohydrateGrams, fatGrams, quantity, unit, reference, addToCatalog, fruit,
                vitaminDMicrograms, omega3Milligrams, magnesiumMilligrams, nutrientSource, nutrientsEstimated,
                epaDhaMilligrams, epaDhaSource, epaDhaEstimated, null, null, null, null, null, null);
        }
        public CoachMealDishRequest(String name, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, BigDecimal quantity, DishUnit unit, DishReference reference, boolean addToCatalog, Boolean fruit,
            BigDecimal vitaminDMicrograms, BigDecimal omega3Milligrams, BigDecimal magnesiumMilligrams, String nutrientSource, Boolean nutrientsEstimated) {
            this(name, calories, proteinGrams, carbohydrateGrams, fatGrams, quantity, unit, reference, addToCatalog, fruit, vitaminDMicrograms, omega3Milligrams, magnesiumMilligrams, nutrientSource, nutrientsEstimated, null, null, null);
        }
        public CoachMealDishRequest(String name, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, BigDecimal quantity, DishUnit unit, DishReference reference, boolean addToCatalog, Boolean fruit) {
            this(name, calories, proteinGrams, carbohydrateGrams, fatGrams, quantity, unit, reference, addToCatalog, fruit, null, null, null, null, null);
        }

        public CoachMealDishRequest(String name, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams) {
            this(name, calories, proteinGrams, carbohydrateGrams, fatGrams, null, null, null, false, null);
        }
        public CoachMealDishRequest(String name, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, BigDecimal quantity, DishUnit unit, DishReference reference) {
            this(name, calories, proteinGrams, carbohydrateGrams, fatGrams, quantity, unit, reference, false, null);
        }
        public CoachMealDishRequest(String name, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, BigDecimal quantity, DishUnit unit, DishReference reference, boolean addToCatalog) {
            this(name, calories, proteinGrams, carbohydrateGrams, fatGrams, quantity, unit, reference, addToCatalog, null);
        }
        @JsonIgnore
        @AssertTrue(message = "Coach dish references require every known nutrient")
        public boolean isReferenceComplete() {
            return reference != null && reference.proteinGrams() != null && reference.carbohydrateGrams() != null
                && reference.fatGrams() != null && reference.vitaminDMicrograms() != null
                && reference.magnesiumMilligrams() != null && reference.fiberGrams() != null
                && (omega3Milligrams == null || reference.omega3Milligrams() != null)
                && (epaDhaMilligrams == null || reference.epaDhaMilligrams() != null)
                && (alaMilligrams == null || reference.alaMilligrams() != null);
        }
        @JsonIgnore
        @AssertTrue(message = "Coach dish totals must match the confirmed reference portion")
        public boolean isNutritionConsistent() {
            return CoachDishReferenceConsistency.matches(meal());
        }
        public MealDishRequest meal() {
            return new MealDishRequest(name, calories, proteinGrams, carbohydrateGrams, fatGrams, quantity, unit, reference, Boolean.TRUE.equals(fruit),
                vitaminDMicrograms, omega3Milligrams, magnesiumMilligrams, nutrientSource, nutrientsEstimated,
                epaDhaMilligrams, epaDhaSource, epaDhaEstimated, alaMilligrams, alaSource, alaEstimated,
                fiberGrams, fiberSource, fiberEstimated, null);
        }
    }

    public record MealResponse(
        Long id,
        String dateFormat,
        LocalDate date,
        MealType mealType,
        Integer mealSequence,
        LocalTime mealTime,
        Integer calories,
        BigDecimal proteinGrams,
        BigDecimal carbohydrateGrams,
        BigDecimal fatGrams,
        String notes,
        Integer rating,
        MealSource source,
        List<MealDishResponse> dishes,
        Integer durationMinutes,
        NutritionDtos.NutrientSummary nutrients) {
        public MealResponse(Long id, String dateFormat, LocalDate date, MealType mealType, Integer mealSequence, LocalTime mealTime, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, String notes, Integer rating, MealSource source, List<MealDishResponse> dishes, Integer durationMinutes) {
            this(id, dateFormat, date, mealType, mealSequence, mealTime, calories, proteinGrams, carbohydrateGrams, fatGrams, notes, rating, source, dishes, durationMinutes, null);
        }

        public static MealResponse from(Meal meal) {
            return new MealResponse(
                meal.getId(),
                DateTimes.formatDate(meal.getMealDate()),
                meal.getMealDate(),
                meal.getMealType(),
                meal.getMealSequence(),
                meal.getMealTime(),
                meal.getCalories(),
                meal.getProteinGrams(),
                meal.getCarbohydrateGrams(),
                meal.getFatGrams(),
                meal.getNotes(),
                meal.getRating(),
                meal.getSource(),
                meal.getDishes().stream().map(MealDishResponse::from).toList(),
                meal.getDurationMinutes(),
                com.jllado.weightcontrol.service.NutrientSummaryService.summarize(List.of(meal))
            );
        }
    }

    public record MealDishResponse(Long id, int position, String name, int calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, BigDecimal quantity, boolean fruit, DishUnit unit, DishReference reference,
        BigDecimal vitaminDMicrograms,
        BigDecimal omega3Milligrams,
        BigDecimal magnesiumMilligrams,
        String nutrientSource,
        Boolean nutrientsEstimated, BigDecimal epaDhaMilligrams, String epaDhaSource, Boolean epaDhaEstimated,
        BigDecimal alaMilligrams, String alaSource, Boolean alaEstimated,
        BigDecimal fiberGrams, String fiberSource, Boolean fiberEstimated) {
        public MealDishResponse(Long id, int position, String name, int calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, BigDecimal quantity, boolean fruit, DishUnit unit, DishReference reference) {
            this(id, position, name, calories, proteinGrams, carbohydrateGrams, fatGrams, quantity, fruit, unit, reference, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        public static MealDishResponse from(com.jllado.weightcontrol.domain.MealDish dish) {
            return new MealDishResponse(dish.getId(), dish.getPosition(), dish.getName(), dish.getCalories(), dish.getProteinGrams(), dish.getCarbohydrateGrams(), dish.getFatGrams(), dish.getQuantity(), dish.isFruit(), dish.getUnit(), DishReference.from(dish), dish.getVitaminDMicrograms(), dish.getOmega3Milligrams(), dish.getMagnesiumMilligrams(), dish.getNutrientSource(), dish.getNutrientsEstimated(), dish.getEpaDhaMilligrams(), dish.getEpaDhaSource(), dish.getEpaDhaEstimated(), dish.getAlaMilligrams(), dish.getAlaSource(), dish.getAlaEstimated(), dish.getFiberGrams(), dish.getFiberSource(), dish.getFiberEstimated());
        }
    }
}
