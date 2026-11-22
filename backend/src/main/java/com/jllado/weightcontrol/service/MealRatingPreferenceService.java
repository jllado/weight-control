package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.MealRatingDtos.Preferences;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealRatingPreferenceService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;

    public MealRatingPreferenceService(JdbcTemplate jdbc, UserRepository users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    public Preferences forDate(User user, LocalDate date) {
        return jdbc.query("""
            SELECT main_meals, main_meal_limit_percent, minimum_meal_gap_minutes, minimum_bedtime_gap_minutes, saturated_fat_energy_percent
            FROM meal_rating_preferences WHERE user_id=? AND effective_date<=? ORDER BY effective_date DESC LIMIT 1
            """, (row, index) -> {
                var mainMeals = EnumSet.noneOf(MealType.class);
                String stored = row.getString("main_meals");
                if (!stored.isEmpty()) {
                    for (String name : stored.split(",")) mainMeals.add(MealType.valueOf(name));
                }
                return new Preferences(mainMeals, row.getInt("main_meal_limit_percent"), row.getInt("minimum_meal_gap_minutes"),
                    row.getInt("minimum_bedtime_gap_minutes"), row.getInt("saturated_fat_energy_percent"));
            }, user.getId(), date).stream().findFirst().orElseGet(Preferences::defaults);
    }

    @Transactional
    public Preferences update(User user, Preferences prefs) {
        users.findByIdForUpdate(user.getId()).orElseThrow();
        String mainMeals = prefs.mainMeals().stream().sorted().map(Enum::name).collect(Collectors.joining(","));
        jdbc.update("""
            INSERT INTO meal_rating_preferences
                (user_id, effective_date, main_meals, main_meal_limit_percent, minimum_meal_gap_minutes, minimum_bedtime_gap_minutes, saturated_fat_energy_percent)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE main_meals=VALUES(main_meals), main_meal_limit_percent=VALUES(main_meal_limit_percent),
                minimum_meal_gap_minutes=VALUES(minimum_meal_gap_minutes), minimum_bedtime_gap_minutes=VALUES(minimum_bedtime_gap_minutes),
                saturated_fat_energy_percent=VALUES(saturated_fat_energy_percent)
            """, user.getId(), LocalDate.now(DateTimes.USER_ZONE), mainMeals, prefs.mainMealLimitPercent(),
            prefs.minimumMealGapMinutes(), prefs.minimumBedtimeGapMinutes(), prefs.saturatedFatEnergyPercent());
        return prefs;
    }
}
