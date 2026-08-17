// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Constants for SIF standard headers and internal broker headers.
 * 
 * <p>This class provides constants for all SIF 3.7 specification headers
 * used in message envelopes and HTTP requests, as well as internal headers
 * injected by the Gateway for routing and correlation.</p>
 * 
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public final class SifHeaders {
    
    // SIF standard headers
    
    /** Authorization header containing HMAC signature */
    public static final String AUTHORIZATION = "Authorization";
    
    /** Unique identifier for the message */
    public static final String MESSAGE_ID = "messageId";
    
    /** ISO-8601 timestamp when the message was created */
    public static final String TIMESTAMP = "timestamp";
    
    /** Type of message: REQUEST, RESPONSE, or EVENT */
    public static final String MESSAGE_TYPE = "messageType";
    
    /** Name of the service being accessed */
    public static final String SERVICE_NAME = "serviceName";
    
    /** Type of service: OBJECT, FUNCTIONAL, or UTILITY */
    public static final String SERVICE_TYPE = "serviceType";
    
    /** Zone identifier for routing */
    public static final String ZONE_ID = "zoneId";
    
    /** Context identifier for additional routing dimension */
    public static final String CONTEXT_ID = "contextId";
    
    /** Request identifier for correlating responses */
    public static final String REQUEST_ID = "requestId";
    
    /** Queue identifier for DELAYED message pattern */
    public static final String QUEUE_ID = "queueId";
    
    /** Relative service path per SIF 3.7 (e.g. /StudentPersonals;contextId=DEFAULT;zoneId=zone1) */
    public static final String RELATIVE_SERVICE_PATH = "relativeServicePath";
    
    /** Source environment identifier */
    public static final String SOURCE_ID = "sourceId";
    
    // Internal headers (injected by Gateway)
    
    /** Internal header for authenticated environment ID */
    public static final String X_ENVIRONMENT_ID = "X-Environment-Id";
    
    /** Internal header for environment application name */
    public static final String X_ENVIRONMENT_NAME = "X-Environment-Name";
    
    /** Internal header for environment fingerprint (e.g. "broker-services") */
    public static final String X_ENVIRONMENT_FINGERPRINT = "X-Environment-Fingerprint";
    
    /** Internal header for the solution UUID the authenticated environment belongs to */
    public static final String X_SOLUTION_UUID = "X-Solution-Uuid";
    
    /** Internal header for request correlation tracking */
    public static final String X_CORRELATION_ID = "X-Correlation-Id";
    
    /** Admin proxy impersonation header — specifies target environment to run as */
    public static final String X_RUN_AS_ENVIRONMENT = "X-Run-As-Environment";
    
    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static constants.
     */
    private SifHeaders() {
        throw new UnsupportedOperationException("Utility class");
    }
}
