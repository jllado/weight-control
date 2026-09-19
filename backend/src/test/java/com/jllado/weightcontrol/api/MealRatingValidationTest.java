package com.jllado.weightcontrol.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import com.jllado.weightcontrol.service.MealService;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.PersonalRecordMutationService;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealType;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.jllado.weightcontrol.api.dto.MealDtos.MealRatingRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class MealRatingValidationTest {
    @Test
    void manualEndpointSavesTenPointScoresAndRejectsInvalidRequests() throws Exception {
        var service = mock(MealService.class);
        var currentUser = mock(CurrentUserService.class);
        var mutationService = mock(PersonalRecordMutationService.class);
        var owner = new User();
        owner.setId(1L);
        when(currentUser.requireUser()).thenReturn(owner);
        var meal = new Meal();
        meal.setId(10L);
        meal.setMealDate(LocalDate.of(2026, 8, 12));
        meal.setMealType(MealType.LUNCH);
        meal.setMealSequence(1);
        meal.setRating(8);
        when(service.rate(owner, 10L, 8)).thenReturn(meal);
        var mvc = standaloneSetup(new MealController(service, currentUser, mutationService)).build();
        mvc.perform(put("/api/meals/10/rating")
                .contentType("application/json").content("{\"rating\":8}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rating").value(8));
        for (String body : new String[] {"{}", "{\"rating\":0}", "{\"rating\":11}", "{\"rating\":8.5}"}) {
            mvc.perform(put("/api/meals/10/rating")
                    .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        }
        verify(service).rate(owner, 10L, 8);
        verifyNoMoreInteractions(service);
        verifyNoInteractions(mutationService);
    }

    @Test
    void manualRatingsUseWholeNumbersFromOneThroughTen() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (int rating : new int[] {1, 8, 10}) {
                assertTrue(validator.validate(mapper.readValue("{\"rating\":" + rating + "}", MealRatingRequest.class)).isEmpty());
            }
            for (Integer rating : new Integer[] {null, 0, 11}) {
                assertFalse(validator.validate(new MealRatingRequest(rating)).isEmpty());
            }
        }
        assertThrows(JsonProcessingException.class, () -> mapper.readValue("{\"rating\":8.5}", MealRatingRequest.class));
    }
}
