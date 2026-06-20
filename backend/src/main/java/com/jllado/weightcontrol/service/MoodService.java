package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.MoodDtos.MoodRequest;
import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MoodRepository;
import com.jllado.weightcontrol.util.DateTimes;
import com.jllado.weightcontrol.util.Numbers;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class MoodService {

    private final MoodRepository repository;

    public MoodService(MoodRepository repository) {
        this.repository = repository;
    }

    public List<Mood> findAll(User user) {
        return repository.findByUserOrderByMoodDateDesc(user);
    }

    public Mood create(User user, MoodRequest request) {
        validateDate(request.date());
        repository.findByUserAndMoodDate(user, request.date()).ifPresent(mood -> {
            throw new BadRequestException("Mood already exists for this date");
        });
        Mood mood = new Mood();
        mood.setUser(user);
        apply(mood, request);
        return repository.save(mood);
    }

    public Mood update(User user, Long id, MoodRequest request) {
        validateDate(request.date());
        Mood mood = requireOwned(user, id);
        repository.findByUserAndMoodDate(user, request.date())
            .filter(existing -> !existing.getId().equals(mood.getId()))
            .ifPresent(existing -> {
                throw new BadRequestException("Mood already exists for this date");
            });
        apply(mood, request);
        return repository.save(mood);
    }

    public void delete(User user, Long id) {
        repository.delete(requireOwned(user, id));
    }

    public Mood requireOwned(User user, Long id) {
        Mood mood = repository.findById(id).orElseThrow(() -> new NotFoundException("Mood not found"));
        if (!mood.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Mood not found");
        }
        return mood;
    }

    public Map<LocalDate, Mood> findByDateRange(User user, LocalDate startDate, LocalDate endDate) {
        return repository.findByUserAndMoodDateBetweenOrderByMoodDateAsc(user, startDate, endDate).stream()
            .collect(Collectors.toMap(Mood::getMoodDate, Function.identity()));
    }

    public BigDecimal getAverage(User user, LocalDate startDate, LocalDate endDate) {
        return average(repository.findByUserAndMoodDateBetweenOrderByMoodDateAsc(user, startDate, endDate));
    }

    public BigDecimal average(List<Mood> moods) {
        if (moods.isEmpty()) {
            return null;
        }
        BigDecimal total = moods.stream()
            .map(Mood::getValue)
            .map(BigDecimal::valueOf)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Numbers.round(total.divide(BigDecimal.valueOf(moods.size()), 2, java.math.RoundingMode.HALF_UP));
    }

    private void apply(Mood mood, MoodRequest request) {
        mood.setMoodDate(request.date());
        mood.setValue(request.value());
        mood.setNote(request.note());
    }

    private void validateDate(LocalDate date) {
        if (date.isAfter(LocalDate.now(DateTimes.USER_ZONE))) {
            throw new BadRequestException("Mood date cannot be in the future");
        }
    }
}
