package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.LipidPanelDtos.LipidPanelRequest;
import com.jllado.weightcontrol.domain.LipidPanel;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.LipidPanelRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class LipidPanelService {

    private final LipidPanelRepository repository;

    public LipidPanelService(LipidPanelRepository repository) {
        this.repository = repository;
    }

    public List<LipidPanel> findAll(User user) {
        return repository.findByUserOrderByPanelDateDesc(user);
    }

    public LipidPanel create(User user, LipidPanelRequest request) {
        validateDate(request.date());
        validateUniqueDate(user, request.date(), null);
        LipidPanel panel = new LipidPanel();
        panel.setUser(user);
        apply(panel, request);
        return repository.save(panel);
    }

    public LipidPanel update(User user, Long id, LipidPanelRequest request) {
        validateDate(request.date());
        LipidPanel panel = requireOwned(user, id);
        validateUniqueDate(user, request.date(), panel.getId());
        apply(panel, request);
        return repository.save(panel);
    }

    public void delete(User user, Long id) {
        repository.delete(requireOwned(user, id));
    }

    private LipidPanel requireOwned(User user, Long id) {
        LipidPanel panel = repository.findById(id).orElseThrow(() -> new NotFoundException("Lipid panel not found"));
        if (!panel.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Lipid panel not found");
        }
        return panel;
    }

    private void validateUniqueDate(User user, LocalDate date, Long currentId) {
        repository.findByUserAndPanelDate(user, date)
            .filter(existing -> !existing.getId().equals(currentId))
            .ifPresent(existing -> {
                throw new BadRequestException("Lipid panel already exists for this date");
            });
    }

    private void apply(LipidPanel panel, LipidPanelRequest request) {
        panel.setPanelDate(request.date());
        panel.setTotalCholesterol(request.totalCholesterol());
        panel.setHdlCholesterol(request.hdlCholesterol());
        panel.setLdlCholesterol(request.ldlCholesterol());
        panel.setTriglycerides(request.triglycerides());
    }

    private void validateDate(LocalDate date) {
        if (date.isAfter(LocalDate.now(DateTimes.USER_ZONE))) {
            throw new BadRequestException("Lipid panel date cannot be in the future");
        }
    }
}
