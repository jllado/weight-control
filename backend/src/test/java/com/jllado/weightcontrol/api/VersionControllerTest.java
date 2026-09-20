package com.jllado.weightcontrol.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:releaseversion;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.auth.google-client-id=test-client-id", "app.push.enabled=false",
    "app.chat-gpt-actions.public-base-url=https://test.example",
    "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"
})
@AutoConfigureMockMvc
class VersionControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void exposesPackagedVersionWithoutOpeningAuthenticatedRoutes() throws Exception {
        String expected = new ClassPathResource("release-tree.txt").getContentAsString(StandardCharsets.UTF_8).strip();
        mvc.perform(get("/api/version")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.sourceTree").value(expected));
        mvc.perform(get("/api/auth/me")).andExpect(status().isForbidden());
        mvc.perform(post("/api/version")).andExpect(status().isForbidden());
    }
}
