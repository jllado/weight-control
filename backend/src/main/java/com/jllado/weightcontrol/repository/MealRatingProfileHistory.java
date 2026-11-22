package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MealRatingProfileHistory {
    private final NamedParameterJdbcTemplate jdbc;

    public MealRatingProfileHistory(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Targets(Integer weekdayCalories, BigDecimal proteinGrams, BigDecimal fiberGrams, BigDecimal saltGrams) { }

    private static final String INSERT = """
        INSERT INTO meal_rating_profile_history
            (user_id, effective_epoch, weekly_maximum, protein_grams, fiber_grams, salt_grams, monday, tuesday, wednesday, thursday, friday, saturday, sunday)
        SELECT :id, :epoch, :maximum, :protein, :fiber, :salt, :monday, :tuesday, :wednesday, :thursday, :friday, :saturday, :sunday
        """;

    // V129 seeded all existing owners. A missing initial row therefore belongs to a new account.
    public void captureInitial(User user) {
        jdbc.update(INSERT + " WHERE NOT EXISTS (SELECT 1 FROM meal_rating_profile_history WHERE user_id = :id)", values(user, user.getCreatedAt()));
    }

    public void captureChange(User user, Instant effectiveAt) {
        jdbc.update(INSERT + """
            ON DUPLICATE KEY UPDATE weekly_maximum=VALUES(weekly_maximum), protein_grams=VALUES(protein_grams),
                fiber_grams=VALUES(fiber_grams), salt_grams=VALUES(salt_grams), monday=VALUES(monday), tuesday=VALUES(tuesday), wednesday=VALUES(wednesday),
                thursday=VALUES(thursday), friday=VALUES(friday), saturday=VALUES(saturday), sunday=VALUES(sunday)
            """, values(user, effectiveAt));
    }

    private MapSqlParameterSource values(User user, Instant effectiveAt) {
        return new MapSqlParameterSource("id", user.getId()).addValue("epoch", effectiveAt.getEpochSecond())
            .addValue("maximum", user.getWeeklyAverageCalorieMaximum()).addValue("protein", user.getMacroProteinTargetGrams())
            .addValue("salt", user.getNutrientSaltTargetGrams()).addValue("fiber", user.getNutrientFiberTargetGrams()).addValue("monday", user.getTypicalCaloriesMonday())
            .addValue("tuesday", user.getTypicalCaloriesTuesday()).addValue("wednesday", user.getTypicalCaloriesWednesday())
            .addValue("thursday", user.getTypicalCaloriesThursday()).addValue("friday", user.getTypicalCaloriesFriday())
            .addValue("saturday", user.getTypicalCaloriesSaturday()).addValue("sunday", user.getTypicalCaloriesSunday());
    }

    public Optional<Targets> find(User user, LocalDate date) {
        return jdbc.query("""
            SELECT monday, tuesday, wednesday, thursday, friday, saturday, sunday, protein_grams, fiber_grams, salt_grams
            FROM meal_rating_profile_history WHERE user_id = :id AND effective_epoch < :until
            ORDER BY effective_epoch DESC LIMIT 1
            """, java.util.Map.of("id", user.getId(), "until", date.plusDays(1).atStartOfDay(DateTimes.USER_ZONE).toEpochSecond()), (row, index) -> new Targets(row.getInt(date.getDayOfWeek().name()),
                row.getBigDecimal("protein_grams"), row.getBigDecimal("fiber_grams"), row.getBigDecimal("salt_grams"))).stream().findFirst();
    }
}
