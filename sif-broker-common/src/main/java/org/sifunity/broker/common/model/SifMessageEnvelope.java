// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.model;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Builder;
import lombok.Value;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Generic message envelope conforming to SIF 3.7 specification.
 * 
 * <p>This class wraps all messages exchanged in the SIF Unity Broker system,
 * providing standardized headers and a generic payload.</p>
 * 
 * <p>The envelope includes both required and optional SIF headers as defined
 * in the SIF 3.7 specification. The messageId and timestamp are automatically
 * generated if not provided during construction.</p>
 * 
 * <p><strong>Required Headers:</strong></p>
 * <ul>
 *   <li>messageId - Unique identifier for the message (auto-generated)</li>
 *   <li>timestamp - Message creation time (auto-generated)</li>
 *   <li>messageType - Type of message (REQUEST, RESPONSE, EVENT)</li>
 *   <li>serviceName - Name of the service</li>
 *   <li>serviceType - Type of service (OBJECT, FUNCTIONAL, UTILITY)</li>
 * </ul>
 * 
 * <p><strong>Optional Headers:</strong></p>
 * <ul>
 *   <li>zoneId - Zone identifier for routing</li>
 *   <li>contextId - Context identifier for routing</li>
 *   <li>requestId - Original request ID for response correlation</li>
 *   <li>queueId - Queue identifier for delayed messages</li>
 *   <li>sourceId - Source environment identifier</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * SifMessageEnvelope<StudentPersonal> envelope = SifMessageEnvelope.<StudentPersonal>builder()
 *     .messageType(MessageType.REQUEST)
 *     .serviceName("StudentPersonal")
 *     .serviceType(ServiceType.OBJECT)
 *     .zoneId("default")
 *     .payload(studentData)
 *     .build();
 * }</pre>
 * 
 * @param <T> the type of the payload
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
@Value
@Builder
public class SifMessageEnvelope<T> {
    
    // Required SIF headers
    
    /**
     * Unique identifier for this message.
     * Automatically generated if not provided.
     */
    @NotNull
    @Builder.Default
    UUID messageId = UUID.randomUUID();
    
    /**
     * Timestamp when the message was created.
     * Automatically set to current time if not provided.
     */
    @NotNull
    @Builder.Default
    Instant timestamp = Instant.now();
    
    /**
     * Type of message (REQUEST, RESPONSE, EVENT).
     * Required field that must be explicitly set.
     */
    @NotNull
    MessageType messageType;
    
    /**
     * Name of the service this message is for.
     * Required field that must be explicitly set.
     */
    @NotBlank
    String serviceName;
    
    /**
     * Type of service (OBJECT, FUNCTIONAL, UTILITY).
     * Required field that must be explicitly set.
     */
    @NotNull
    ServiceType serviceType;
    
    // Optional SIF headers
    
    /**
     * Zone identifier for routing.
     * Optional field used for multi-zone deployments.
     */
    String zoneId;
    
    /**
     * Context identifier for routing.
     * Optional field used for additional routing dimensions.
     */
    String contextId;
    
    /**
     * Original request ID for response correlation.
     * Used to correlate responses with their originating requests.
     * Per SIF 3.3: "A Consumer specified 'token' that uniquely identifies every
     * delayed Request. It could be as simple as a monotonically increasing integer."
     * Type is String to accommodate any consumer-supplied correlation token.
     */
    String requestId;
    
    /**
     * Queue identifier for delayed messages.
     * Used when messages are queued for asynchronous delivery.
     */
    UUID queueId;
    
    /**
     * Source environment identifier.
     * Identifies the environment that originated this message.
     */
    UUID sourceId;
    
    // Passthrough headers
    
    /**
     * Passthrough HTTP headers that survive transit through the Broker.
     * Contains non-blacklisted, non-SIF-structured headers from the original request.
     * Defaults to an empty map when not provided or when null is deserialized (backward compatibility).
     */
    @Builder.Default
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    Map<String, String> headers = Collections.emptyMap();
    
    // Payload
    
    /**
     * The message payload.
     * Can be any type, allowing for flexible message content.
     */
    T payload;
}
