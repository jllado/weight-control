package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

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
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NutrientTargetServiceTest {

    @Mock UserRepository userRepository;

    @Test
    void appliesAdultReferencesAndDoesNotInventAnOmega3Target() {
        User user = new User();
        user.setBirthDate(LocalDate.of(1990, 2, 1));
        user.setSex(UserSex.MALE);

        var targets = new NutrientTargetService(userRepository).resolve(user, LocalDate.of(2026, 10, 10));

        assertEquals(new BigDecimal("15"), targets.vitaminD().value());
        assertEquals(NutrientTargetSource.EFSA_AI, targets.vitaminD().source());
        assertEquals(new BigDecimal("350"), targets.magnesium().value());
        assertEquals(NutrientTargetSource.EFSA_PRI, targets.magnesium().source());
        assertEquals(NutrientTargetSource.NONE, targets.omega3().source());
        assertNull(targets.omega3().value());
    }

    @Test
    void usesTheFemaleAdultMagnesiumReferenceAtTheEighteenthBirthday() {
        User user = new User();
        user.setBirthDate(LocalDate.of(2008, 10, 10));
        user.setSex(UserSex.FEMALE);

        var targets = new NutrientTargetService(userRepository).resolve(user, LocalDate.of(2026, 10, 10));

        assertEquals(new BigDecimal("300"), targets.magnesium().value());
    }

    @Test
    void requiresAdultProfileForReferencesAndPrefersPersonalOverrides() {
        User user = new User();
        user.setBirthDate(LocalDate.of(2010, 10, 10));
        user.setSex(UserSex.FEMALE);
        user.setNutrientVitaminDTargetMicrograms(new BigDecimal("20"));
        when(userRepository.save(user)).thenReturn(user);

        var service = new NutrientTargetService(userRepository);
        var saved = service.update(user, new NutrientTargetOverrides(new BigDecimal("20"), null, null), LocalDate.of(2026, 10, 10));

        assertEquals(NutrientTargetSource.PERSONAL, saved.targets().vitaminD().source());
        assertEquals(new BigDecimal("20"), saved.targets().vitaminD().value());
        assertEquals(NutrientTargetSource.NONE, saved.targets().magnesium().source());
    }
}
