// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Enumeration of valid SIF serviceType HTTP header values.
 *
 * <p>Per SIF 3.3 section 4.3.2, the {@code serviceType} header distinguishes
 * different categories of service requests. This enum provides case-insensitive
 * lookup and validation utilities used by the gateway's ServiceTypeValidationFilter.</p>
 *
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.3/">SIF 3.3 Specification</a>
 */
public enum SifServiceType {
    OBJECT,
    UTILITY,
    FUNCTIONAL,
    SERVICEPATH,
    XQUERYTEMPLATE,
    SERVICE;

    private static final Map<String, SifServiceType> LOOKUP =
        Arrays.stream(values())
            .collect(Collectors.toMap(v -> v.name().toUpperCase(), v -> v));

    /**
     * Performs a case-insensitive lookup of a serviceType string value.
     *
     * @param value the serviceType header value (may be null or blank)
     * @return an Optional containing the matching SifServiceType, or empty if
     *         the value is null, blank, or not a recognized serviceType
     */
    public static Optional<SifServiceType> fromString(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        return Optional.ofNullable(LOOKUP.get(value.toUpperCase()));
    }

    /**
     * Checks whether a string is a valid SIF serviceType value (case-insensitive).
     *
     * @param value the serviceType header value to check
     * @return true if the value matches one of the six valid SIF serviceType values
     */
    public static boolean isValid(String value) {
        return value != null && LOOKUP.containsKey(value.toUpperCase());
    }
}
