// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import org.slf4j.MDC;
import java.util.UUID;

/**
 * Utility class for structured logging with correlation IDs and request context.
 * 
 * <p>This class provides methods for managing logging context using SLF4J's MDC (Mapped Diagnostic Context).
 * It supports adding correlation IDs and request context information to log entries, enabling consistent
 * and parseable logs across all microservices.</p>
 * 
 * <p>Usage example:</p>
 * <pre>
 * // Set correlation ID for request tracking
 * LoggingUtils.setCorrelationId(UUID.randomUUID());
 * 
 * // Set request context
 * LoggingUtils.setRequestContext(environmentId, "StudentPersonal", "default", null);
 * 
 * // Log messages will now include correlation ID and context
 * logger.info("Processing request");
 * 
 * // Clear context when done
 * LoggingUtils.clearContext();
 * </pre>
 * 
 * @see org.slf4j.MDC
 */
public final class LoggingUtils {
    
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String ENVIRONMENT_ID_KEY = "environmentId";
    private static final String SERVICE_NAME_KEY = "serviceName";
    private static final String ZONE_ID_KEY = "zoneId";
    private static final String CONTEXT_ID_KEY = "contextId";
    
    /**
     * Sets the correlation ID in the logging context.
     * 
     * <p>The correlation ID is used to track requests across multiple services and log entries.
     * It will be included in all subsequent log messages until cleared.</p>
     * 
     * @param correlationId the correlation ID as a string
     */
    public static void setCorrelationId(String correlationId) {
        MDC.put(CORRELATION_ID_KEY, correlationId);
    }
    
    /**
     * Sets the correlation ID in the logging context.
     * 
     * <p>Convenience method that accepts a UUID and converts it to a string.</p>
     * 
     * @param correlationId the correlation ID as a UUID
     */
    public static void setCorrelationId(UUID correlationId) {
        setCorrelationId(correlationId.toString());
    }
    
    /**
     * Gets the current correlation ID from the logging context.
     * 
     * @return the correlation ID, or null if not set
     */
    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }
    
    /**
     * Sets the request context in the logging context.
     * 
     * <p>This method adds environment ID, service name, zone ID, and context ID to the logging context.
     * These values will be included in all subsequent log messages until cleared.</p>
     * 
     * <p>Null values are ignored and will not be added to the context.</p>
     * 
     * @param environmentId the environment ID (optional)
     * @param serviceName the service name (optional)
     * @param zoneId the zone ID (optional)
     * @param contextId the context ID (optional)
     */
    public static void setRequestContext(String environmentId, String serviceName, 
                                         String zoneId, String contextId) {
        if (environmentId != null) MDC.put(ENVIRONMENT_ID_KEY, environmentId);
        if (serviceName != null) MDC.put(SERVICE_NAME_KEY, serviceName);
        if (zoneId != null) MDC.put(ZONE_ID_KEY, zoneId);
        if (contextId != null) MDC.put(CONTEXT_ID_KEY, contextId);
    }
    
    /**
     * Clears all values from the logging context.
     * 
     * <p>This method should be called at the end of request processing to prevent
     * context leakage between requests, especially in thread pool environments.</p>
     */
    public static void clearContext() {
        MDC.clear();
    }
    
    private LoggingUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
}
