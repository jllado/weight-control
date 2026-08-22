package com.jllado.weightcontrol.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jllado.weightcontrol.service.ProgressPhotoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ChatGptFileControllerTest {

    @Mock
    private ProgressPhotoService progressPhotoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatGptFileController(progressPhotoService)).build();
    }

    @Test
    void servesSignedPhotosWithoutCaching() throws Exception {
        when(progressPhotoService.getFile("signed-token")).thenReturn(new ProgressPhotoService.ProgressPhotoFile(
            new ByteArrayResource(new byte[]{1, 2, 3}),
            MediaType.IMAGE_PNG,
            "progress-photo-2026-08-20-left.png"
        ));

        mockMvc.perform(get("/api/chatgpt-files/progress-photos/signed-token"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_PNG))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"progress-photo-2026-08-20-left.png\""));
    }
}
