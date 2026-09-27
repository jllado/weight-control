package com.jllado.weightcontrol.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

@Converter
public class WorkoutPlanTargetSnapshotConverter implements AttributeConverter<List<WorkoutPlanTargetSnapshot>, String> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<WorkoutPlanTargetSnapshot>> TYPE = new TypeReference<>() { };
    @Override public String convertToDatabaseColumn(List<WorkoutPlanTargetSnapshot> value) {
        try { return value == null ? null : JSON.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Failed to serialize planned workout targets", e); }
    }
    @Override public List<WorkoutPlanTargetSnapshot> convertToEntityAttribute(String value) {
        try { return value == null ? null : JSON.readValue(value, TYPE); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Failed to deserialize planned workout targets", e); }
    }
}
