package com.endia.infrastructure.persistence;

import com.endia.domain.model.DragDropContent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts a structured {@link DragDropContent} object to/from a MySQL JSON column
 * using Jackson. Unlike the {@code List<String>}-typed {@link JpaJsonConverter},
 * this serializes the full object (variant + items + canonical arrangement).
 */
@Converter
public class JpaJsonObjectConverter implements AttributeConverter<DragDropContent, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(DragDropContent attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert DragDropContent to JSON", e);
        }
    }

    @Override
    public DragDropContent convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(dbData, DragDropContent.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert JSON to DragDropContent", e);
        }
    }
}
