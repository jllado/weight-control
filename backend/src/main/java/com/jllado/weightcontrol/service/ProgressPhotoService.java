package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.ProgressPhotoDtos.OpenAiFileResponse;
import com.jllado.weightcontrol.api.dto.ProgressPhotoDtos.ProgressPhotoSetResponse;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.ProgressPhotoSide;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.repository.WeightRepository;
import com.jllado.weightcontrol.util.DateTimes;
import io.jsonwebtoken.JwtException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProgressPhotoService {

    private final WeightRepository weightRepository;
    private final PhotoStorageService photoStorageService;
    private final ProgressPhotoTokenService tokenService;
    private final String publicBaseUrl;

    public ProgressPhotoService(
        WeightRepository weightRepository,
        PhotoStorageService photoStorageService,
        ProgressPhotoTokenService tokenService,
        AppProperties properties
    ) {
        this.weightRepository = weightRepository;
        this.photoStorageService = photoStorageService;
        this.tokenService = tokenService;
        this.publicBaseUrl = StringUtils.trimTrailingCharacter(
            properties.chatGptActions().publicBaseUrl(),
            '/'
        );
    }

    public List<ProgressPhotoSetResponse> findAll(User user) {
        return toResponses(weightRepository.findByUserOrderByMeasuredAtDesc(user));
    }

    public List<ProgressPhotoSetResponse> findBetween(User user, LocalDate from, LocalDate to) {
        return toResponses(weightRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(
            user,
            DateTimes.startOfDay(from),
            DateTimes.startOfDay(to).plusDays(1)
        ));
    }

    public OpenAiFileResponse getFiles(User user, long photoSetId, Set<ProgressPhotoSide> sides) {
        if (sides.isEmpty()) {
            throw new BadRequestException("At least one progress photo side is required");
        }
        Weight weight = requireOwned(user, photoSetId);
        List<String> urls = Arrays.stream(ProgressPhotoSide.values())
            .filter(sides::contains)
            .map(side -> signedUrl(user, weight, side))
            .toList();
        return new OpenAiFileResponse(urls);
    }

    public ProgressPhotoFile getFile(String token) {
        try {
            ProgressPhotoTokenService.ProgressPhotoToken claims = tokenService.parse(token);
            Weight weight = weightRepository.findById(claims.photoSetId())
                .orElseThrow(() -> new NotFoundException("Progress photo not found"));
            if (!weight.getUser().getId().equals(claims.userId())) {
                throw new NotFoundException("Progress photo not found");
            }
            String path = requireSide(weight, claims.side());
            Resource resource = photoStorageService.load(path);
            MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
            String extension = StringUtils.getFilenameExtension(resource.getFilename());
            String filename = "progress-photo-" + DateTimes.toLocalDate(weight.getMeasuredAt()) + "-" + claims.side().fileLabel()
                + (extension == null ? "" : "." + extension);
            return new ProgressPhotoFile(resource, mediaType, filename);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new NotFoundException("Progress photo not found");
        }
    }

    private List<ProgressPhotoSetResponse> toResponses(List<Weight> weights) {
        return weights.stream()
            .filter(this::hasPhoto)
            .map(ProgressPhotoSetResponse::from)
            .toList();
    }

    private boolean hasPhoto(Weight weight) {
        return Arrays.stream(ProgressPhotoSide.values()).anyMatch(side -> side.path(weight) != null);
    }

    private String signedUrl(User user, Weight weight, ProgressPhotoSide side) {
        requireSide(weight, side);
        return publicBaseUrl + "/api/chatgpt-files/progress-photos/" + tokenService.create(user.getId(), weight.getId(), side);
    }

    private String requireSide(Weight weight, ProgressPhotoSide side) {
        String path = side.path(weight);
        if (path == null) {
            throw new NotFoundException("Progress photo not found");
        }
        return path;
    }

    private Weight requireOwned(User user, long photoSetId) {
        Weight weight = weightRepository.findById(photoSetId)
            .orElseThrow(() -> new NotFoundException("Progress photo set not found"));
        if (!weight.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Progress photo set not found");
        }
        return weight;
    }

    public record ProgressPhotoFile(Resource resource, MediaType mediaType, String filename) {
    }
}
