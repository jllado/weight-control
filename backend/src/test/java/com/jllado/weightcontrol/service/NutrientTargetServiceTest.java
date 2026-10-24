package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetOverrides;
import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetSource;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.UserSex;
import com.jllado.weightcontrol.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NutrientTargetServiceTest {

    @Mock UserRepository userRepository;
    @Mock WeeklyCaloriePlanService caloriePlans;
    @InjectMocks NutrientTargetService service;

    @Test
    void savesResolvedPersonalTargetsAndPreservesThemForLegacyUpdates() throws Exception {
        var mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        var owner = new User();
        when(userRepository.save(owner)).thenReturn(owner);
        var date = LocalDate.of(2026, 10, 10);
        when(caloriePlans.effectiveTargets(owner, date, date)).thenReturn(java.util.List.of(
            new WeeklyCaloriePlanService.EffectiveCalorieTarget(date, 2050, 0)));
        var request = mapper.readValue("""
            {"vitaminDMicrograms":20,"epaDha":{"value":250},"ala":{"value":1600},"fiber":{"value":25},
             "macros":{"proteinGrams":115,"fatPercentage":30}}
            """, NutrientTargetOverrides.class);

        var saved = mapper.valueToTree(service.update(owner, request, date));
        assertEquals(115, saved.at("/overrides/macros/proteinGrams").asInt());
        assertEquals(30, saved.at("/overrides/macros/fatPercentage").asInt());
        assertEquals(115, saved.at("/macroTargets/protein/value").asInt());
        assertEquals("MINIMUM", saved.at("/macroTargets/protein/comparison").asText());
        assertEquals(68.33, saved.at("/macroTargets/fat/value").asDouble());
        assertEquals("MAXIMUM", saved.at("/macroTargets/fat/comparison").asText());
        assertEquals(243.75, saved.at("/macroTargets/carbohydrate/value").asDouble());
        assertEquals(250, saved.at("/targets/epaDha/value").asInt());
        assertEquals(1600, saved.at("/targets/ala/value").asInt());
        assertEquals(25, saved.at("/targets/fiber/value").asInt());
        assertEquals("PERSONAL", saved.at("/targets/epaDha/source").asText());

        var legacy = mapper.valueToTree(service.update(owner, new NutrientTargetOverrides(null, null, null), date));
        assertEquals(115, legacy.at("/overrides/macros/proteinGrams").asInt());
        assertEquals(250, legacy.at("/overrides/epaDha/value").asInt());
        assertEquals(1600, legacy.at("/overrides/ala/value").asInt());
        assertEquals(25, legacy.at("/overrides/fiber/value").asInt());
        var another = mapper.valueToTree(service.resolve(new User(), date));
        assertEquals("NONE", another.at("/epaDha/source").asText());

        var clear = mapper.readValue("""
            {"epaDha":{"value":null},"ala":{"value":null},"fiber":{"value":null},
             "macros":{"proteinGrams":null,"fatPercentage":null}}
            """, NutrientTargetOverrides.class);
        var cleared = mapper.valueToTree(service.update(owner, clear, date));
        org.junit.jupiter.api.Assertions.assertTrue(cleared.at("/overrides/macros/proteinGrams").isNull());
        org.junit.jupiter.api.Assertions.assertTrue(cleared.at("/overrides/macros/fatPercentage").isNull());
        assertEquals("NONE", cleared.at("/targets/epaDha/source").asText());
        assertEquals("NONE", cleared.at("/targets/ala/source").asText());
        assertEquals("NONE", cleared.at("/targets/fiber/source").asText());
    }

    @Test
    void appliesAdultReferencesAndDoesNotInventAnOmega3Target() {
        User user = new User();
        user.setBirthDate(LocalDate.of(1990, 2, 1));
        user.setSex(UserSex.MALE);

        var targets = service.resolve(user, LocalDate.of(2026, 10, 10));

        assertEquals(new BigDecimal("15"), targets.vitaminD().value());
        assertEquals(NutrientTargetSource.EFSA_AI, targets.vitaminD().source());
        assertEquals(new BigDecimal("350"), targets.magnesium().value());
        assertEquals(NutrientTargetSource.EFSA_AI, targets.magnesium().source());
        assertEquals(NutrientTargetSource.NONE, targets.omega3().source());
        assertNull(targets.omega3().value());
    }

    @Test
    void usesTheFemaleAdultMagnesiumReferenceAtTheEighteenthBirthday() {
        User user = new User();
        user.setBirthDate(LocalDate.of(2008, 10, 10));
        user.setSex(UserSex.FEMALE);

        var targets = service.resolve(user, LocalDate.of(2026, 10, 10));

        assertEquals(new BigDecimal("300"), targets.magnesium().value());
    }

    @Test
    void requiresAdultProfileForReferencesAndPrefersPersonalOverrides() {
        User user = new User();
        user.setBirthDate(LocalDate.of(2010, 10, 10));
        user.setSex(UserSex.FEMALE);
        user.setNutrientVitaminDTargetMicrograms(new BigDecimal("20"));
        when(userRepository.save(user)).thenReturn(user);
        var date = LocalDate.of(2026, 10, 10);
        when(caloriePlans.effectiveTargets(user, date, date)).thenReturn(java.util.List.of(
            new WeeklyCaloriePlanService.EffectiveCalorieTarget(date, 2050, 0)));

        var saved = service.update(user, new NutrientTargetOverrides(new BigDecimal("20"), null, null), date);

        assertEquals(NutrientTargetSource.PERSONAL, saved.targets().vitaminD().source());
        assertEquals(new BigDecimal("20"), saved.targets().vitaminD().value());
        assertEquals(NutrientTargetSource.NONE, saved.targets().magnesium().source());
    }
}
