package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WeightDtos.WeightRequest;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.repository.WeightRepository;
import com.jllado.weightcontrol.repository.DailyStatusRepository;
import com.jllado.weightcontrol.util.DateTimes;
import com.jllado.weightcontrol.util.Numbers;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class WeightService {

    private final WeightRepository weightRepository;
    private final DailyStatusRepository dailyStatusRepository;
    private final PhotoStorageService photoStorageService;

    public WeightService(WeightRepository weightRepository, DailyStatusRepository dailyStatusRepository, PhotoStorageService photoStorageService) {
        this.weightRepository = weightRepository;
        this.dailyStatusRepository = dailyStatusRepository;
        this.photoStorageService = photoStorageService;
    }

    public List<Weight> findAll(User user) {
        return weightRepository.findByUserOrderByMeasuredAtDesc(user);
    }

    public Weight create(User user, WeightRequest request) {
        Weight weight = new Weight();
        weight.setUser(user);
        apply(weight, request);
        weightRepository.save(weight);
        recalculateAround(weight);
        return weight;
    }

    public Weight update(User user, Long id, WeightRequest request) {
        Weight weight = requireOwned(user, id);
        apply(weight, request);
        weightRepository.save(weight);
        recalculateAround(weight);
        return weight;
    }

    public void delete(User user, Long id) {
        Weight weight = requireOwned(user, id);
        Optional<Weight> next = weightRepository.findFirstByUserAndMeasuredAtGreaterThanOrderByMeasuredAtAsc(user, weight.getMeasuredAt());
        try {
            photoStorageService.delete(weight.getPhotoFrontPath());
            photoStorageService.delete(weight.getPhotoLeftPath());
            photoStorageService.delete(weight.getPhotoRightPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        weightRepository.delete(weight);
        next.ifPresent(this::recalculateAround);
    }

    public Weight uploadPhoto(User user, Long id, String side, MultipartFile file) throws IOException {
        Weight weight = requireOwned(user, id);
        String path = photoStorageService.storeWeightPhoto(weight, side, file);
        switch (side) {
            case "front" -> weight.setPhotoFrontPath(path);
            case "left" -> weight.setPhotoLeftPath(path);
            case "right" -> weight.setPhotoRightPath(path);
            default -> throw new BadRequestException("Invalid photo side");
        }
        return weightRepository.save(weight);
    }

    public void deletePhoto(User user, Long id, String side) throws IOException {
        Weight weight = requireOwned(user, id);
        switch (side) {
            case "front" -> {
                photoStorageService.delete(weight.getPhotoFrontPath());
                weight.setPhotoFrontPath(null);
            }
            case "left" -> {
                photoStorageService.delete(weight.getPhotoLeftPath());
                weight.setPhotoLeftPath(null);
            }
            case "right" -> {
                photoStorageService.delete(weight.getPhotoRightPath());
                weight.setPhotoRightPath(null);
            }
            default -> throw new BadRequestException("Invalid photo side");
        }
        weightRepository.save(weight);
    }

    public Resource getPhoto(User user, Long id, String side) {
        Weight weight = requireOwned(user, id);
        String path = switch (side) {
            case "front" -> weight.getPhotoFrontPath();
            case "left" -> weight.getPhotoLeftPath();
            case "right" -> weight.getPhotoRightPath();
            default -> throw new BadRequestException("Invalid photo side");
        };
        if (path == null) {
            throw new NotFoundException("Photo not found");
        }
        return photoStorageService.load(path);
    }

    public Weight requireOwned(User user, Long id) {
        Weight weight = weightRepository.findById(id).orElseThrow(() -> new NotFoundException("Weight not found"));
        if (!weight.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Weight not found");
        }
        return weight;
    }

    public Weight getLastOrNull(User user) {
        return weightRepository.findFirstByUserOrderByMeasuredAtDesc(user).orElse(null);
    }

    public Weight getPreviousOrNull(User user, Weight weight) {
        return weightRepository.findFirstByUserAndMeasuredAtLessThanOrderByMeasuredAtDesc(user, weight.getMeasuredAt()).orElse(null);
    }

    public WeightPerformanceWeek getPerformanceWeek(Weight weight) {
        LocalDate measurementDate = DateTimes.toLocalDate(weight.getMeasuredAt());
        LocalDate endDate = switch (measurementDate.getDayOfWeek()) {
            case FRIDAY -> measurementDate;
            case SATURDAY -> measurementDate.minusDays(1);
            case SUNDAY -> measurementDate.minusDays(2);
            default -> null;
        };
        if (endDate == null) {
            return null;
        }
        LocalDate startDate = endDate.minusDays(6);
        List<com.jllado.weightcontrol.domain.DailyStatus> statuses = dailyStatusRepository
            .findByUserAndStatusDateBetweenOrderByStatusDateAsc(weight.getUser(), startDate, endDate);
        long completed = statuses.stream().mapToLong(com.jllado.weightcontrol.domain.DailyStatus::getRoutinesDone).sum();
        long opportunities = statuses.stream().mapToLong(com.jllado.weightcontrol.domain.DailyStatus::getTotalRoutines).sum();
        return new WeightPerformanceWeek(startDate, endDate, opportunities == 0 ? null : Numbers.percentage(completed, opportunities));
    }

    private void apply(Weight weight, WeightRequest request) {
        weight.setMeasuredAt(request.date());
        weight.setWeight(Numbers.round(request.weight()));
        weight.setFatPercentage(Numbers.round(request.fatPercentage()));
        weight.setFat(Numbers.round(request.fatPercentage().multiply(request.weight()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP)));
        weight.setMuscle(Numbers.round(request.muscle()));
        weight.setMusclePercentage(Numbers.round(request.muscle().multiply(BigDecimal.valueOf(100)).divide(request.weight(), 2, java.math.RoundingMode.HALF_UP)));
        if (weight.getLostWeight() == null) {
            weight.setLostWeight(BigDecimal.ZERO.setScale(2));
            weight.setLostFat(BigDecimal.ZERO.setScale(2));
            weight.setLostMuscle(BigDecimal.ZERO.setScale(2));
        }
    }

    private void recalculateAround(Weight weight) {
        User user = weight.getUser();
        Weight previous = weightRepository.findFirstByUserAndMeasuredAtLessThanOrderByMeasuredAtDesc(user, weight.getMeasuredAt()).orElse(null);
        applyDeltas(weight, previous);
        weightRepository.save(weight);

        weightRepository.findFirstByUserAndMeasuredAtGreaterThanOrderByMeasuredAtAsc(user, weight.getMeasuredAt())
            .ifPresent(next -> {
                applyDeltas(next, weight);
                weightRepository.save(next);
            });
    }

    private void applyDeltas(Weight weight, Weight previous) {
        if (previous == null) {
            weight.setLostWeight(BigDecimal.ZERO.setScale(2));
            weight.setLostFat(BigDecimal.ZERO.setScale(2));
            weight.setLostMuscle(BigDecimal.ZERO.setScale(2));
            return;
        }
        weight.setLostWeight(Numbers.round(weight.getWeight().subtract(previous.getWeight())));
        weight.setLostFat(Numbers.round(weight.getFat().subtract(previous.getFat())));
        weight.setLostMuscle(Numbers.round(weight.getMuscle().subtract(previous.getMuscle())));
    }
}
