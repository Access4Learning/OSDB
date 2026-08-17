// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import org.sifunity.broker.common.exception.ParameterConflictException;

import java.util.Map;

/**
 * Stateless utility class that merges SIF parameters from two sources:
 * URL matrix parameters (SIF 3.3 legacy) and HTTP headers (SIF 3.7 modern).
 *
 * <p>Resolution logic per parameter:</p>
 * <ul>
 *   <li>Both sources present with same value → use that value</li>
 *   <li>Only one source present → use that value</li>
 *   <li>Both sources present with different values → throw {@link ParameterConflictException}</li>
 *   <li>Both sources absent → null</li>
 * </ul>
 *
 * <p>Key mapping from matrix parameters to resolved fields:</p>
 * <ul>
 *   <li>Matrix {@code "zoneId"} / Header {@code "zoneId"} → {@code ResolvedParams.zoneId}</li>
 *   <li>Matrix {@code "contextId"} / Header {@code "contextId"} → {@code ResolvedParams.contextId}</li>
 *   <li>Matrix {@code "messageId"} / Header {@code "deleteMessageId"} → {@code ResolvedParams.deleteMessageId}</li>
 * </ul>
 *
 * @see ParameterConflictException
 * @see MatrixParameterParser
 */
public final class ParameterResolver {

    /** Matrix parameter key for zone ID. */
    private static final String MATRIX_ZONE_ID = "zoneId";
    /** Matrix parameter key for context ID. */
    private static final String MATRIX_CONTEXT_ID = "contextId";
    /** Matrix parameter key for delete message ID (SIF 3.3 uses "messageId"). */
    private static final String MATRIX_MESSAGE_ID = "messageId";

    /** HTTP header key for zone ID. */
    private static final String HEADER_ZONE_ID = "zoneId";
    /** HTTP header key for context ID. */
    private static final String HEADER_CONTEXT_ID = "contextId";
    /** HTTP header key for delete message ID. */
    private static final String HEADER_DELETE_MESSAGE_ID = "deleteMessageId";

    /**
     * Resolved parameters after merging matrix params and headers.
     *
     * @param zoneId          the resolved zone ID, or null if absent from both sources
     * @param contextId       the resolved context ID, or null if absent from both sources
     * @param deleteMessageId the resolved delete message ID, or null if absent from both sources
     */
    public record ResolvedParams(String zoneId, String contextId, String deleteMessageId) {}

    /**
     * Resolves parameters by merging values from matrix parameters and HTTP headers.
     *
     * <p>For each recognized parameter, applies the resolution logic:
     * both-same → use value; one-source-only → use that value;
     * both-different → throw; both-absent → null.</p>
     *
     * @param matrixParams the extracted matrix parameters (from {@link MatrixParameterParser}), may be null or empty
     * @param headers      the HTTP header values (key → value), may be null or empty
     * @return a {@link ResolvedParams} record with merged values
     * @throws ParameterConflictException if any parameter has conflicting values between sources
     */
    public static ResolvedParams resolve(Map<String, String> matrixParams, Map<String, String> headers)
            throws ParameterConflictException {

        Map<String, String> matrix = matrixParams != null ? matrixParams : Map.of();
        Map<String, String> hdrs = headers != null ? headers : Map.of();

        String zoneId = resolveParam("zoneId",
                getOrNull(matrix, MATRIX_ZONE_ID),
                getOrNull(hdrs, HEADER_ZONE_ID));

        String contextId = resolveParam("contextId",
                getOrNull(matrix, MATRIX_CONTEXT_ID),
                getOrNull(hdrs, HEADER_CONTEXT_ID));

        String deleteMessageId = resolveParam("deleteMessageId",
                getOrNull(matrix, MATRIX_MESSAGE_ID),
                getOrNull(hdrs, HEADER_DELETE_MESSAGE_ID));

        return new ResolvedParams(zoneId, contextId, deleteMessageId);
    }

    /**
     * Resolves a single parameter from two sources.
     *
     * @param paramName   the logical parameter name (for error reporting)
     * @param matrixValue the value from matrix parameters, or null if absent
     * @param headerValue the value from HTTP headers, or null if absent
     * @return the resolved value, or null if both absent
     * @throws ParameterConflictException if both values are present and differ
     */
    private static String resolveParam(String paramName, String matrixValue, String headerValue) {
        if (matrixValue == null && headerValue == null) {
            return null;
        }
        if (matrixValue == null) {
            return headerValue;
        }
        if (headerValue == null) {
            return matrixValue;
        }
        // Both present — check for conflict
        if (matrixValue.equals(headerValue)) {
            return matrixValue;
        }
        throw new ParameterConflictException(paramName, matrixValue, headerValue);
    }

    /**
     * Gets a value from a map, treating empty strings as null (absent).
     */
    private static String getOrNull(Map<String, String> map, String key) {
        String value = map.get(key);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value;
    }

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private ParameterResolver() {
        throw new UnsupportedOperationException("Utility class");
    }
}
