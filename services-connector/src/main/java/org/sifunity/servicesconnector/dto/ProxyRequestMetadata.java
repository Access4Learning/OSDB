// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Metadata for DELAYED_to_IMMEDIATE proxy requests in functional services.
 *
 * <p>Stores all information needed by a background worker to forward a DELAYED
 * request to an IMMEDIATE-only provider and deliver the response to the
 * consumer's queue.</p>
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>Request metadata is stored in Redis work queue</li>
 *   <li>HTTP 202 Accepted is returned to the consumer immediately</li>
 *   <li>A background worker retrieves the metadata</li>
 *   <li>The worker makes a synchronous HTTP call to the provider</li>
 *   <li>The worker delivers the provider's response to the consumer's queue</li>
 * </ol>
 *
 * <p><strong>Requirements:</strong> 5.2, 5.4</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxyRequestMetadata {

    /** Unique identifier for the request message. */
    private UUID messageId;

    /** The consumer's queue ID where the response should be delivered. */
    private UUID consumerQueueId;

    /** The environment ID from the request context. */
    private UUID environmentId;

    /** The environment name from the request context. */
    private String environmentName;

    /** The provider's HTTP endpoint URL. */
    private String providerEndpoint;

    /** The HTTP method to use when forwarding (GET, POST, PUT, DELETE). */
    private String httpMethod;

    /** The provider URL path suffix (e.g., /serviceName/jobId/phaseName). */
    private String pathSuffix;

    /** The complete SIF message envelope to send to the provider. */
    private Object requestEnvelope;

    /** Sanitized passthrough headers to forward to the provider. */
    private Map<String, String> passthroughHeaders;

    /** Timestamp when the request metadata was created. */
    private Instant createdAt;

    /** Number of retry attempts made for this request. */
    private int retryCount;
}
