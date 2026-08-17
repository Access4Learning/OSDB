// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.exception.InvalidParameterException;
import org.sifunity.broker.common.exception.MissingRequiredFieldException;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

/**
 * Utility class for parsing and validating SIF headers from HTTP requests.
 * 
 * <p>This class provides methods for extracting required and optional headers,
 * parsing UUID headers, and validating that all required SIF headers are present
 * in a request. It is used by the Gateway and other services to process incoming
 * SIF requests according to the SIF 3.7 specification.</p>
 * 
 * <p>Required SIF headers for most requests:
 * <ul>
 *   <li>{@link SifHeaders#AUTHORIZATION} - HMAC signature for authentication</li>
 *   <li>{@link SifHeaders#SERVICE_NAME} - Name of the service being accessed</li>
 *   <li>{@link SifHeaders#ZONE_ID} - Zone identifier for routing</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Extract required header
 * String serviceName = SifHeaderUtils.getRequiredHeader(headers, SifHeaders.SERVICE_NAME);
 * 
 * // Extract optional header
 * String contextId = SifHeaderUtils.getOptionalHeader(headers, SifHeaders.CONTEXT_ID);
 * 
 * // Extract UUID header
 * UUID requestId = SifHeaderUtils.getUuidHeader(headers, SifHeaders.REQUEST_ID);
 * 
 * // Validate all required headers are present
 * SifHeaderUtils.validateRequiredHeaders(headers);
 * }</pre>
 * 
 * @see SifHeaders
 * @see MissingRequiredFieldException
 * @see InvalidParameterException
 */
public final class SifHeaderUtils {
    
    /**
     * Extracts a required header value from the HTTP headers.
     * 
     * <p>This method retrieves the first value of the specified header.
     * If the header is missing, null, or blank (empty or whitespace only),
     * a {@link MissingRequiredFieldException} is thrown.</p>
     * 
     * @param headers    the HTTP headers to extract from
     * @param headerName the name of the header to extract
     * @return the header value (never null or blank)
     * @throws MissingRequiredFieldException if the header is missing, null, or blank
     */
    public static String getRequiredHeader(HttpHeaders headers, String headerName) {
        String value = headers.getFirst(headerName);
        if (value == null || value.isBlank()) {
            throw new MissingRequiredFieldException(headerName);
        }
        return value;
    }
    
    /**
     * Extracts an optional header value from the HTTP headers.
     * 
     * <p>This method retrieves the first value of the specified header.
     * If the header is missing or null, this method returns null without
     * throwing an exception.</p>
     * 
     * @param headers    the HTTP headers to extract from
     * @param headerName the name of the header to extract
     * @return the header value, or null if the header is not present
     */
    public static String getOptionalHeader(HttpHeaders headers, String headerName) {
        return headers.getFirst(headerName);
    }
    
    /**
     * Extracts a UUID header value from the HTTP headers.
     * 
     * <p>This method retrieves the first value of the specified header and
     * attempts to parse it as a UUID. If the header is not present, null is
     * returned. If the header is present but cannot be parsed as a valid UUID,
     * an {@link InvalidParameterException} is thrown.</p>
     * 
     * @param headers    the HTTP headers to extract from
     * @param headerName the name of the header to extract
     * @return the parsed UUID value, or null if the header is not present
     * @throws InvalidParameterException if the header value is not a valid UUID format
     */
    public static UUID getUuidHeader(HttpHeaders headers, String headerName) {
        String value = getOptionalHeader(headers, headerName);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidParameterException(headerName, "Invalid UUID format");
        }
    }
    
    /**
     * Validates that all required SIF headers are present in the HTTP headers.
     * 
     * <p>This method checks for the presence of the following required headers:
     * <ul>
     *   <li>{@link SifHeaders#AUTHORIZATION} - HMAC signature for authentication</li>
     *   <li>{@link SifHeaders#SERVICE_NAME} - Name of the service being accessed</li>
     *   <li>{@link SifHeaders#ZONE_ID} - Zone identifier for routing</li>
     * </ul>
     * 
     * <p>If any required header is missing, null, or blank, a
     * {@link MissingRequiredFieldException} is thrown for the first missing header.</p>
     * 
     * @param headers the HTTP headers to validate
     * @throws MissingRequiredFieldException if any required header is missing, null, or blank
     */
    public static void validateRequiredHeaders(HttpHeaders headers) {
        getRequiredHeader(headers, SifHeaders.AUTHORIZATION);
        getRequiredHeader(headers, SifHeaders.SERVICE_NAME);
        getRequiredHeader(headers, SifHeaders.ZONE_ID);
    }
    
    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private SifHeaderUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
}
