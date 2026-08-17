// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * JPA converter for mapping PostgreSQL VARCHAR[] columns to {@code List<String>}.
 *
 * <p>Converts between a Java {@code List<String>} and a PostgreSQL text array
 * stored as a comma-delimited string in the format {@code {val1,val2,val3}}.
 * Hibernate 6 with PostgreSQL stores array columns as their string representation
 * when using a basic AttributeConverter.</p>
 *
 * <p>This converter handles the PostgreSQL array literal format:
 * <ul>
 *   <li>{@code null} → {@code null}</li>
 *   <li>Empty list → {@code "{}"}</li>
 *   <li>{@code ["A","B"]} → {@code "{A,B}"}</li>
 * </ul>
 * </p>
 */
@Converter
public class StringArrayConverter implements AttributeConverter<List<String>, String> {

    /**
     * Converts a {@code List<String>} to a PostgreSQL array literal string.
     *
     * @param attribute the list of strings (may be null)
     * @return PostgreSQL array literal, or null if input is null
     */
    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null) {
            return null;
        }
        if (attribute.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < attribute.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            String value = attribute.get(i);
            if (value == null) {
                sb.append("NULL");
            } else if (value.contains(",") || value.contains("\"") || value.contains("{")
                    || value.contains("}") || value.contains("\\") || value.contains(" ")) {
                sb.append("\"").append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            } else {
                sb.append(value);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Converts a PostgreSQL array literal string to a {@code List<String>}.
     *
     * @param dbData the PostgreSQL array literal (may be null)
     * @return list of strings, or null if input is null
     */
    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        String trimmed = dbData.trim();
        if (trimmed.equals("{}")) {
            return Collections.emptyList();
        }
        // Strip surrounding braces
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return parsePostgresArray(trimmed);
    }

    private List<String> parsePostgresArray(String content) {
        if (content.isEmpty()) {
            return Collections.emptyList();
        }
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result;
    }
}
