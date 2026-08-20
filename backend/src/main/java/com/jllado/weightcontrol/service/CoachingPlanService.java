package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.CoachingPlanDtos.CoachCoachingPlanRequest;
import com.jllado.weightcontrol.api.dto.CoachingPlanDtos.CoachingPlanRequest;
import com.jllado.weightcontrol.domain.CoachingPlan;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.CoachingPlanRepository;
import jakarta.transaction.Transactional;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class CoachingPlanService {

    private final CoachingPlanRepository repository;

    public CoachingPlanService(CoachingPlanRepository repository) {
        this.repository = repository;
    }

    public Optional<CoachingPlan> find(User user) {
        return repository.findByUser(user);
    }

    public CoachingPlan replace(User user, CoachingPlanRequest request) {
        validateDates(request);
        CoachingPlan plan = repository.findByUser(user).orElseGet(() -> {
            CoachingPlan created = new CoachingPlan();
            created.setUser(user);
            return created;
        });
        plan.setGoal(request.goal());
        plan.setPrinciples(request.principles());
        plan.setPriorities(request.priorities());
        plan.setActions(request.actions());
        plan.setStartDate(request.startDate());
        plan.setReviewDate(request.reviewDate());
        plan.setNotes(request.notes());
        return repository.save(plan);
    }

    public CoachingPlan replaceConfirmed(User user, CoachCoachingPlanRequest request) {
        if (!request.confirmed()) {
            throw new BadRequestException("Coaching plan write requires explicit confirmation");
        }
        return replace(user, request.plan());
    }

    private void validateDates(CoachingPlanRequest request) {
        if (request.reviewDate() != null && request.startDate().isAfter(request.reviewDate())) {
            throw new BadRequestException("Coaching plan start date must not be after the review date");
        }
    }
}
