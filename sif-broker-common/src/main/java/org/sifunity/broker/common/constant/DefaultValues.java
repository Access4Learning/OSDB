// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Default configuration values used across all SIF Broker services.
 * 
 * <p>This class provides sensible defaults for various configuration parameters
 * including authentication, queue settings, and message handling. Services can
 * override these defaults through their own configuration files.</p>
 */
public final class DefaultValues {
    
    // Authentication settings
    
    /** 
     * Default timestamp tolerance in minutes for HMAC authentication.
     * Requests with timestamps outside this window will be rejected.
     */
    public static final long TIMESTAMP_TOLERANCE_MINUTES = 5;
    
    // Queue settings
    
    /** 
     * Default maximum number of delivery retry attempts for queued messages.
     * After this many failures, messages are marked as FAILED.
     */
    public static final int DEFAULT_MAX_RETRIES = 3;
    
    /** 
     * Default delay in milliseconds between retry attempts.
     * This provides a simple backoff strategy for failed deliveries.
     */
    public static final long DEFAULT_RETRY_DELAY_MS = 5000;
    
    // Message settings
    
    /** 
     * Maximum message payload size in megabytes.
     * Messages exceeding this size will be rejected.
     */
    public static final int MAX_MESSAGE_SIZE_MB = 10;
    
    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static constants.
     */
    private DefaultValues() {
        throw new UnsupportedOperationException("Utility class");
    }
}
