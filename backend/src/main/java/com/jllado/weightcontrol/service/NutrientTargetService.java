package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.NutritionDtos.MacroTargetValue;
import com.jllado.weightcontrol.api.dto.NutritionDtos.MacroTargetsResponse;
import com.jllado.weightcontrol.api.dto.NutritionDtos.MacroTargetSettings;
import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetOverrides;
import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetSettingsResponse;
import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetSource;
import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetValue;
import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetsResponse;
import com.jllado.weightcontrol.api.dto.NutritionDtos.PersonalNutrientTarget;
import com.jllado.weightcontrol.domain.MacroTargetComparison;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.UserSex;
import com.jllado.weightcontrol.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class NutrientTargetService {

    private static final BigDecimal VITAMIN_D_REFERENCE = new BigDecimal("15");
    private static final BigDecimal MAGNESIUM_MALE_REFERENCE = new BigDecimal("350");
    private static final BigDecimal MAGNESIUM_FEMALE_REFERENCE = new BigDecimal("300");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal FOUR = new BigDecimal("4");
    private static final BigDecimal NINE = new BigDecimal("9");

    private final UserRepository userRepository;
    private final WeeklyCaloriePlanService caloriePlans;

    public NutrientTargetService(UserRepository userRepository, WeeklyCaloriePlanService caloriePlans) {
        this.userRepository = userRepository;
        this.caloriePlans = caloriePlans;
    }

    public NutrientTargetSettingsResponse settings(User user, LocalDate asOf) {
        int calories = caloriePlans.effectiveTargets(user, asOf, asOf).getFirst().calories();
        return new NutrientTargetSettingsResponse(resolve(user, asOf), new NutrientTargetOverrides(
            user.getNutrientVitaminDTargetMicrograms(), user.getNutrientOmega3TargetMilligrams(),
            user.getNutrientMagnesiumTargetMilligrams(),
            new MacroTargetSettings(user.getMacroProteinTargetGrams(), user.getMacroFatTargetPercentage()),
            new PersonalNutrientTarget(user.getNutrientEpaDhaTargetMilligrams()),
            new PersonalNutrientTarget(user.getNutrientAlaTargetMilligrams()),
            new PersonalNutrientTarget(user.getNutrientFiberTargetGrams())
        ), macroTargets(user, calories));
    }

    public NutrientTargetSettingsResponse update(User user, NutrientTargetOverrides overrides, LocalDate asOf) {
        user.setNutrientVitaminDTargetMicrograms(overrides.vitaminDMicrograms());
        user.setNutrientOmega3TargetMilligrams(overrides.omega3Milligrams());
        user.setNutrientMagnesiumTargetMilligrams(overrides.magnesiumMilligrams());
        if (overrides.epaDha() != null) user.setNutrientEpaDhaTargetMilligrams(overrides.epaDha().value());
        if (overrides.ala() != null) user.setNutrientAlaTargetMilligrams(overrides.ala().value());
        if (overrides.fiber() != null) user.setNutrientFiberTargetGrams(overrides.fiber().value());
        if (overrides.macros() != null) {
            user.setMacroProteinTargetGrams(overrides.macros().proteinGrams());
            user.setMacroFatTargetPercentage(overrides.macros().fatPercentage());
        }
        return settings(userRepository.save(user), asOf);
    }

    public MacroTargetsResponse macroTargets(User user, int calorieBudget) {
        BigDecimal protein = user.getMacroProteinTargetGrams();
        BigDecimal fatPercentage = user.getMacroFatTargetPercentage();
        var proteinTarget = new MacroTargetValue(protein, MacroTargetComparison.MINIMUM, null);
        if (calorieBudget <= 0) {
            var unavailable = new MacroTargetValue(null, MacroTargetComparison.MAXIMUM, "No effective calorie budget for this date");
            return new MacroTargetsResponse(proteinTarget, unavailable, unavailable, calorieBudget);
        }
        BigDecimal calories = BigDecimal.valueOf(calorieBudget);
        BigDecimal fatEnergy = fatPercentage == null ? null : calories.multiply(fatPercentage).divide(HUNDRED);
        BigDecimal fat = fatEnergy == null ? null : fatEnergy.divide(NINE, MathContext.DECIMAL128).setScale(2, RoundingMode.HALF_UP);
        var fatTarget = new MacroTargetValue(fat, MacroTargetComparison.MAXIMUM,
            fat == null ? "Set a fat percentage" : null);
        if (protein == null || fatEnergy == null) {
            return new MacroTargetsResponse(proteinTarget,
                new MacroTargetValue(null, MacroTargetComparison.MAXIMUM, "Set protein and fat targets"), fatTarget, calorieBudget);
        }
        BigDecimal carbohydrateEnergy = calories.subtract(protein.multiply(FOUR)).subtract(fatEnergy);
        if (carbohydrateEnergy.signum() < 0) {
            return new MacroTargetsResponse(proteinTarget,
                new MacroTargetValue(null, MacroTargetComparison.MAXIMUM, "Protein and fat allocations exceed this calorie budget"),
                fatTarget, calorieBudget);
        }
        BigDecimal carbohydrates = carbohydrateEnergy.divide(FOUR).setScale(2, RoundingMode.HALF_UP);
        return new MacroTargetsResponse(proteinTarget,
            new MacroTargetValue(carbohydrates, MacroTargetComparison.MAXIMUM, null), fatTarget, calorieBudget);
    }

    public NutrientTargetsResponse resolve(User user, LocalDate asOf) {
        if (asOf.isAfter(LocalDate.now(com.jllado.weightcontrol.util.DateTimes.USER_ZONE))) {
            throw new BadRequestException("Nutrient targets cannot be resolved for a future date");
        }
        boolean supportedAdult = user.getBirthDate() != null
            && !user.getBirthDate().plusYears(18).isAfter(asOf)
            && user.getSex() != null;
        BigDecimal vitaminDReference = supportedAdult ? VITAMIN_D_REFERENCE : null;
        BigDecimal magnesiumReference = supportedAdult
            ? user.getSex() == UserSex.MALE ? MAGNESIUM_MALE_REFERENCE : MAGNESIUM_FEMALE_REFERENCE
            : null;
        return new NutrientTargetsResponse(
            target(user.getNutrientVitaminDTargetMicrograms(), vitaminDReference, NutrientTargetSource.EFSA_AI),
            target(user.getNutrientOmega3TargetMilligrams(), null, NutrientTargetSource.EFSA_AI),
            target(user.getNutrientMagnesiumTargetMilligrams(), magnesiumReference, NutrientTargetSource.EFSA_AI),
            target(user.getNutrientEpaDhaTargetMilligrams(), null, NutrientTargetSource.EFSA_AI),
            target(user.getNutrientAlaTargetMilligrams(), null, NutrientTargetSource.EFSA_AI),
            target(user.getNutrientFiberTargetGrams(), null, NutrientTargetSource.EFSA_AI)
        );
    }

    private NutrientTargetValue target(BigDecimal override, BigDecimal reference, NutrientTargetSource referenceSource) {
        if (override != null) return new NutrientTargetValue(override, NutrientTargetSource.PERSONAL, reference);
        if (reference != null) return new NutrientTargetValue(reference, referenceSource, reference);
        return new NutrientTargetValue(null, NutrientTargetSource.NONE, null);
    }
}
