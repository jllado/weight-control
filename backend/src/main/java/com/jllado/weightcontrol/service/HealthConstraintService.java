package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.HealthConstraintDtos.CoachHealthConstraintRequest;
import com.jllado.weightcontrol.api.dto.HealthConstraintDtos.HealthConstraintRequest;
import com.jllado.weightcontrol.domain.HealthConstraint;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.HealthConstraintRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class HealthConstraintService {

    private final HealthConstraintRepository repository;

    public HealthConstraintService(HealthConstraintRepository repository) {
        this.repository = repository;
    }

    public List<HealthConstraint> findAll(User user) {
        return repository.findByUserOrderByActiveDescStartDateDescIdDesc(user);
    }

    public List<HealthConstraint> findActiveOverlapping(User user, LocalDate from, LocalDate to) {
        return repository.findActiveOverlapping(user, from, to);
    }

    public HealthConstraint create(User user, HealthConstraintRequest request) {
        validateDates(request.startDate(), request.endDate());
        HealthConstraint constraint = new HealthConstraint();
        constraint.setUser(user);
        apply(constraint, request);
        return repository.save(constraint);
    }

    public HealthConstraint update(User user, Long id, HealthConstraintRequest request) {
        validateDates(request.startDate(), request.endDate());
        HealthConstraint constraint = requireOwned(user, id);
        apply(constraint, request);
        return repository.save(constraint);
    }

    public HealthConstraint createConfirmed(User user, CoachHealthConstraintRequest request) {
        requireConfirmation(request.confirmed());
        return create(user, request.constraint());
    }

    public HealthConstraint updateConfirmed(User user, Long id, CoachHealthConstraintRequest request) {
        requireConfirmation(request.confirmed());
        return update(user, id, request.constraint());
    }

    public void delete(User user, Long id) {
        repository.delete(requireOwned(user, id));
    }

    private HealthConstraint requireOwned(User user, Long id) {
        HealthConstraint constraint = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Health constraint not found"));
        if (!constraint.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Health constraint not found");
        }
        return constraint;
    }

    private void apply(HealthConstraint constraint, HealthConstraintRequest request) {
        constraint.setType(request.type());
        constraint.setTitle(request.title());
        constraint.setDetails(request.details());
        constraint.setSource(request.source());
        constraint.setStartDate(request.startDate());
        constraint.setEndDate(request.endDate());
        constraint.setActive(request.active());
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && startDate.isAfter(endDate)) {
            throw new BadRequestException("Health constraint start date must not be after the end date");
        }
    }

    private void requireConfirmation(boolean confirmed) {
        if (!confirmed) {
            throw new BadRequestException("Health constraint write requires explicit confirmation");
        }
    }
}
