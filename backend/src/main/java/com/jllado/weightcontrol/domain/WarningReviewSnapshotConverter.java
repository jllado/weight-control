package com.jllado.weightcontrol.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class WarningReviewSnapshotConverter implements AttributeConverter<WarningReviewSnapshot, String> {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Override
    public String convertToDatabaseColumn(WarningReviewSnapshot value) {
        if (value == null) return null;
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Cannot serialize warning review snapshot", exception); }
    }

    @Override
    public WarningReviewSnapshot convertToEntityAttribute(String value) {
        if (value == null) return null;
        try { return JSON.readValue(value, WarningReviewSnapshot.class); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Cannot read warning review snapshot", exception); }
    }
}
