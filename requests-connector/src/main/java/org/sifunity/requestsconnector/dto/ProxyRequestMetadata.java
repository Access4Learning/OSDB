// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for DELAYED_to_IMMEDIATE proxy requests.
 * 
 * This DTO stores the metadata required for background processing of DELAYED requests
 * to IMMEDIATE-only providers. When a consumer sends a DELAYED request but the provider
 * only supports IMMEDIATE pattern, the Requests Connector acts as a proxy:
 * 
 * 1. The request metadata is stored in Redis work queue
 * 2. HTTP 202 Accepted is returned to the consumer immediately
 * 3. A background worker retrieves the metadata from the queue
 * 4. The worker makes a synchronous HTTP call to the provider
 * 5. The worker routes the provider's response to the Responses Connector
 * 6. The Responses Connector enqueues the response to the consumer's queue
 * 
 * This class contains all the information needed by the background worker to:
 * - Make the HTTP call to the provider (providerEndpoint, requestEnvelope)
 * - Route the response to the correct consumer (consumerQueueId)
 * - Track and correlate the request (messageId)
 * - Implement retry logic (retryCount, createdAt)
 * 
 * Validates Requirements 5.1, 5.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxyRequestMetadata {
    
    /**
     * Unique identifier for the request message.
     * Used for tracking, correlation, and logging throughout the proxy processing lifecycle.
     */
    private UUID messageId;
    
    /**
     * The consumer's queue identifier where the response should be delivered.
     * The background worker passes this to the Responses Connector so the response
     * can be enqueued to the correct consumer queue.
     */
    private UUID consumerQueueId;
    
    /**
     * The environment ID from the request context.
     * Required by the Responses Connector for routing the response.
     */
    private UUID environmentId;
    
    /**
     * The environment name from the request context.
     * Required by the Responses Connector for routing the response.
     */
    private String environmentName;
    
    /**
     * The domain ID from the request context.
     * Required by the Responses Connector for correct queue stream key resolution.
     */
    private String domainId;
    
    /**
     * The solution ID from the request context.
     * Required by the Responses Connector for correct queue stream key resolution.
     */
    private String solutionId;
    
    /**
     * The original HTTP method from the consumer's request (GET, POST, PUT, DELETE).
     * The proxy worker preserves this when forwarding to the provider.
     */
    private String httpMethod;
    
    /**
     * The provider environment's UUID.
     * Used by provider request interceptors to perform lookups and retrieve
     * provider-specific configuration (e.g., shared secrets, tags).
     */
    private UUID providerEnvironmentId;
    
    /**
     * The relativeServicePath per SIF 3.3 §5.14.1.
     * Replicates the service name and matrix parameters (zone, context) from the original request URL.
     * The broker inserts this into all delayed responses as an aid to stateless consumers.
     * Example: "StudentPersonals;contextId=DEFAULT;zoneId=production"
     */
    private String relativeServicePath;
    
    /**
     * The provider's HTTP endpoint URL.
     * The background worker makes a synchronous HTTP POST request to this endpoint
     * with the requestEnvelope as the body.
     */
    private String providerEndpoint;
    
    /**
     * The complete SIF message envelope to send to the provider.
     * This envelope contains all required SIF headers (messageId, timestamp, serviceName, etc.)
     * and the original request payload from the consumer.
     * The envelope is constructed by the DelayedRequestHandler before storing the metadata.
     * 
     * Note: This field references the SifMessageEnvelope type from sif-broker-common library.
     * For serialization to Redis, ensure Jackson is configured to handle this type.
     */
    private Object requestEnvelope;  // Type: SifMessageEnvelope from sif-broker-common
    
    /**
     * Timestamp when the request metadata was created and stored.
     * Used for monitoring request age, detecting stale requests, and observability.
     */
    private Instant createdAt;
    
    /**
     * Number of retry attempts made for this request.
     * Initialized to 0 when the metadata is first created.
     * Incremented each time the background worker retries after a failure.
     * When retryCount reaches the configured maximum (e.g., 3), the worker
     * sends an error response to the consumer's queue instead of retrying again.
     */
    private int retryCount;
}
