package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.BackPainEpisodeCreateRequest;
import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.BackPainEpisodeUpdateRequest;
import com.jllado.weightcontrol.domain.BackPainEpisode;
import com.jllado.weightcontrol.domain.BackRegion;
import com.jllado.weightcontrol.domain.BackSide;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.BackPainEpisodeRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class BackPainEpisodeService {

    private final BackPainEpisodeRepository repository;

    public BackPainEpisodeService(BackPainEpisodeRepository repository) {
        this.repository = repository;
    }

    public List<BackPainEpisode> findAll(User user) {
        return repository.findByUserOrderByEpisodeDateDescEpisodeTimeDescIdDesc(user);
    }

    public BackPainEpisode create(User user, BackPainEpisodeCreateRequest request) {
        validateDate(request.date());
        BackPainEpisode episode = new BackPainEpisode();
        episode.setUser(user);
        episode.setEpisodeDate(request.date());
        episode.setEpisodeTime(LocalTime.now(DateTimes.USER_ZONE).withNano(0));
        apply(episode, request.region(), request.side(), request.pain(), request.note());
        return repository.save(episode);
    }

    public BackPainEpisode update(User user, Long id, BackPainEpisodeUpdateRequest request) {
        BackPainEpisode episode = requireOwned(user, id);
        apply(episode, request.region(), request.side(), request.pain(), request.note());
        return repository.save(episode);
    }

    public void delete(User user, Long id) {
        repository.delete(requireOwned(user, id));
    }

    public BackPainEpisode requireOwned(User user, Long id) {
        BackPainEpisode episode = repository.findById(id).orElseThrow(() -> new NotFoundException("Back pain episode not found"));
        if (!episode.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Back pain episode not found");
        }
        return episode;
    }

    private void apply(BackPainEpisode episode, BackRegion region, BackSide side, Integer pain, String note) {
        episode.setRegion(region);
        episode.setSide(side);
        episode.setPain(pain);
        episode.setNote(note);
    }

    private void validateDate(LocalDate date) {
        if (date.isAfter(LocalDate.now(DateTimes.USER_ZONE))) {
            throw new BadRequestException("Back pain episode date cannot be in the future");
        }
    }
}
