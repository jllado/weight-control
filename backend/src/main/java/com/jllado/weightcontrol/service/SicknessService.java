package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.SicknessDtos.SicknessRequest;
import com.jllado.weightcontrol.domain.Sickness;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.SicknessRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class SicknessService {

    private final SicknessRepository repository;

    public SicknessService(SicknessRepository repository) {
        this.repository = repository;
    }

    public List<Sickness> findAll(User user) {
        return repository.findByUserOrderBySicknessDateDesc(user);
    }

    public List<Sickness> findBetween(User user, LocalDate from, LocalDate to) {
        return repository.findByUserAndSicknessDateBetweenOrderBySicknessDateAsc(user, from, to);
    }

    public Sickness create(User user, SicknessRequest request) {
        validateDate(request.date());
        repository.findByUserAndSicknessDate(user, request.date()).ifPresent(existing -> {
            throw new BadRequestException("Sickness entry already exists for this date");
        });
        Sickness sickness = new Sickness();
        sickness.setUser(user);
        apply(sickness, request);
        return repository.save(sickness);
    }

    public Sickness update(User user, Long id, SicknessRequest request) {
        validateDate(request.date());
        Sickness sickness = requireOwned(user, id);
        repository.findByUserAndSicknessDate(user, request.date())
            .filter(existing -> !existing.getId().equals(sickness.getId()))
            .ifPresent(existing -> {
                throw new BadRequestException("Sickness entry already exists for this date");
            });
        apply(sickness, request);
        return repository.save(sickness);
    }

    public void delete(User user, Long id) {
        repository.delete(requireOwned(user, id));
    }

    public Sickness requireOwned(User user, Long id) {
        Sickness sickness = repository.findById(id).orElseThrow(() -> new NotFoundException("Sickness entry not found"));
        if (!sickness.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Sickness entry not found");
        }
        return sickness;
    }

    private void apply(Sickness sickness, SicknessRequest request) {
        sickness.setSicknessDate(request.date());
        sickness.setType(request.type());
        sickness.setSeverity(request.severity());
        sickness.setNote(request.note());
    }

    private void validateDate(LocalDate date) {
        if (date.isAfter(LocalDate.now(DateTimes.USER_ZONE))) {
            throw new BadRequestException("Sickness date cannot be in the future");
        }
    }
}
