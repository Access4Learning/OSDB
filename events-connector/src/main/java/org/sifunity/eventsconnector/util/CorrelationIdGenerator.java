// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.util;

import java.util.UUID;

/**
 * Utility class for generating correlation IDs for request tracing.
 * Uses UUID-based correlation IDs to uniquely identify requests across services.
 * 
 * Requirements: 14 (Observability and Logging)
 */
public class CorrelationIdGenerator {
    
    /**
     * Generates a new UUID-based correlation ID.
     * 
     * @return a unique correlation ID string
     */
    public static String generate() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private CorrelationIdGenerator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
