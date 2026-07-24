package com.jllado.weightcontrol.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize string list", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        try {
            return OBJECT_MAPPER.readValue(dbData, TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize string list", e);
        }
    }
}
