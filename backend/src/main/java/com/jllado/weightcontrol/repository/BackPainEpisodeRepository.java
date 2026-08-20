package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.BackPainEpisode;
import com.jllado.weightcontrol.domain.BackRegion;
import com.jllado.weightcontrol.domain.BackSide;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackPainEpisodeRepository extends JpaRepository<BackPainEpisode, Long> {
    List<BackPainEpisode> findByUserOrderByEpisodeDateDescEpisodeTimeDescIdDesc(User user);
    List<BackPainEpisode> findByUserAndEpisodeDateBetweenOrderByEpisodeDateAscEpisodeTimeAscIdAsc(User user, LocalDate startDate, LocalDate endDate);
    Optional<BackPainEpisode> findFirstByUserOrderByEpisodeDateAscEpisodeTimeAscIdAsc(User user);
    Optional<BackPainEpisode> findFirstByUserOrderByEpisodeDateDescEpisodeTimeDescIdDesc(User user);
    Optional<BackPainEpisode> findByUserAndEpisodeDateAndPeriodAndRegionAndSide(User user, LocalDate episodeDate, MoodPeriod period, BackRegion region, BackSide side);
    boolean existsByUserAndEpisodeDateAndPeriod(User user, LocalDate episodeDate, MoodPeriod period);
    long countByUser(User user);
}
