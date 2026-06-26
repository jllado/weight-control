package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.CalorieDtos.CalorieRequest;
import com.jllado.weightcontrol.domain.Calorie;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.CalorieRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class CalorieService {

    private final CalorieRepository repository;

    public CalorieService(CalorieRepository repository) {
        this.repository = repository;
    }

    public List<Calorie> findAll(User user) {
        return repository.findByUserOrderByCalorieDateDesc(user);
    }

    public Calorie create(User user, CalorieRequest request) {
        validateDate(request.date());
        repository.findByUserAndCalorieDate(user, request.date()).ifPresent(existing -> {
            throw new BadRequestException("Calorie entry already exists for this date");
        });
        Calorie calorie = new Calorie();
        calorie.setUser(user);
        apply(calorie, request);
        return repository.save(calorie);
    }

    public Calorie update(User user, Long id, CalorieRequest request) {
        validateDate(request.date());
        Calorie calorie = requireOwned(user, id);
        repository.findByUserAndCalorieDate(user, request.date())
            .filter(existing -> !existing.getId().equals(calorie.getId()))
            .ifPresent(existing -> {
                throw new BadRequestException("Calorie entry already exists for this date");
            });
        apply(calorie, request);
        return repository.save(calorie);
    }

    public void delete(User user, Long id) {
        repository.delete(requireOwned(user, id));
    }

    public Calorie requireOwned(User user, Long id) {
        Calorie calorie = repository.findById(id).orElseThrow(() -> new NotFoundException("Calorie entry not found"));
        if (!calorie.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Calorie entry not found");
        }
        return calorie;
    }

    private void apply(Calorie calorie, CalorieRequest request) {
        calorie.setCalorieDate(request.date());
        calorie.setCalories(request.calories());
    }

    private void validateDate(LocalDate date) {
        if (date.isAfter(LocalDate.now(DateTimes.USER_ZONE))) {
            throw new BadRequestException("Calorie date cannot be in the future");
        }
    }
}
