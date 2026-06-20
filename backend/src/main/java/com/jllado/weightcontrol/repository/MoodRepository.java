package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoodRepository extends JpaRepository<Mood, Long> {
    List<Mood> findByUserOrderByMoodDateDesc(User user);
    Optional<Mood> findByUserAndMoodDate(User user, LocalDate moodDate);
    List<Mood> findByUserAndMoodDateBetweenOrderByMoodDateAsc(User user, LocalDate startDate, LocalDate endDate);
}
