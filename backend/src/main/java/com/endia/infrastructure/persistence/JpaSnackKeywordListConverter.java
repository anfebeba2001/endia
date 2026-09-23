package com.endia.infrastructure.persistence;

import com.endia.domain.model.SnackKeyword;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * Converts a {@link List} of {@link SnackKeyword} to/from a MySQL JSON column using Jackson
 * (feature 013, FR-011/FR-012).
 *
 * <p>This mirrors {@link JpaJsonObjectConverter} in structure, error handling and the
 * null/blank → empty-list policy. It is a <strong>new class</strong> because an
 * {@code AttributeConverter<X, String>} is statically bound to one attribute type: the existing
 * converter is bound to {@code DragDropContent} and cannot be reused for
 * {@code List<SnackKeyword>}.
 */
@Converter
public class JpaSnackKeywordListConverter implements AttributeConverter<List<SnackKeyword>, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<SnackKeyword> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return mapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert keyword list to JSON", e);
        }
    }

    @Override
    public List<SnackKeyword> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isBlank()) {
                return List.of();
            }
            return mapper.readValue(dbData, new TypeReference<List<SnackKeyword>>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert JSON to keyword list", e);
        }
    }
}
