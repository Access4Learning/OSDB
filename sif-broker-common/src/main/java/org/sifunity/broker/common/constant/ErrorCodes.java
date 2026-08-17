// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Constants for standardized error codes used across all SIF Broker services.
 * 
 * <p>Error codes follow a hierarchical naming convention:</p>
 * <ul>
 *   <li>AUTH_xxx - Authentication errors (HTTP 401)</li>
 *   <li>AUTHZ_xxx - Authorization errors (HTTP 403)</li>
 *   <li>ROUTE_xxx - Routing errors (HTTP 404)</li>
 *   <li>VAL_xxx - Validation errors (HTTP 400)</li>
 *   <li>STORAGE_xxx - Storage errors (HTTP 500/503)</li>
 *   <li>SERVICE_xxx - Service availability errors (HTTP 503)</li>
 * </ul>
 * 
 * @see org.sifunity.broker.common.exception.SifBrokerException
 */
public final class ErrorCodes {
    
    // Authentication errors (AUTH_xxx) - HTTP 401
    
    /** Invalid or expired session token */
    public static final String INVALID_TOKEN = "AUTH_001";
    
    /** Timestamp outside acceptable tolerance window */
    public static final String INVALID_TIMESTAMP = "AUTH_002";
    
    /** Malformed Authorization header */
    public static final String MALFORMED_AUTH_HEADER = "AUTH_003";
    
    // Authorization errors (AUTHZ_xxx) - HTTP 403
    
    /** Insufficient permissions for requested operation */
    public static final String INSUFFICIENT_PERMISSIONS = "AUTHZ_001";
    
    /** Access denied to specified zone */
    public static final String ZONE_ACCESS_DENIED = "AUTHZ_002";
    
    // Routing errors (ROUTE_xxx) - HTTP 404
    
    /** Provider not found for service */
    public static final String PROVIDER_NOT_FOUND = "ROUTE_001";
    
    /** Queue not found */
    public static final String QUEUE_NOT_FOUND = "ROUTE_002";
    
    /** Subscription not found */
    public static final String SUBSCRIPTION_NOT_FOUND = "ROUTE_003";
    
    // Validation errors (VAL_xxx) - HTTP 400
    
    /** Invalid request format or content */
    public static final String INVALID_REQUEST = "VAL_001";
    
    /** Required field is missing */
    public static final String MISSING_REQUIRED_FIELD = "VAL_002";
    
    /** Invalid parameter value */
    public static final String INVALID_PARAMETER = "VAL_003";
    
    // Storage errors (STORAGE_xxx) - HTTP 500/503
    
    /** General storage operation error */
    public static final String STORAGE_ERROR = "STORAGE_001";
    
    /** Storage service unavailable */
    public static final String STORAGE_UNAVAILABLE = "STORAGE_002";
    
    // Service errors (SERVICE_xxx) - HTTP 503
    
    /** Provider service unavailable */
    public static final String PROVIDER_UNAVAILABLE = "SERVICE_001";
    
    /** General service unavailable */
    public static final String SERVICE_UNAVAILABLE = "SERVICE_002";
    
    // Proxy impersonation errors (PROXY_xxx)
    
    /** Environment-service unreachable during target lookup (HTTP 503) */
    public static final String PROXY_SERVICE_UNAVAILABLE = "PROXY_001";
    
    /** Target environment not found (HTTP 422) */
    public static final String PROXY_TARGET_NOT_FOUND = "PROXY_002";
    
    /** Target environment is not a broker-services environment (HTTP 403) */
    public static final String PROXY_NOT_BROKER_SERVICES = "PROXY_003";
    
    /** Target environment does not belong to the requested solution (HTTP 403) */
    public static final String PROXY_SOLUTION_MISMATCH = "PROXY_004";
    
    /** Invalid X-Run-As-Environment header value (HTTP 400) */
    public static final String PROXY_INVALID_HEADER = "PROXY_005";
    
    // Internal environment authentication errors
    
    /** Internal environment attempted external authentication (HTTP 401) */
    public static final String INTERNAL_ENV_AUTH_BLOCKED = "AUTH_010";
    
    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static constants.
     */
    private ErrorCodes() {
        throw new UnsupportedOperationException("Utility class");
    }
}
