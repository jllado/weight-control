package com.jllado.weightcontrol;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jllado.weightcontrol.service.ProgressPhotoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.flyway.enabled=false",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"app.auth.google-client-id=test-client-id",
	"app.chat-gpt-actions.public-base-url=https://test.example",
	"app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"
})
@AutoConfigureMockMvc
class WeightControlBackendApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProgressPhotoService progressPhotoService;

	@Test
	void contextLoads() {
	}

	@Test
	void signedProgressPhotoRouteIsPublicWhileCoachActionsRequireAuthentication() throws Exception {
		when(progressPhotoService.getFile("signed-token")).thenReturn(new ProgressPhotoService.ProgressPhotoFile(
			new ByteArrayResource(new byte[]{1}),
			MediaType.IMAGE_JPEG,
			"progress-photo-front.jpg"
		));

		mockMvc.perform(get("/api/chatgpt-files/progress-photos/signed-token"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/chatgpt-actions/coach/progress-photos"))
			.andExpect(status().isUnauthorized());
	}

}
