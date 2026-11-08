package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jllado.weightcontrol.util.DateTimes;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** One-time classification of approved owner history; recorded plan snapshots remain immutable. */
public class V123__classify_owner_walking_and_enable_routine extends BaseJavaMigration {
    private record Exercise(long id, String name, String description) { }
    private record Evidence(long workoutId, LocalDate date) { }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        long owner;
        try (var statement = connection.prepareStatement("select id from users where email = 'jllado@gmail.com' for update"); var rows = statement.executeQuery()) {
            if (!rows.next()) return;
            owner = rows.getLong(1);
        }
        Exercise walking = exercise(connection, "walking");
        Exercise indoor = exercise(connection, "indoor-walking");
        Exercise outdoor = exercise(connection, "outdoor-walking");
        if (walking != null) {
            classifyLines(connection, owner, walking, indoor, false);
            classifyLines(connection, owner, walking, outdoor, true);
            classifyActivePlan(connection, owner, walking, indoor, outdoor);
        }
        if (outdoor != null) enableRoutine(connection, owner, outdoor);
    }

    private Exercise exercise(Connection connection, String key) throws Exception {
        try (var statement = connection.prepareStatement("select id,name,description from exercises where built_in_image_key = ?")) {
            statement.setString(1, key);
            try (var rows = statement.executeQuery()) { return rows.next() ? new Exercise(rows.getLong(1), rows.getString(2), rows.getString(3)) : null; }
        }
    }

    private void classifyLines(Connection connection, long owner, Exercise walking, Exercise replacement, boolean weekend) throws Exception {
        // A matching custom catalog name can prevent V122 from adding this built-in identity.
        if (replacement == null) return;
        try (var statement = connection.prepareStatement("""
            update workout_lines l join workouts w on w.id = l.workout_id
            set l.exercise_id = ?, l.updated_at = l.updated_at
            where w.user_id = ? and l.exercise_id = ? and (weekday(w.workout_date) >= 5) = ?
            """)) {
            statement.setLong(1, replacement.id()); statement.setLong(2, owner); statement.setLong(3, walking.id()); statement.setBoolean(4, weekend);
            statement.executeUpdate();
        }
    }

    private void classifyActivePlan(Connection connection, long owner, Exercise walking, Exercise indoor, Exercise outdoor) throws Exception {
        long planId;
        String savedDays;
        try (var statement = connection.prepareStatement("select id,days_json from workout_plans where user_id = ? and archived_at is null for update")) {
            statement.setLong(1, owner);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return;
                planId = rows.getLong(1); savedDays = rows.getString(2);
            }
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode days = mapper.readTree(savedDays);
        boolean changed = false;
        for (JsonNode day : days) {
            DayOfWeek weekday = DayOfWeek.valueOf(day.get("day").asText());
            Exercise replacement = weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY ? outdoor : indoor;
            if (replacement == null) continue;
            if (day.has("sessions")) {
                for (JsonNode session : day.get("sessions")) changed |= classifyTargets(session.path("lines"), walking, replacement);
            } else {
                changed |= classifyTargets(day.path("lines"), walking, replacement);
            }
        }
        if (!changed) return;
        try (var statement = connection.prepareStatement("update workout_plans set days_json = ?, update_token = ?, updated_at = current_timestamp(6) where id = ?")) {
            statement.setString(1, mapper.writeValueAsString(days)); statement.setString(2, UUID.randomUUID().toString()); statement.setLong(3, planId); statement.executeUpdate();
        }
    }

    private boolean classifyTargets(JsonNode lines, Exercise walking, Exercise replacement) {
        boolean changed = false;
        for (JsonNode line : lines) {
            if (line.get("exerciseId").asLong() != walking.id()) continue;
            ObjectNode target = (ObjectNode) line;
            target.put("exerciseId", replacement.id()); target.put("exerciseName", replacement.name()); target.put("exerciseDescription", replacement.description());
            changed = true;
        }
        return changed;
    }

    private void enableRoutine(Connection connection, long owner, Exercise outdoor) throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 7);
        try (var statement = connection.prepareStatement("""
            update routines set automatic_trigger = 'OUTDOOR_WALK_OVER_40_MINUTES',
                automatic_completion_enabled = true, updated_at = updated_at
            where id = 52 and user_id = ? and binary name = 'Long outside walk'
                and cadence = 'WEEKLY' and start_date = ?
                and automatic_trigger = 'NONE' and automatic_completion_enabled = false
            """)) {
            statement.setLong(1, owner); statement.setTimestamp(2, midnight(start), utc());
            if (statement.executeUpdate() == 0) return;
        }
        var evidence = new ArrayList<Evidence>();
        try (var statement = connection.prepareStatement("""
            select w.id,w.workout_date from workouts w
            join workout_lines l on l.workout_id = w.id
            join workout_segments s on s.workout_line_id = l.id
            where w.user_id = ? and w.workout_date >= ? and l.exercise_id = ? and s.skipped = false
            group by w.id,w.workout_date having sum(s.duration_seconds) > 2400
            order by w.workout_date,w.id
            """)) {
            statement.setLong(1, owner); statement.setObject(2, start); statement.setLong(3, outdoor.id());
            try (var rows = statement.executeQuery()) { while (rows.next()) evidence.add(new Evidence(rows.getLong(1), rows.getObject(2, LocalDate.class))); }
        }
        for (Evidence source : evidence) {
            try (var statement = connection.prepareStatement("insert into routine_automatic_evidence (routine_id,source_kind,source_key,event_date) values (52,'WORKOUT',?,?)")) {
                statement.setString(1, Long.toString(source.workoutId())); statement.setObject(2, source.date()); statement.executeUpdate();
            }
            // Match runtime's one check-in per actual workout date without replacing manual evidence.
            try (var statement = connection.prepareStatement("""
                insert into routine_checkins (routine_id,checked_at,manual_completion)
                select 52,?,false where not exists (
                    select 1 from routine_checkins where routine_id = 52 and checked_at >= ? and checked_at < ?)
                """)) {
                statement.setTimestamp(1, midnight(source.date()), utc()); statement.setTimestamp(2, midnight(source.date()), utc()); statement.setTimestamp(3, midnight(source.date().plusDays(1)), utc()); statement.executeUpdate();
            }
        }
        rebuildWeeklySummary(connection);
    }

    private void rebuildWeeklySummary(Connection connection) throws Exception {
        var periods = new TreeSet<LocalDate>();
        Timestamp last = null;
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("select checked_at from routine_checkins where routine_id = 52 order by checked_at")) {
            while (rows.next()) {
                last = rows.getTimestamp(1, utc());
                periods.add(DateTimes.startOfDashboardWeek(last.toInstant().atZone(DateTimes.USER_ZONE).toLocalDate()));
            }
        }
        int current = 0, best = 0;
        LocalDate previous = null;
        for (LocalDate period : periods) {
            current = previous != null && previous.plusWeeks(1).equals(period) ? current + 1 : 1;
            best = Math.max(best, current); previous = period;
        }
        try (var statement = connection.prepareStatement("update routines set current_strike = ?, best_strike = ?, last_time_date = ?, updated_at = updated_at where id = 52")) {
            statement.setInt(1, current); statement.setInt(2, best); statement.setTimestamp(3, last, utc()); statement.executeUpdate();
        }
    }

    private Timestamp midnight(LocalDate date) { return Timestamp.from(DateTimes.startOfDay(date).toInstant()); }
    private Calendar utc() { return Calendar.getInstance(TimeZone.getTimeZone("UTC")); }
}
