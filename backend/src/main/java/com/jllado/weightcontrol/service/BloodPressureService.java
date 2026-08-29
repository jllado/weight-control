package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.BloodPressureDtos.BloodPressureRequest;
import com.jllado.weightcontrol.domain.BloodPressure;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class BloodPressureService {

    private final BloodPressureRepository repository;

    public BloodPressureService(BloodPressureRepository repository) {
        this.repository = repository;
    }

    public List<BloodPressure> findAll(User user) {
        return repository.findByUserOrderByMeasuredAtDesc(user);
    }

    public List<BloodPressure> findBetween(User user, LocalDate from, LocalDate to) {
        return repository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(
            user,
            DateTimes.startOfDay(from),
            DateTimes.startOfDay(to).plusDays(1)
        );
    }

    public BloodPressure create(User user, BloodPressureRequest request) {
        BloodPressure bloodPressure = new BloodPressure();
        bloodPressure.setUser(user);
        apply(bloodPressure, request);
        repository.save(bloodPressure);
        recalculateAround(bloodPressure);
        return bloodPressure;
    }

    public BloodPressure update(User user, Long id, BloodPressureRequest request) {
        BloodPressure bloodPressure = requireOwned(user, id);
        apply(bloodPressure, request);
        repository.save(bloodPressure);
        recalculateAround(bloodPressure);
        return bloodPressure;
    }

    public void delete(User user, Long id) {
        BloodPressure bloodPressure = requireOwned(user, id);
        var next = repository.findFirstByUserAndMeasuredAtGreaterThanOrderByMeasuredAtAsc(user, bloodPressure.getMeasuredAt());
        repository.delete(bloodPressure);
        next.ifPresent(this::recalculateAround);
    }

    public BloodPressure getLastOrNull(User user) {
        return repository.findFirstByUserOrderByMeasuredAtDesc(user).orElse(null);
    }

    public BloodPressure requireOwned(User user, Long id) {
        BloodPressure bloodPressure = repository.findById(id).orElseThrow(() -> new NotFoundException("Blood pressure not found"));
        if (!bloodPressure.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Blood pressure not found");
        }
        return bloodPressure;
    }

    private void apply(BloodPressure bloodPressure, BloodPressureRequest request) {
        bloodPressure.setMeasuredAt(request.date());
        bloodPressure.setUpper(request.upper());
        bloodPressure.setLower(request.lower());
        if (bloodPressure.getLostUpper() == null) {
            bloodPressure.setLostUpper(0);
            bloodPressure.setLostLower(0);
        }
    }

    private void recalculateAround(BloodPressure bloodPressure) {
        User user = bloodPressure.getUser();
        BloodPressure previous = repository.findFirstByUserAndMeasuredAtLessThanOrderByMeasuredAtDesc(user, bloodPressure.getMeasuredAt()).orElse(null);
        applyDeltas(bloodPressure, previous);
        repository.save(bloodPressure);
        repository.findFirstByUserAndMeasuredAtGreaterThanOrderByMeasuredAtAsc(user, bloodPressure.getMeasuredAt()).ifPresent(next -> {
            applyDeltas(next, bloodPressure);
            repository.save(next);
        });
    }

    private void applyDeltas(BloodPressure current, BloodPressure previous) {
        if (previous == null) {
            current.setLostUpper(0);
            current.setLostLower(0);
            return;
        }
        current.setLostUpper(current.getUpper() - previous.getUpper());
        current.setLostLower(current.getLower() - previous.getLower());
    }
}
