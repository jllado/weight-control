package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetOverrides;
import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetSettingsResponse;
import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetSource;
import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetValue;
import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetsResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.UserSex;
import com.jllado.weightcontrol.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class NutrientTargetService {

    private static final BigDecimal VITAMIN_D_REFERENCE = new BigDecimal("15");
    private static final BigDecimal MAGNESIUM_MALE_REFERENCE = new BigDecimal("350");
    private static final BigDecimal MAGNESIUM_FEMALE_REFERENCE = new BigDecimal("300");

    private final UserRepository userRepository;

    public NutrientTargetService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public NutrientTargetSettingsResponse settings(User user, LocalDate asOf) {
        return new NutrientTargetSettingsResponse(resolve(user, asOf), new NutrientTargetOverrides(
            user.getNutrientVitaminDTargetMicrograms(), user.getNutrientOmega3TargetMilligrams(), user.getNutrientMagnesiumTargetMilligrams()
        ));
    }

    public NutrientTargetSettingsResponse update(User user, NutrientTargetOverrides overrides, LocalDate asOf) {
        user.setNutrientVitaminDTargetMicrograms(overrides.vitaminDMicrograms());
        user.setNutrientOmega3TargetMilligrams(overrides.omega3Milligrams());
        user.setNutrientMagnesiumTargetMilligrams(overrides.magnesiumMilligrams());
        return settings(userRepository.save(user), asOf);
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
            target(user.getNutrientMagnesiumTargetMilligrams(), magnesiumReference, NutrientTargetSource.EFSA_PRI)
        );
    }

    private NutrientTargetValue target(BigDecimal override, BigDecimal reference, NutrientTargetSource referenceSource) {
        if (override != null) return new NutrientTargetValue(override, NutrientTargetSource.PERSONAL, reference);
        if (reference != null) return new NutrientTargetValue(reference, referenceSource, reference);
        return new NutrientTargetValue(null, NutrientTargetSource.NONE, null);
    }
}
