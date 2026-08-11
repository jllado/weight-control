package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.BackPainEpisode;
import com.jllado.weightcontrol.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackPainEpisodeRepository extends JpaRepository<BackPainEpisode, Long> {
    List<BackPainEpisode> findByUserOrderByEpisodeDateDescEpisodeTimeDescIdDesc(User user);
}
