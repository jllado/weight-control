package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetSource;
import com.jllado.weightcontrol.domain.MacroTargetComparison;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NutrientAssessmentTest {
    @Test void vitaminReferenceIsNeutralButPersonalGoalIsAssessed() {
        assertEquals("REFERENCE_ONLY", NutrientAssessment.vitaminD(n(5), n(15), NutrientTargetSource.EFSA_AI, true).code());
        assertEquals("BELOW_GOAL", NutrientAssessment.vitaminD(n(5), n(15), NutrientTargetSource.PERSONAL, true).code());
    }
    @Test void omegaRequiresIndependentMinimaAndPreservesSupportedShortfall() {
        assertEquals("GOAL_MET", NutrientAssessment.omega3(n(250), n(250), true, n(1000), n(1000), true).code());
        assertEquals("BELOW_GOAL", NutrientAssessment.omega3(n(249), n(250), true, null, null, false).code());
        assertEquals("UNAVAILABLE", NutrientAssessment.omega3(n(250), n(250), true, null, n(1000), false).code());
        assertEquals("UNAVAILABLE", NutrientAssessment.omega3(n(249), n(250), false, n(999), n(1000), false).code());
        assertEquals("GOAL_MET", NutrientAssessment.omega3(n(250), n(250), false, n(1000), n(1000), false).code());
    }
    @Test void decisionsUseRawValuesAndMaximumPartialEvidenceCannotEstablishMet() {
        assertEquals("BELOW_GOAL", NutrientAssessment.compare(new BigDecimal("14.999"), n(15), MacroTargetComparison.MINIMUM, true).code());
        assertEquals("UNAVAILABLE", NutrientAssessment.compare(n(5), n(15), MacroTargetComparison.MAXIMUM, false).code());
        assertEquals("ABOVE_GOAL", NutrientAssessment.compare(n(16), n(15), MacroTargetComparison.MAXIMUM, false).code());
    }
    private BigDecimal n(int number) { return BigDecimal.valueOf(number); }
}
