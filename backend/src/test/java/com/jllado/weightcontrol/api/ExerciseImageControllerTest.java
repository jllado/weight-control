package com.jllado.weightcontrol.api;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.jllado.weightcontrol.config.SecurityConfig;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.security.JwtSessionService;
import com.jllado.weightcontrol.security.SessionCookieService;
import com.jllado.weightcontrol.service.CoachAuthAlertService;
import com.jllado.weightcontrol.service.ExerciseImageService;
import com.jllado.weightcontrol.service.ExerciseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExerciseController.class)
@Import(SecurityConfig.class)
class ExerciseImageControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ExerciseService exercises;
    @MockitoBean ExerciseImageService images;
    @MockitoBean JwtSessionService sessions;
    @MockitoBean SessionCookieService cookies;
    @MockitoBean UserRepository users;
    @MockitoBean CoachAuthAlertService alerts;

    @Test void requiresAuthenticationForEveryPictureOperation() throws Exception {
        mvc.perform(get("/api/workout-exercises/1/image")).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/workout-exercises/1/image").file(new MockMultipartFile("file", new byte[]{1}))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/workout-exercises/1/image")).andExpect(status().isForbidden());
        verifyNoInteractions(images);
    }

    @Test void returnsImageBytesAndOnlyPublicMetadata() throws Exception {
        when(images.load(1L)).thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));
        mvc.perform(get("/api/workout-exercises/1/image").with(user("owner")))
            .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
            .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().bytes(new byte[]{1, 2, 3}));
        Exercise exercise = new Exercise(); exercise.setId(1L); exercise.setCustomImagePath("exercise-images/1/new.jpg");
        when(images.replace(eq(1L), any())).thenReturn(exercise);
        mvc.perform(multipart("/api/workout-exercises/1/image").file(new MockMultipartFile("file", new byte[]{1})).with(user("owner")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl").value("/api/workout-exercises/1/image?v=new.jpg"))
            .andExpect(jsonPath("$.hasCustomImage").value(true)).andExpect(jsonPath("$.customImagePath").doesNotExist());
        exercise.setCustomImagePath(null); exercise.setBuiltInImageKey("push-up");
        when(images.remove(1L)).thenReturn(exercise);
        mvc.perform(delete("/api/workout-exercises/1/image").with(user("owner")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hasCustomImage").value(false))
            .andExpect(jsonPath("$.builtInImageKey").doesNotExist());
    }
}
