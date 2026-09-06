package com.jllado.weightcontrol.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jllado.weightcontrol.api.dto.MealDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.CatalogFoodService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CatalogFoodControllerTest {
    private final CatalogFoodService service = mock(CatalogFoodService.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final User user = new User();
    private MockMvc mvc;
    private static final String BODY = """
        {"name":"Oats","quantity":60,"unit":"GRAM","calories":206,"proteinGrams":8,"carbohydrateGrams":34,"fatGrams":4}
        """;
    @BeforeEach void setup() {
        when(currentUser.requireUser()).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(new CatalogFoodController(service, currentUser)).build();
    }
    @Test void mapsAuthenticatedCatalogWritesAndReads() throws Exception {
        var response = new CatalogFoodResponse(1L, "Oats", 206, null, null, null, new BigDecimal("60"), DishUnit.GRAM, new DishReference(new BigDecimal("60"), 206, null, null, null));
        when(service.create(eq(user), any())).thenReturn(response);
        when(service.update(eq(user), eq(1L), any())).thenReturn(response);
        when(service.findAll(user)).thenReturn(List.of(response));
        mvc.perform(post("/api/foods").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1)).andExpect(jsonPath("$.quantity").value(60)).andExpect(jsonPath("$.reference.calories").value(206))
            .andExpect(jsonPath("$.proteinGrams").isEmpty()).andExpect(jsonPath("$.user").doesNotExist()).andExpect(jsonPath("$.deleted").doesNotExist());
        mvc.perform(get("/api/foods")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Oats"));
        mvc.perform(put("/api/foods/1").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        verify(service).update(eq(user), eq(1L), any());
        mvc.perform(delete("/api/foods/1")).andExpect(status().isNoContent());
        verify(service).delete(user, 1L);
    }
    @Test void rejectsInvalidFoodValuesAndReportsConcurrentNameConflicts() throws Exception {
        for (String invalid : List.of(BODY.replace("Oats", " "), BODY.replace("60", "0"), BODY.replace("206", "-1"), BODY.replace("60", "1.0001"), BODY.replace("GRAM", "UNKNOWN"))) {
            mvc.perform(post("/api/foods").contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
        when(service.create(eq(user), any())).thenThrow(new DataIntegrityViolationException("Duplicate normalized name"));
        mvc.perform(post("/api/foods").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isConflict());
    }
}
