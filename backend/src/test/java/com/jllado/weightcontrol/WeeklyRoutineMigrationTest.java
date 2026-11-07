package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.Context;
import db.migration.V120__migrate_weekly_measurement_routines;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class WeeklyRoutineMigrationTest {
    @Container static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("weekly_routines");

    @Test void convertsEachOwnersSchedulesAndEntireMetricHistoryOnceWithoutNotifications() throws Exception {
        flyway("119").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            for (int owner = 1; owner <= 2; owner++) {
                statement.executeUpdate("insert into users (email, weight_reminder_day, weight_reminder_time, blood_pressure_reminder_day, blood_pressure_reminder_time, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values ('weekly" + owner + "@example.com','MONDAY','08:00','FRIDAY','09:15',2500,2500,2500,2500,2500,2500,2500)");
            }
            statement.executeUpdate("insert into routines (user_id,start_date,name,current_strike,best_strike) values (1,'2020-01-01','Legacy daily',5,10)");
            for (String instant : List.of("2026-10-16 22:30:00", "2026-10-19 08:00:00", "2026-10-26 08:00:00")) {
                statement.executeUpdate("insert into weights (user_id, measured_at, weight, fat_percentage, fat, muscle, muscle_percentage, lost_weight, lost_fat, lost_muscle) values (1,'" + instant + "',80,15,12,60,75,0,0,0)");
            }
            statement.executeUpdate("insert into blood_pressures (user_id,measured_at,upper,lower,lost_upper,lost_lower) values (1,'2026-10-30 09:00:00',120,80,0,0)");
            statement.executeUpdate("insert into weights (user_id, measured_at, weight, fat_percentage, fat, muscle, muscle_percentage, lost_weight, lost_fat, lost_muscle) values (2,'2026-09-06 08:00:00',80,15,12,60,75,0,0,0)");
            flyway(null).migrate();
            assertEquals(List.of(List.of("DAILY","NONE","5","10")), snapshot(connection,"select cadence,automatic_trigger,current_strike,best_strike from routines where id = 1"));
            assertEquals(List.of(List.of("1","Check weight","MONDAY","08:00:00"),List.of("1","Check blood pressure","FRIDAY","09:15:00"),List.of("2","Check weight","MONDAY","08:00:00"),List.of("2","Check blood pressure","FRIDAY","09:15:00")), snapshot(connection,"select r.user_id,r.name,rr.reminder_day,rr.reminder_time from routines r join routine_reminders rr on rr.routine_id=r.id order by r.id"));
            assertEquals(List.of(List.of("2026-10-17","2","2")), snapshot(connection,"select date(start_date + interval 2 hour),current_strike,best_strike from routines where user_id=1 and automatic_trigger='WEIGHT_MEASUREMENT'"));
            assertEquals(List.of(List.of("2026-10-17"),List.of("2026-10-19"),List.of("2026-10-26")), snapshot(connection,"select event_date from routine_automatic_evidence e join routines r on r.id=e.routine_id where r.user_id=1 and r.automatic_trigger='WEIGHT_MEASUREMENT' order by event_date"));
            assertEquals(List.of(List.of("2026-09-05")), snapshot(connection,"select date(start_date + interval 2 hour) from routines where user_id=2 and automatic_trigger='WEIGHT_MEASUREMENT'"));
            assertEquals(List.of(List.of("0")), snapshot(connection,"select count(*) from in_app_notifications"));
            assertEquals(List.of(List.of("0")), snapshot(connection,"select count(*) from routine_automatic_evidence e join routines r on r.id=e.routine_id join weights w on e.source_key=cast(w.id as char) left join routine_checkins c on c.routine_id=r.id and c.checked_at=w.measured_at where e.source_kind='WEIGHT' and c.id is null"));
            var before = snapshot(connection,"select * from routine_checkins order by id");
            var routines = snapshot(connection,"select * from routines order by id");
            Context context = mock(Context.class); when(context.getConnection()).thenReturn(connection);
            new V120__migrate_weekly_measurement_routines().migrate(context);
            assertEquals(before,snapshot(connection,"select * from routine_checkins order by id"));
            assertEquals(routines,snapshot(connection,"select * from routines order by id"));
        }
    }

    private List<List<String>> snapshot(Connection connection,String query) throws Exception {
        var result = new ArrayList<List<String>>();
        try (var statement=connection.createStatement(); var rows=statement.executeQuery(query)) {
            while(rows.next()) { var row=new ArrayList<String>(); for(int i=1;i<=rows.getMetaData().getColumnCount();i++) row.add(rows.getString(i)); result.add(row); }
        }
        return result;
    }
    private Flyway flyway(String target) {
        var config=Flyway.configure().dataSource(DATABASE.getJdbcUrl(),DATABASE.getUsername(),DATABASE.getPassword()).locations("classpath:db/migration");
        if(target!=null) config.target(target);
        return config.load();
    }
}
