package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.BackStatusDtos.BackRegionStatus;
import com.jllado.weightcontrol.api.dto.BackStatusDtos.BackStatusRequest;
import com.jllado.weightcontrol.domain.BackStatus;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.BackStatusRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class BackStatusService {

    private final BackStatusRepository repository;

    public BackStatusService(BackStatusRepository repository) {
        this.repository = repository;
    }

    public List<BackStatus> findAll(User user) {
        return repository.findByUserOrderByStatusDateDesc(user);
    }

    public BackStatus create(User user, BackStatusRequest request) {
        validateDate(request.date());
        repository.findByUserAndStatusDate(user, request.date()).ifPresent(status -> {
            throw new BadRequestException("Back status already exists for this date");
        });
        BackStatus status = new BackStatus();
        status.setUser(user);
        apply(status, request);
        return repository.save(status);
    }

    public BackStatus update(User user, Long id, BackStatusRequest request) {
        validateDate(request.date());
        BackStatus status = requireOwned(user, id);
        repository.findByUserAndStatusDate(user, request.date())
            .filter(existing -> !existing.getId().equals(status.getId()))
            .ifPresent(existing -> {
                throw new BadRequestException("Back status already exists for this date");
            });
        apply(status, request);
        return repository.save(status);
    }

    public void delete(User user, Long id) {
        repository.delete(requireOwned(user, id));
    }

    public BackStatus requireOwned(User user, Long id) {
        BackStatus status = repository.findById(id).orElseThrow(() -> new NotFoundException("Back status not found"));
        if (!status.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Back status not found");
        }
        return status;
    }

    private void apply(BackStatus status, BackStatusRequest request) {
        status.setStatusDate(request.date());
        applyLower(status, request.lower());
        applyMiddle(status, request.middle());
        applyUpper(status, request.upper());
        status.setNote(request.note());
    }

    private void applyLower(BackStatus status, BackRegionStatus region) {
        status.setLowerPain(region.pain());
        status.setLowerStiffness(region.stiffness());
        status.setLowerActivityLimitation(region.activityLimitation());
    }

    private void applyMiddle(BackStatus status, BackRegionStatus region) {
        status.setMiddlePain(region.pain());
        status.setMiddleStiffness(region.stiffness());
        status.setMiddleActivityLimitation(region.activityLimitation());
    }

    private void applyUpper(BackStatus status, BackRegionStatus region) {
        status.setUpperPain(region.pain());
        status.setUpperStiffness(region.stiffness());
        status.setUpperActivityLimitation(region.activityLimitation());
    }

    private void validateDate(LocalDate date) {
        if (date.isAfter(LocalDate.now(DateTimes.USER_ZONE))) {
            throw new BadRequestException("Back status date cannot be in the future");
        }
    }
}
