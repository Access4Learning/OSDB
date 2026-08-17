// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.sifunity.broker.environment.dto.ExtendedElement;

import java.util.Collections;
import java.util.List;

/**
 * JPA converter for serializing/deserializing {@link ExtendedElement} lists
 * to and from JSON strings for storage in a PostgreSQL JSONB column.
 * 
 * <p>Uses Jackson {@link ObjectMapper} for JSON processing. Handles null and
 * empty list edge cases gracefully.</p>
 * 
 * @see ExtendedElement
 */
@Converter
public class ExtendedElementsConverter
        implements AttributeConverter<List<ExtendedElement>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Converts a list of {@link ExtendedElement} to a JSON string for database storage.
     * 
     * @param attribute the list of extended elements (may be null or empty)
     * @return JSON string representation, or null if the input is null
     */
    @Override
    public String convertToDatabaseColumn(List<ExtendedElement> attribute) {
        if (attribute == null) {
            return null;
        }
        if (attribute.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize extendedElements to JSON", e);
        }
    }

    /**
     * Converts a JSON string from the database to a list of {@link ExtendedElement}.
     * 
     * @param dbData the JSON string from the JSONB column (may be null or empty)
     * @return list of extended elements, or empty list if input is null/empty
     */
    @Override
    public List<ExtendedElement> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<List<ExtendedElement>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize extendedElements from JSON", e);
        }
    }
}
