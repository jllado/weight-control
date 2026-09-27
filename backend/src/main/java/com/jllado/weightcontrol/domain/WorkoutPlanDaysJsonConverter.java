package com.jllado.weightcontrol.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import java.util.ArrayList;
import java.time.DayOfWeek;
import java.io.IOException;

@Converter
public class WorkoutPlanDaysJsonConverter implements AttributeConverter<List<WorkoutPlanDay>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    @Override
    public String convertToDatabaseColumn(List<WorkoutPlanDay> attribute) {
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to serialize workout plan days", e);
        }
    }

    @Override
    public List<WorkoutPlanDay> convertToEntityAttribute(String dbData) {
        try {
            var rows = OBJECT_MAPPER.readTree(dbData);
            var days = new ArrayList<WorkoutPlanDay>();
            for (var row : rows) {
                var day = OBJECT_MAPPER.treeToValue(row.get("day"), DayOfWeek.class);
                boolean rest = row.path("rest").asBoolean();
                if (row.has("sessions")) {
                    var sessions = OBJECT_MAPPER.readValue(OBJECT_MAPPER.treeAsTokens(row.get("sessions")), new TypeReference<List<WorkoutPlanDay.Session>>() { });
                    String note = row.path("note").isNull() ? null : row.path("note").asText();
                    days.add(new WorkoutPlanDay(day, rest, rest ? note : null, sessions));
                }
                else {
                    List<WorkoutPlanDay.Target> lines = row.has("lines") ? OBJECT_MAPPER.readValue(OBJECT_MAPPER.treeAsTokens(row.get("lines")), new TypeReference<List<WorkoutPlanDay.Target>>() { }) : List.of();
                    String note = row.path("note").isNull() ? null : row.path("note").asText();
                    var sessions = lines.isEmpty() ? List.<WorkoutPlanDay.Session>of() : List.of(new WorkoutPlanDay.Session(null, note, lines));
                    days.add(new WorkoutPlanDay(day, rest, lines.isEmpty() ? note : null, sessions));
                }
            }
            return days;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to deserialize workout plan days", e);
        }
    }
}
