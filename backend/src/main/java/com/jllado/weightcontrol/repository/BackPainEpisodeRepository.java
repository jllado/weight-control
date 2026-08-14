package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.BackPainEpisode;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackPainEpisodeRepository extends JpaRepository<BackPainEpisode, Long> {
    List<BackPainEpisode> findByUserOrderByEpisodeDateDescEpisodeTimeDescIdDesc(User user);
    Optional<BackPainEpisode> findByUserAndEpisodeDateAndPeriod(User user, LocalDate episodeDate, MoodPeriod period);
    boolean existsByUserAndEpisodeDateAndPeriod(User user, LocalDate episodeDate, MoodPeriod period);
}
