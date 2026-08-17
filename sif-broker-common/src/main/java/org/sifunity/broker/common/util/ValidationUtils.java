// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.MessageExchangePattern;
import org.sifunity.broker.common.exception.InvalidParameterException;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Utility class for common validation operations.
 * 
 * <p>This class provides validation methods for common data types and formats
 * used throughout the SIF Unity Broker system, including UUIDs, service names,
 * zone IDs, message exchange patterns, and access types.</p>
 * 
 * <p>All validation methods throw {@link InvalidParameterException} when
 * validation fails, providing descriptive error messages to help identify
 * the validation issue.</p>
 * 
 * <p>This is a utility class and cannot be instantiated.</p>
 * 
 * <h2>Usage Examples:</h2>
 * <pre>{@code
 * // Validate a UUID
 * ValidationUtils.validateUuid("550e8400-e29b-41d4-a716-446655440000", "environmentId");
 * 
 * // Validate a service name
 * ValidationUtils.validateServiceName("StudentPersonal");
 * 
 * // Validate a zone ID
 * ValidationUtils.validateZoneId("default");
 * 
 * // Validate a message exchange pattern
 * ValidationUtils.validateMessageExchangePattern("IMMEDIATE");
 * 
 * // Validate an access type
 * ValidationUtils.validateAccessType("PROVIDE");
 * }</pre>
 * 
 * @see InvalidParameterException
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public final class ValidationUtils {
    
    /**
     * Pattern for valid service names.
     * Service names must start with a letter and contain only alphanumeric characters,
     * underscores, or hyphens.
     */
    private static final Pattern SERVICE_NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]*$");
    
    /**
     * Pattern for valid zone IDs.
     * Zone IDs must start with an alphanumeric character and contain only
     * alphanumeric characters, underscores, or hyphens.
     */
    private static final Pattern ZONE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]*$");
    
    /**
     * Validates that a string is a valid UUID format.
     * 
     * <p>This method attempts to parse the provided string as a UUID. If the
     * string is not a valid UUID format, an {@link InvalidParameterException}
     * is thrown with a descriptive error message.</p>
     * 
     * @param value     the string to validate as a UUID
     * @param fieldName the name of the field being validated (used in error message)
     * @throws InvalidParameterException if the value is not a valid UUID format
     * 
     * @see UUID#fromString(String)
     */
    public static void validateUuid(String value, String fieldName) {
        if (value == null) {
            throw new InvalidParameterException(fieldName, "Must not be null");
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidParameterException(fieldName, "Invalid UUID format");
        }
    }
    
    /**
     * Validates that a service name conforms to the required format.
     * 
     * <p>Valid service names must:
     * <ul>
     *   <li>Start with a letter (a-z, A-Z)</li>
     *   <li>Contain only alphanumeric characters, underscores, or hyphens</li>
     *   <li>Not be null or empty</li>
     * </ul>
     * 
     * <p>Examples of valid service names:
     * <ul>
     *   <li>StudentPersonal</li>
     *   <li>School_Info</li>
     *   <li>student-demographics</li>
     * </ul>
     * 
     * @param serviceName the service name to validate
     * @throws InvalidParameterException if the service name is null or does not match the required format
     */
    public static void validateServiceName(String serviceName) {
        if (serviceName == null || !SERVICE_NAME_PATTERN.matcher(serviceName).matches()) {
            throw new InvalidParameterException("serviceName", 
                "Must start with letter and contain only alphanumeric, underscore, or hyphen");
        }
    }
    
    /**
     * Validates that a zone ID conforms to the required format.
     * 
     * <p>Valid zone IDs must:
     * <ul>
     *   <li>Start with an alphanumeric character (a-z, A-Z, 0-9)</li>
     *   <li>Contain only alphanumeric characters, underscores, or hyphens</li>
     *   <li>Not be null or empty</li>
     * </ul>
     * 
     * <p>Examples of valid zone IDs:
     * <ul>
     *   <li>default</li>
     *   <li>zone1</li>
     *   <li>test-zone</li>
     *   <li>prod_zone_01</li>
     * </ul>
     * 
     * @param zoneId the zone ID to validate
     * @throws InvalidParameterException if the zone ID is null or does not match the required format
     */
    public static void validateZoneId(String zoneId) {
        if (zoneId == null || !ZONE_ID_PATTERN.matcher(zoneId).matches()) {
            throw new InvalidParameterException("zoneId", 
                "Must start with alphanumeric and contain only alphanumeric, underscore, or hyphen");
        }
    }
    
    /**
     * Validates that a message exchange pattern is a valid enum value.
     * 
     * <p>Valid message exchange patterns are:
     * <ul>
     *   <li>IMMEDIATE - Synchronous request/response</li>
     *   <li>DELAYED - Asynchronous via queues</li>
     *   <li>EVENT - Publish/subscribe</li>
     * </ul>
     * 
     * <p>The pattern string is case-sensitive and must match exactly.</p>
     * 
     * @param pattern the message exchange pattern string to validate
     * @throws InvalidParameterException if the pattern is not a valid MessageExchangePattern enum value
     * 
     * @see MessageExchangePattern
     */
    public static void validateMessageExchangePattern(String pattern) {
        if (pattern == null) {
            throw new InvalidParameterException("messageExchangePattern", 
                "Must be one of: IMMEDIATE, DELAYED, EVENT");
        }
        try {
            MessageExchangePattern.valueOf(pattern);
        } catch (IllegalArgumentException e) {
            throw new InvalidParameterException("messageExchangePattern", 
                "Must be one of: IMMEDIATE, DELAYED, EVENT");
        }
    }
    
    /**
     * Validates that an access type is a valid enum value.
     * 
     * <p>Valid access types per SIF 3.7 {@code rightTypeType}:
     * <ul>
     *   <li>QUERY - Read/query access to a service</li>
     *   <li>CREATE - Create access to a service</li>
     *   <li>UPDATE - Update access to a service</li>
     *   <li>DELETE - Delete access to a service</li>
     *   <li>SUBSCRIBE - Subscribe to events from a service</li>
     *   <li>PROVIDE - Provide a service</li>
     * </ul>
     * 
     * <p>The access type string is case-sensitive and must match exactly.</p>
     * 
     * @param accessType the access type string to validate
     * @throws InvalidParameterException if the access type is not a valid AccessType enum value
     * 
     * @see AccessType
     */
    public static void validateAccessType(String accessType) {
        if (accessType == null) {
            throw new InvalidParameterException("accessType", 
                "Must be one of: QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE");
        }
        try {
            AccessType.valueOf(accessType);
        } catch (IllegalArgumentException e) {
            throw new InvalidParameterException("accessType", 
                "Must be one of: QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE");
        }
    }
    
    /**
     * Private constructor to prevent instantiation.
     * This is a utility class and should not be instantiated.
     * 
     * @throws UnsupportedOperationException if instantiation is attempted
     */
    private ValidationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
}
