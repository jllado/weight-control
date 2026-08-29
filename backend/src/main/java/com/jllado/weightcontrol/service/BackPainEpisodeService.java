package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.BackPainEpisodeCreateRequest;
import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.BackPainEpisodeUpdateRequest;
import com.jllado.weightcontrol.domain.BackPainEpisode;
import com.jllado.weightcontrol.domain.BackPainSeverity;
import com.jllado.weightcontrol.domain.BackRegion;
import com.jllado.weightcontrol.domain.BackSide;
import com.jllado.weightcontrol.domain.MoodPeriod;
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

    public List<BackPainEpisode> findBetween(User user, LocalDate from, LocalDate to) {
        return repository.findByUserAndEpisodeDateBetweenOrderByEpisodeDateAscEpisodeTimeAscIdAsc(user, from, to);
    }

    public BackPainEpisode create(User user, BackPainEpisodeCreateRequest request) {
        validateDate(request.date());
        rejectDuplicate(user, request.date(), request.period(), request.region(), request.side(), null);
        BackPainEpisode episode = new BackPainEpisode();
        episode.setUser(user);
        episode.setEpisodeDate(request.date());
        episode.setEpisodeTime(LocalTime.now(DateTimes.USER_ZONE).withNano(0));
        apply(episode, request.period(), request.region(), request.side(), request.severity(), request.note());
        return repository.save(episode);
    }

    public BackPainEpisode update(User user, Long id, BackPainEpisodeUpdateRequest request) {
        BackPainEpisode episode = requireOwned(user, id);
        rejectDuplicate(user, episode.getEpisodeDate(), request.period(), request.region(), request.side(), episode.getId());
        apply(episode, request.period(), request.region(), request.side(), request.severity(), request.note());
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

    private void apply(BackPainEpisode episode, MoodPeriod period, BackRegion region, BackSide side, BackPainSeverity severity, String note) {
        episode.setPeriod(period);
        episode.setRegion(region);
        episode.setSide(side);
        episode.setSeverity(severity);
        episode.setNote(note);
    }

    private void rejectDuplicate(User user, LocalDate date, MoodPeriod period, BackRegion region, BackSide side, Long episodeId) {
        repository.findByUserAndEpisodeDateAndPeriodAndRegionAndSide(user, date, period, region, side)
            .filter(existing -> !existing.getId().equals(episodeId))
            .ifPresent(existing -> {
                throw new BadRequestException("Back pain episode already exists for this date, period, and location");
            });
    }

    private void validateDate(LocalDate date) {
        if (date.isAfter(LocalDate.now(DateTimes.USER_ZONE))) {
            throw new BadRequestException("Back pain episode date cannot be in the future");
        }
    }
}
