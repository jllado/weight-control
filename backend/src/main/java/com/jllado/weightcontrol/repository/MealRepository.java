package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealRepository extends JpaRepository<Meal, Long> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "dishes")
    List<Meal> findByUserOrderByMealDateDescIdAsc(User user);

    record Timing(long id, LocalDate mealDate, java.time.LocalTime mealTime, Integer durationMinutes, MealType mealType) {
        public java.time.LocalDateTime start() { return mealDate.atTime(mealTime); }
    }

    List<Timing> findTimingByUserAndMealDateBetweenAndMealTimeIsNotNullOrderByMealDateAscMealTimeAscIdAsc(User user, LocalDate from, LocalDate to);

    Optional<Timing> findFirstTimingByUserAndMealDateLessThanAndMealTimeIsNotNullOrderByMealDateDescMealTimeDescIdDesc(User user, LocalDate before);

    // V129 introduced the explanation solely for backend-generated scores. Earlier app and Coach ratings have no explanation.
    @org.springframework.data.jpa.repository.Query("""
        select m from Meal m where m.user = :user and m.ratingVersion is null and m.rating is not null
        and m.ratingExplanation like 'Base 5. Protein:% Fiber:% Day through meal:% Week from %'
        order by m.mealDate, m.id
        """)
    List<Meal> findLegacyAutomaticRatings(User user);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = """
        UPDATE meals SET rating=:score, rating_explanation=:explanation, rating_version=:version, updated_at=updated_at
        WHERE id=:id AND user_id=:owner AND (rating_version IS NULL OR rating_version=2)
        """, nativeQuery = true)
    void migrateRating(long owner, long id, Integer score, String explanation, int version);

    Optional<Meal> findFirstByUserOrderByMealDateAscIdAsc(User user);

    Optional<Meal> findFirstByUserOrderByMealDateDescIdDesc(User user);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "dishes")
    List<Meal> findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(User user, LocalDate startDate, LocalDate endDate);

    List<Meal> findByUserAndMealTimeIsNotNullOrderByMealDateAscMealTimeAscIdAsc(User user);

    Optional<Meal> findFirstByUserAndMealTimeIsNotNullOrderByMealDateDescMealTimeDescIdDesc(User user);

    List<Meal> findByUserAndMealDateAndMealTypeOrderByMealSequenceAsc(User user, LocalDate mealDate, MealType mealType);

    Optional<Meal> findByUserAndMealDateAndMealTypeAndMealSequence(User user, LocalDate mealDate, MealType mealType, Integer mealSequence);

    long countByUser(User user);
}
