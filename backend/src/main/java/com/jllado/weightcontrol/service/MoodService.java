package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.MoodDtos.MoodRequest;
import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MoodRepository;
import com.jllado.weightcontrol.util.DateTimes;
import com.jllado.weightcontrol.util.Numbers;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        return repository.findByUserOrderByMoodDateDesc(user).stream()
            .sorted(Comparator.comparing(Mood::getMoodDate).reversed().thenComparing(Mood::getPeriod))
            .toList();
    }

    public Mood create(User user, MoodRequest request) {
        validateDate(request.date());
        repository.findByUserAndMoodDateAndPeriod(user, request.date(), request.period()).ifPresent(mood -> {
            throw new BadRequestException("Mood already exists for this date and period");
        });
        Mood mood = new Mood();
        mood.setUser(user);
        apply(mood, request);
        return repository.save(mood);
    }

    public Mood update(User user, Long id, MoodRequest request) {
        validateDate(request.date());
        Mood mood = requireOwned(user, id);
        repository.findByUserAndMoodDateAndPeriod(user, request.date(), request.period())
            .filter(existing -> !existing.getId().equals(mood.getId()))
            .ifPresent(existing -> {
                throw new BadRequestException("Mood already exists for this date and period");
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

    public Map<LocalDate, List<Mood>> findByDateRange(User user, LocalDate startDate, LocalDate endDate) {
        return repository.findByUserAndMoodDateBetweenOrderByMoodDateAsc(user, startDate, endDate).stream()
            .sorted(Comparator.comparing(Mood::getMoodDate).thenComparing(Mood::getPeriod))
            .collect(Collectors.groupingBy(Mood::getMoodDate, LinkedHashMap::new, Collectors.toList()));
    }

    public BigDecimal getAverage(User user, LocalDate startDate, LocalDate endDate) {
        return average(repository.findByUserAndMoodDateBetweenOrderByMoodDateAsc(user, startDate, endDate));
    }

    public BigDecimal average(List<Mood> moods) {
        if (moods.isEmpty()) {
            return null;
        }
        List<BigDecimal> dailyAverages = moods.stream()
            .collect(Collectors.groupingBy(Mood::getMoodDate))
            .values().stream()
            .map(this::averageReadings)
            .toList();
        BigDecimal total = dailyAverages.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return Numbers.round(total.divide(BigDecimal.valueOf(dailyAverages.size()), 10, RoundingMode.HALF_UP));
    }

    private BigDecimal averageReadings(List<Mood> moods) {
        int total = moods.stream().mapToInt(Mood::getValue).sum();
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(moods.size()), 10, RoundingMode.HALF_UP);
    }

    private void apply(Mood mood, MoodRequest request) {
        mood.setMoodDate(request.date());
        mood.setPeriod(request.period());
        mood.setValue(request.value());
        mood.setNote(request.note());
    }

    private void validateDate(LocalDate date) {
        if (date.isAfter(LocalDate.now(DateTimes.USER_ZONE))) {
            throw new BadRequestException("Mood date cannot be in the future");
        }
    }
}
