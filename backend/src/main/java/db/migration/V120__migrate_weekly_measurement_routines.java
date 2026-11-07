package db.migration;

import com.jllado.weightcontrol.util.DateTimes;
import java.sql.Connection;
import java.sql.Timestamp;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** One-time owner-scoped conversion; it records evidence without emitting notifications. */
public class V120__migrate_weekly_measurement_routines extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        List<Long> owners = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("select id from users order by id")) {
            while (rows.next()) owners.add(rows.getLong(1));
        }
        for (long owner : owners) {
            migrateMetric(connection, owner, "weights", "weight", "WEIGHT_MEASUREMENT", "WEIGHT", "Check weight");
            migrateMetric(connection, owner, "blood_pressures", "blood_pressure", "BLOOD_PRESSURE_MEASUREMENT", "BLOOD_PRESSURE", "Check blood pressure");
        }
    }

    private java.util.Calendar utc() {
        return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
    }

    private void migrateMetric(Connection connection, long owner, String table, String setting, String trigger, String kind, String name) throws Exception {
        record Reading(long id, Timestamp instant, LocalDate date) { }
        List<Reading> readings = new ArrayList<>();
        try (var statement = connection.prepareStatement("select id, measured_at from " + table + " where user_id = ? order by measured_at, id")) {
            statement.setLong(1, owner);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    Timestamp instant = rows.getTimestamp(2, utc());
                    readings.add(new Reading(rows.getLong(1), instant, instant.toInstant().atZone(DateTimes.USER_ZONE).toLocalDate()));
                }
            }
        }
        Long routineId = null;
        try (var statement = connection.prepareStatement("select id from routines where user_id = ? and automatic_trigger = ? and cadence = 'WEEKLY'")) {
            statement.setLong(1, owner); statement.setString(2, trigger);
            try (var rows = statement.executeQuery()) { if (rows.next()) routineId = rows.getLong(1); }
        }
        if (routineId != null) return;
        LocalDate first = readings.isEmpty() ? LocalDate.now(DateTimes.USER_ZONE) : readings.getFirst().date();
        try (var statement = connection.prepareStatement("insert into routines (user_id,start_date,name,current_strike,best_strike,personal_records_enabled,automatic_trigger,automatic_completion_enabled,cadence) values (?,?,?,0,0,false,?,true,'WEEKLY')", Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, owner);
            statement.setTimestamp(2, Timestamp.from(DateTimes.startOfDay(DateTimes.startOfDashboardWeek(first)).toInstant()), utc());
            statement.setString(3, name); statement.setString(4, trigger); statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) { keys.next(); routineId = keys.getLong(1); }
        }
        try (var statement = connection.prepareStatement("insert into routine_types (routine_id,type) values (?,?)")) {
            statement.setLong(1, routineId); statement.setString(2, kind); statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("insert into routine_reminders (routine_id,reminder_day,reminder_time) select ?," + setting + "_reminder_day," + setting + "_reminder_time from users where id = ?")) {
            statement.setLong(1, routineId); statement.setLong(2, owner); statement.executeUpdate();
        }
        for (Reading reading : readings) {
            try (var statement = connection.prepareStatement("insert into routine_automatic_evidence (routine_id,source_kind,source_key,event_date) values (?,?,?,?)")) {
                statement.setLong(1, routineId); statement.setString(2, kind); statement.setString(3, Long.toString(reading.id())); statement.setObject(4, reading.date());
                statement.executeUpdate();
            }
            // Keep the original measurement instant; period deduplication belongs to summaries.
            try (var statement = connection.prepareStatement("insert into routine_checkins (routine_id,checked_at,manual_completion) select ?,?,false where not exists (select 1 from routine_checkins where routine_id = ? and checked_at = ?)")) {
                statement.setLong(1, routineId); statement.setTimestamp(2, reading.instant(), utc()); statement.setLong(3, routineId); statement.setTimestamp(4, reading.instant(), utc()); statement.executeUpdate();
            }
        }
        var periods = new TreeSet<LocalDate>();
        readings.forEach(reading -> periods.add(DateTimes.startOfDashboardWeek(reading.date())));
        int current = 0, best = 0;
        LocalDate previous = null;
        for (LocalDate period : periods) {
            current = previous != null && previous.plusWeeks(1).equals(period) ? current + 1 : 1;
            best = Math.max(best, current); previous = period;
        }
        try (var statement = connection.prepareStatement("update routines set current_strike = ?, best_strike = ?, last_time_date = ? where id = ?")) {
            statement.setInt(1, current); statement.setInt(2, best);
            if (readings.isEmpty()) statement.setNull(3, java.sql.Types.TIMESTAMP);
            else statement.setTimestamp(3, readings.getLast().instant(), utc());
            statement.setLong(4, routineId); statement.executeUpdate();
        }
    }
}
