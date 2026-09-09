package com.jllado.weightcontrol.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.CoachDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class CoachWarningService {
    private final CoachWarningRepository warnings;
    private final CoachWarningRevisionRepository revisions;
    private final EntityManager entityManager;
    private final ObjectMapper mapper;

    public CoachWarningService(CoachWarningRepository warnings, CoachWarningRevisionRepository revisions, EntityManager entityManager, ObjectMapper mapper) {
        this.warnings = warnings;
        this.revisions = revisions;
        this.entityManager = entityManager;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public WarningReadResponse read(User user, WarningView view, Long id, int page) {
        pageRequest(page);
        if (view == WarningView.REVISIONS && id == null) throw new BadRequestException("Warning id is required for revisions");
        var overview = overview(user);
        if (view == WarningView.ACTIVE) return new WarningReadResponse(overview.active(), 0, false, overview.hasHistory());
        var result = view == WarningView.HISTORY ? history(user, page) : revisions(user, id, page);
        return new WarningReadResponse(result.items(), result.page(), result.hasMore(), overview.hasHistory());
    }

    public WarningResponse write(User user, WarningWriteRequest request) {
        if (request.create() != null) return create(user, request.create());
        if (request.update() != null) return update(user, request.id(), request.update());
        return resolve(user, request.id(), request.resolve());
    }

    @Transactional(readOnly = true)
    public WarningOverview overview(User user) {
        return new WarningOverview(warnings.findByUserAndStatusOrderByTypeAsc(user, CoachWarningStatus.ACTIVE).stream().map(WarningResponse::from).toList(),
            warnings.findByUserAndStatusOrderByUpdatedAtDescIdDesc(user, CoachWarningStatus.RESOLVED, PageRequest.of(0, 1)).hasContent());
    }

    @Transactional(readOnly = true)
    public WarningPage history(User user, int page) {
        var result = warnings.findByUserAndStatusOrderByUpdatedAtDescIdDesc(user, CoachWarningStatus.RESOLVED, pageRequest(page));
        return new WarningPage(result.map(WarningResponse::from).toList(), page, result.hasNext());
    }

    @Transactional(readOnly = true)
    public WarningPage revisions(User user, Long id, int page) {
        owned(user, id);
        var result = revisions.findByWarningIdOrderByVersionDesc(id, pageRequest(page));
        return new WarningPage(result.map(revision -> decode(revision.getSnapshot())).toList(), page, result.hasNext());
    }

    public WarningResponse create(User user, CreateWarningRequest request) {
        lock(user);
        String payload = encode(request);
        var existing = warnings.findByUserAndRequestKey(user, request.requestKey().toString());
        if (existing.isPresent()) {
            if (!existing.get().getCreatePayload().equals(payload)) throw conflict("Request key already used for different warning values");
            return WarningResponse.from(existing.get());
        }
        if (warnings.existsByUserAndTypeAndStatus(user, request.type(), CoachWarningStatus.ACTIVE)) throw conflict("This warning type is already active; retrieve and update it");
        CoachWarning warning = new CoachWarning();
        warning.setUser(user);
        warning.setType(request.type());
        warning.setStatus(CoachWarningStatus.ACTIVE);
        warning.setRequestKey(request.requestKey().toString());
        warning.setCreatePayload(payload);
        warning.setCreatedAt(Instant.now());
        apply(warning, request.content());
        return save(warning);
    }

    public WarningResponse update(User user, Long id, UpdateWarningRequest request) {
        lock(user);
        CoachWarning warning = editable(user, id, request.version());
        apply(warning, request.content());
        warning.setVersion(warning.getVersion() + 1);
        return save(warning);
    }

    public WarningResponse resolve(User user, Long id, ResolveWarningRequest request) {
        lock(user);
        CoachWarning warning = editable(user, id, request.version());
        validateReview(warning, request.reviewedDate());
        warning.setReviewedDate(request.reviewedDate());
        warning.setStatus(CoachWarningStatus.RESOLVED);
        warning.setResolutionRationale(request.rationale());
        warning.setResolvedAt(Instant.now());
        warning.setVersion(warning.getVersion() + 1);
        return save(warning);
    }

    private void lock(User user) {
        entityManager.find(User.class, user.getId(), LockModeType.PESSIMISTIC_WRITE);
    }

    private CoachWarning owned(User user, Long id) {
        return warnings.findByUserAndId(user, id).orElseThrow(() -> new NotFoundException("Coach warning not found"));
    }

    private CoachWarning editable(User user, Long id, long version) {
        CoachWarning warning = owned(user, id);
        if (warning.getVersion() != version || warning.getStatus() != CoachWarningStatus.ACTIVE) throw conflict("Warning changed or resolved; retrieve current warnings before reviewing again");
        return warning;
    }

    private void apply(CoachWarning warning, WarningContent content) {
        validateReview(warning, content.reviewedDate());
        if (content.onsetDate() != null && content.onsetDate().isAfter(content.reviewedDate())) throw new BadRequestException("Onset must not follow the review date");
        warning.setExplanation(content.explanation());
        warning.setEvidence(content.evidence());
        warning.setAction(content.action());
        warning.setOnsetDate(content.onsetDate());
        warning.setReviewedDate(content.reviewedDate());
    }

    private void validateReview(CoachWarning warning, LocalDate date) {
        if (warning.getReviewedDate() != null && date.isBefore(warning.getReviewedDate())) throw conflict("Review date must not move backwards");
    }

    private WarningResponse save(CoachWarning warning) {
        warning.setUpdatedAt(Instant.now());
        warnings.saveAndFlush(warning);
        WarningResponse response = WarningResponse.from(warning);
        CoachWarningRevision revision = new CoachWarningRevision();
        revision.setWarning(warning);
        revision.setVersion(warning.getVersion());
        revision.setSnapshot(encode(response));
        revisions.save(revision);
        return response;
    }

    private PageRequest pageRequest(int page) {
        if (page < 0) throw new BadRequestException("Page must not be negative");
        return PageRequest.of(page, 10);
    }

    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }

    private String encode(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize warning", exception); }
    }

    private WarningResponse decode(String value) {
        try { return mapper.readValue(value, WarningResponse.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot read warning revision", exception); }
    }
}
