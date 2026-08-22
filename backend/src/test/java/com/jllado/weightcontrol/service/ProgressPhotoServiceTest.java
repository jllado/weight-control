package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.ProgressPhotoSide;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.repository.WeightRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;

@ExtendWith(MockitoExtension.class)
class ProgressPhotoServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");

    @Mock
    private WeightRepository weightRepository;
    @Mock
    private PhotoStorageService photoStorageService;

    private AppProperties properties;
    private ProgressPhotoTokenService tokenService;
    private ProgressPhotoService service;
    private User user;
    private Weight weight;

    @BeforeEach
    void setUp() {
        properties = properties();
        tokenService = new ProgressPhotoTokenService(properties, Clock.fixed(NOW, ZoneOffset.UTC));
        service = new ProgressPhotoService(weightRepository, photoStorageService, tokenService, properties);
        user = user(1L);
        weight = weight(user, 20L);
    }

    @Test
    void listsOnlyPhotoSetsWithoutExposingStoragePaths() {
        Weight withoutPhotos = weight(user, 21L);
        withoutPhotos.setPhotoFrontPath(null);
        withoutPhotos.setPhotoLeftPath(null);
        when(weightRepository.findByUserOrderByMeasuredAtDesc(user)).thenReturn(List.of(weight, withoutPhotos));

        var response = service.findAll(user);

        assertEquals(1, response.size());
        assertEquals(20L, response.getFirst().photoSetId());
        assertEquals(new BigDecimal("80.00"), response.getFirst().weightKg());
        assertEquals(List.of(ProgressPhotoSide.FRONT, ProgressPhotoSide.LEFT), response.getFirst().availableSides());
    }

    @Test
    void signsSelectedExistingSidesInStableOrder() {
        when(weightRepository.findById(20L)).thenReturn(Optional.of(weight));

        var response = service.getFiles(user, 20L, Set.of(ProgressPhotoSide.LEFT, ProgressPhotoSide.FRONT));

        assertEquals(2, response.openaiFileResponse().size());
        assertTrue(response.openaiFileResponse().getFirst().startsWith("https://weightcontrol.test/api/chatgpt-files/progress-photos/"));
        assertEquals(ProgressPhotoSide.FRONT, tokenService.parse(token(response.openaiFileResponse().getFirst())).side());
        assertEquals(ProgressPhotoSide.LEFT, tokenService.parse(token(response.openaiFileResponse().get(1))).side());
    }

    @Test
    void rejectsEmptyMissingAndUnownedSelections() {
        assertThrows(BadRequestException.class, () -> service.getFiles(user, 20L, Set.of()));
        when(weightRepository.findById(20L)).thenReturn(Optional.of(weight));
        assertThrows(NotFoundException.class, () -> service.getFiles(user, 20L, Set.of(ProgressPhotoSide.RIGHT)));
        assertThrows(NotFoundException.class, () -> service.getFiles(user(2L), 20L, Set.of(ProgressPhotoSide.FRONT)));
    }

    @Test
    void downloadsAValidOwnedPhotoWithItsMimeType() {
        when(weightRepository.findById(20L)).thenReturn(Optional.of(weight));
        when(photoStorageService.load("private/front.jpg")).thenReturn(resource("front.jpg"));

        var response = service.getFile(tokenService.create(1L, 20L, ProgressPhotoSide.FRONT));

        assertEquals(MediaType.IMAGE_JPEG, response.mediaType());
        assertEquals("progress-photo-2026-08-20-front.jpg", response.filename());
    }

    @Test
    void rejectsExpiredTamperedWrongPurposeWrongUserAndMissingSideTokens() {
        String valid = tokenService.create(1L, 20L, ProgressPhotoSide.FRONT);
        ProgressPhotoTokenService expiredTokenService = new ProgressPhotoTokenService(
            properties,
            Clock.fixed(NOW.plusSeconds(301), ZoneOffset.UTC)
        );
        ProgressPhotoService expiredService = new ProgressPhotoService(
            weightRepository,
            photoStorageService,
            expiredTokenService,
            properties
        );
        assertThrows(NotFoundException.class, () -> expiredService.getFile(valid));
        assertThrows(NotFoundException.class, () -> service.getFile(valid + "x"));
        assertThrows(NotFoundException.class, () -> service.getFile(
            tokenService.create(1L, 20L, ProgressPhotoSide.FRONT, "another-purpose")
        ));

        when(weightRepository.findById(20L)).thenReturn(Optional.of(weight));
        assertThrows(NotFoundException.class, () -> service.getFile(tokenService.create(2L, 20L, ProgressPhotoSide.FRONT)));
        assertThrows(NotFoundException.class, () -> service.getFile(tokenService.create(1L, 20L, ProgressPhotoSide.RIGHT)));
    }

    private String token(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private ByteArrayResource resource(String filename) {
        return new ByteArrayResource(new byte[]{1, 2, 3}) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private User user(long id) {
        User value = new User();
        value.setId(id);
        return value;
    }

    private Weight weight(User owner, long id) {
        Weight value = new Weight();
        value.setId(id);
        value.setUser(owner);
        value.setMeasuredAt(OffsetDateTime.parse("2026-08-20T08:00:00+02:00"));
        value.setWeight(new BigDecimal("80.00"));
        value.setFatPercentage(new BigDecimal("20.00"));
        value.setFat(new BigDecimal("16.00"));
        value.setMuscle(new BigDecimal("60.00"));
        value.setMusclePercentage(new BigDecimal("75.00"));
        value.setPhotoFrontPath("private/front.jpg");
        value.setPhotoLeftPath("private/left.png");
        return value;
    }

    private AppProperties properties() {
        return new AppProperties(
            new AppProperties.Auth("client", "test-jwt-secret-test-jwt-secret", 7, false),
            new AppProperties.Cors(List.of()),
            new AppProperties.Storage(Path.of("data")),
            new AppProperties.ChatGptActions(
                "action-token",
                "owner@example.com",
                "https://weightcontrol.test/",
                "test-file-signing-secret-32-bytes-long"
            ),
            new AppProperties.Push(false, "", "", "mailto:test@example.com", ""),
            new AppProperties.WeeklySummary(false, "", "", "", "")
        );
    }
}
