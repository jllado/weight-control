package com.jllado.weightcontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.security.AuthenticatedUser;
import com.jllado.weightcontrol.security.JwtSessionService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.*;
import com.jllado.weightcontrol.service.WeightService;
import com.jllado.weightcontrol.api.dto.WeightDtos.WeightRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:multipart;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "app.auth.google-client-id=test-client-id",
    "app.auth.jwt-secret=test-secret-test-secret-test-secret-test-secret",
    "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long",
    "app.coach-auth-alerts.enabled=false", "app.push.enabled=false", "app.weekly-summary.enabled=false",
    "spring.servlet.multipart.max-file-size=1KB", "spring.servlet.multipart.max-request-size=2KB"
})
@ExtendWith(OutputCaptureExtension.class)
class MultipartRequestIntegrationTest {
    private static final String BOUNDARY = "test-boundary";
    private static final String MULTIPART = "multipart/form-data; boundary=" + BOUNDARY;
    @LocalServerPort int port;
    @Autowired JwtSessionService sessions;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ExerciseRepository exercises;
    @Autowired WeightRepository weights;
    @Autowired WeightService weightService;
    @TempDir static Path storage;
    private User owner;
    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) { registry.add("app.storage.root", storage::toString); }

    @BeforeEach
    void createOwner() {
        owner = new User(); owner.setEmail(UUID.randomUUID() + "@test.example");
        owner = users.save(owner);
    }

    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void noSecondaryServletFailure(CapturedOutput output) {
        assertThat(output.getAll()).doesNotContain("Exception Processing [ErrorPage", "Servlet.service() for servlet");
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "text/html"})
    void authenticationRejectionDoesNotParseMalformedBody(String accept) throws Exception {
        var response = send("POST", "/api/workout-exercises/1/image", "multipart/form-data", "invalid", false, accept);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).doesNotContain("HTTP Status 403", "Exception", "boundary");
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "text/html"})
    void missingBoundaryReturnsBadRequest(String accept) throws Exception {
        assertError(send("POST", "/api/workout-exercises/1/image", "multipart/form-data", "invalid", true, accept), 400, "Malformed multipart request");
    }

    @Test
    void truncatedBodyReturnsBadRequest() throws Exception {
        assertError(send("POST", "/api/workout-exercises/1/image", MULTIPART, part("file", "hello"), true, "application/json"), 400, "Malformed multipart request");
    }

    @Test
    void fileLimitReturnsPayloadTooLarge() throws Exception {
        assertError(send("POST", "/api/workout-exercises/1/image", MULTIPART, multipart("x".repeat(1100)), true, "application/json"), 413, "Multipart upload exceeds the configured limit");
    }

    @Test
    void requestLimitReturnsPayloadTooLarge() throws Exception {
        String body = part("file", "x".repeat(1000)) + part("other", "x".repeat(1000)) + "--" + BOUNDARY + "--\r\n";
        assertError(send("POST", "/api/workout-exercises/1/image", MULTIPART, body, true, "application/json"), 413, "Multipart upload exceeds the configured limit");
    }

    @Test
    void validExerciseImageIsSavedAndReadable() throws Exception {
        Exercise exercise = new Exercise(); exercise.setName("Multipart " + UUID.randomUUID());
        exercise.setDescription("Upload test"); exercise.setExerciseType(ExerciseType.TRAINING); exercise.setTrackingMode(ExerciseTrackingMode.REPS);
        exercise = exercises.save(exercise);
        String path = "/api/workout-exercises/" + exercise.getId() + "/image";
        var response = send("POST", path, MULTIPART, multipart(png()), true, "application/json");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("hasCustomImage").asBoolean()).isTrue();
        assertThat(exercises.findById(exercise.getId()).orElseThrow().getCustomImagePath()).isNotBlank();
        assertThat(send("GET", path, null, "", true, "image/jpeg").statusCode()).isEqualTo(200);
    }

    @Test
    void validProgressPhotoIsSavedAndRemainsOwnerScoped() throws Exception {
        var weight = weightService.create(owner, new WeightRequest(OffsetDateTime.parse("2026-10-28T08:00:00Z"), new BigDecimal("75"), new BigDecimal("20"), new BigDecimal("55")));
        String path = "/api/weights/" + weight.getId() + "/photos/front";
        String picture = png();
        assertThat(send("POST", path, MULTIPART, multipart(picture), true, "application/json").statusCode()).isEqualTo(200);
        assertThat(weights.findById(weight.getId()).orElseThrow().getPhotoFrontPath()).isNotBlank();
        assertThat(send("GET", path, null, "", true, "*/*").statusCode()).isEqualTo(200);
        assertThat(send("POST", path, MULTIPART, multipart(picture), false, "application/json").statusCode()).isEqualTo(403);
        createOwner();
        assertThat(send("POST", path, MULTIPART, multipart(picture), true, "application/json").statusCode()).isEqualTo(404);
    }

    private String png() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "PNG", bytes);
        return bytes.toString(StandardCharsets.ISO_8859_1);
    }

    @Test
    void unsupportedMethodKeepsItsStatus() throws Exception {
        var response = send("PUT", "/api/workout-exercises/1/image", "multipart/form-data", "invalid", true, "application/json");
        assertThat(response.statusCode()).isEqualTo(405);
    }

    @Test
    void invalidJsonKeepsItsStatus() throws Exception {
        var response = send("POST", "/api/auth/google", "application/json", "{", false, "application/json");
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void normalAuthenticatedRequestStillWorks() throws Exception {
        var response = send("GET", "/api/workout-exercises", null, "", true, "application/json");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).isArray()).isTrue();
    }

    private void assertError(HttpResponse<String> response, int status, String message) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        var body = json.readTree(response.body());
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("message").asText()).isEqualTo(message);
        assertThat(body.get("path").asText()).isEqualTo("/api/workout-exercises/1/image");
        assertThat(body.get("timestamp").asText()).isNotBlank();
        assertThat(body.get("error").asText()).isEqualTo(status == 400 ? "Bad Request" : "Payload Too Large");
        assertThat(response.body()).doesNotContain("Exception", "org.apache", "org.springframework");
    }

    private HttpResponse<String> send(String method, String path, String contentType, String body, boolean authenticated, String accept) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(10)).header("Accept", accept);
        if (contentType != null) request.header("Content-Type", contentType);
        if (authenticated) request.header("Cookie", "wc_session=" + sessions.createToken(new AuthenticatedUser(owner.getId(), owner.getEmail())));
        return client.send(request.method(method, HttpRequest.BodyPublishers.ofByteArray(body.getBytes(StandardCharsets.ISO_8859_1))).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String part(String name, String content) {
        return "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"" + name + "\"; filename=\"test.png\"\r\nContent-Type: image/png\r\n\r\n" + content + "\r\n";
    }

    private String multipart(String content) { return part("file", content) + "--" + BOUNDARY + "--\r\n"; }

}
