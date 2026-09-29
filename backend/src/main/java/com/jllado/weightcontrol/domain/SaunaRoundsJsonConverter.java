package com.jllado.weightcontrol.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

@Converter
public class SaunaRoundsJsonConverter implements AttributeConverter<List<Integer>, String> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Integer>> TYPE = new TypeReference<>() { };

    @Override public String convertToDatabaseColumn(List<Integer> value) {
        try { return value == null ? null : JSON.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Failed to serialize sauna rounds", exception); }
    }

    @Override public List<Integer> convertToEntityAttribute(String value) {
        try { return value == null ? null : JSON.readValue(value, TYPE); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Failed to deserialize sauna rounds", exception); }
    }
}
