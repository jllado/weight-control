package com.jllado.weightcontrol.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jllado.weightcontrol.api.dto.DishRecipeDtos.*;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.DishRecipeService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DishRecipeControllerTest {
    private final DishRecipeService service = mock(DishRecipeService.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final User user = new User();
    private MockMvc mvc;
    private static final String BODY = """
        {"name":"Rice","servings":2,"ingredients":[{"name":"Rice","quantity":100,"unit":"GRAM","calories":101,"proteinGrams":null,"carbohydrateGrams":null,"fatGrams":0}]}
        """;
    @BeforeEach void setup() {
        when(currentUser.requireUser()).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(new DishRecipeController(service, currentUser)).build();
    }
    @Test void mapsAuthenticatedRecipeWritesAndReads() throws Exception {
        when(service.create(eq(user), any())).thenAnswer(call -> { RecipeRequest request = call.getArgument(1); return new RecipeResponse(1L, request.name(), request.servings(), request.ingredients()); });
        mvc.perform(post("/api/dishes").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1)).andExpect(jsonPath("$.ingredients[0].quantity").value(100)).andExpect(jsonPath("$.ingredients[0].proteinGrams").isEmpty());
        var response = new RecipeResponse(1L, "Rice", BigDecimal.ONE, List.of());
        when(service.findAll(user)).thenReturn(List.of(response));
        when(service.find(user, 1L)).thenReturn(response);
        mvc.perform(get("/api/dishes")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Rice"));
        mvc.perform(get("/api/dishes/1")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Rice"));
        mvc.perform(put("/api/dishes/1").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        verify(service).update(eq(user), eq(1L), any());
        mvc.perform(delete("/api/dishes/1")).andExpect(status().isNoContent());
        verify(service).delete(user, 1L);
    }
    @Test void rejectsInvalidRecipesAndReportsConcurrentNameConflicts() throws Exception {
        mvc.perform(post("/api/dishes").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rice\",\"servings\":0,\"ingredients\":[]}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        when(service.create(eq(user), any())).thenThrow(new DataIntegrityViolationException("Duplicate normalized name"));
        mvc.perform(post("/api/dishes").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isConflict()).andExpect(content().string("The dish could not be saved. Check that its name is unique."));
    }
}
