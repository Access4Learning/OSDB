// SPDX-License-Identifier: MIT
package org.sifunity.integration.generator;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.integration.dto.EnvironmentRegistrationResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generator for creating and managing test queues in the Queue Service.
 * 
 * <p>This generator provides utilities for:
 * <ul>
 *   <li>Creating queues for test environments</li>
 *   <li>Enqueuing test messages</li>
 *   <li>Polling queues for messages</li>
 *   <li>Acknowledging messages</li>
 *   <li>Deleting queues</li>
 * </ul>
 * 
 * @since 1.0.0
 */
@Slf4j
public class QueueGenerator {
    
    private final String queueServiceUrl;
    private final EnvironmentGenerator environmentGenerator;
    
    private static final String PATH_PREFIX = "/" + GlobalAdminConstants.GLOBAL_DOMAIN 
        + "/" + GlobalAdminConstants.GLOBAL_SOLUTION;
    
    public QueueGenerator(String queueServiceUrl, EnvironmentGenerator environmentGenerator) {
        this.queueServiceUrl = queueServiceUrl;
        this.environmentGenerator = environmentGenerator;
    }
    
    /**
     * Creates a queue for the specified environment.
     * 
     * @param ownerId the environment ID that owns the queue
     * @param queueName the name of the queue
     * @param zoneId the zone ID (not used by Queue Service, kept for API compatibility)
     * @param contextId the context ID (not used by Queue Service, kept for API compatibility)
     * @param sessionToken the session token for authentication
     * @param sharedSecret the shared secret for authentication
     * @return the queue ID
     */
    public UUID createQueue(
            UUID ownerId,
            String queueName,
            String zoneId,
            String contextId,
            String sessionToken,
            String sharedSecret) {
        
        log.info("Creating queue: name={}, ownerId={}", queueName, ownerId);
        
        Map<String, Object> request = new HashMap<>();
        request.put("name", queueName);
        request.put("ownerId", ownerId);
        request.put("maxRetries", 3);
        request.put("retryDelayMs", 5000);
        
        Response response = RestAssured.given()
            .baseUri(queueServiceUrl)
            .header("Authorization", environmentGenerator.generateAuthHeader(sessionToken, sharedSecret))
            .header("Content-Type", "application/json")
            .body(request)
        .when()
            .post(PATH_PREFIX + "/queues")
        .then()
            .statusCode(201)
            .extract().response();
        
        UUID queueId = UUID.fromString(response.jsonPath().getString("id"));
        log.info("Queue created: queueId={}", queueId);
        
        return queueId;
    }
    
    /**
     * Creates a queue for the specified environment using the environment's credentials.
     * 
     * @param environment the environment that owns the queue
     * @param queueName the name of the queue
     * @param zoneId the zone ID
     * @param contextId the context ID
     * @return the queue ID
     */
    public UUID createQueue(
            EnvironmentRegistrationResponse environment,
            String queueName,
            String zoneId,
            String contextId) {
        
        return createQueue(
            UUID.fromString(environment.getId()),
            queueName,
            zoneId,
            contextId,
            environment.getSessionToken(),
            environment.getSharedSecret()
        );
    }
    
    /**
     * Enqueues a message to the specified queue.
     * 
     * @param queueId the queue ID
     * @param messagePayload the message payload (JSON string)
     * @param sessionToken the session token for authentication
     * @param sharedSecret the shared secret for authentication
     * @return the message ID
     */
    public UUID enqueueMessage(
            UUID queueId,
            String messagePayload,
            String sessionToken,
            String sharedSecret) {
        
        log.info("Enqueuing message to queue: queueId={}", queueId);
        
        Map<String, Object> request = new HashMap<>();
        request.put("payload", messagePayload);
        
        Response response = RestAssured.given()
            .baseUri(queueServiceUrl)
            .header("Authorization", environmentGenerator.generateAuthHeader(sessionToken, sharedSecret))
            .header("Content-Type", "application/json")
            .body(request)
        .when()
            .post(PATH_PREFIX + "/queues/" + queueId + "/messages")
        .then()
            .statusCode(201)
            .extract().response();
        
        UUID messageId = UUID.fromString(response.jsonPath().getString("messageId"));
        log.info("Message enqueued: messageId={}", messageId);
        
        return messageId;
    }
    
    /**
     * Polls the queue for messages.
     * 
     * @param queueId the queue ID
     * @param maxMessages the maximum number of messages to retrieve
     * @param sessionToken the session token for authentication
     * @param sharedSecret the shared secret for authentication
     * @return the response containing messages
     */
    public Response pollQueue(
            UUID queueId,
            int maxMessages,
            String sessionToken,
            String sharedSecret) {
        
        log.info("Polling queue: queueId={}, maxMessages={}", queueId, maxMessages);
        
        // Use GET /queues/{queueId}/messages (ACK-and-GET-NEXT pattern)
        // Returns 200 with single message body + messageid header, or 204 if empty
        Response response = RestAssured.given()
            .baseUri(queueServiceUrl)
            .header("Authorization", environmentGenerator.generateAuthHeader(sessionToken, sharedSecret))
            .header("Content-Type", "application/json")
        .when()
            .get(PATH_PREFIX + "/queues/" + queueId + "/messages")
        .then()
            .extract().response();
        
        if (response.getStatusCode() == 204) {
            log.info("Polled queue: queueId={}, messagesReceived=0 (empty)", queueId);
        } else {
            String messageId = response.getHeader("messageid");
            log.info("Polled queue: queueId={}, messageId={}, status={}", queueId, messageId, response.getStatusCode());
        }
        
        return response;
    }
    
    /**
     * Polls the queue for messages using the environment's credentials.
     * 
     * @param queueId the queue ID
     * @param maxMessages the maximum number of messages to retrieve
     * @param environment the environment polling the queue
     * @return the response containing messages
     */
    public Response pollQueue(
            UUID queueId,
            int maxMessages,
            EnvironmentRegistrationResponse environment) {
        
        return pollQueue(
            queueId,
            maxMessages,
            environment.getSessionToken(),
            environment.getSharedSecret()
        );
    }
    
    /**
     * Acknowledges a message using the ACK-and-GET-NEXT pattern.
     * Calls GET /queues/{queueId}/messages?deleteMessageId={messageId} which
     * acknowledges the specified message and returns the next one (or 204 if empty).
     *
     * @param queueId the queue ID
     * @param messageId the message ID to acknowledge
     * @param sessionToken the session token for authentication
     * @param sharedSecret the shared secret for authentication
     * @return the response (200 with next message, or 204 if queue empty after ack)
     */
    public Response acknowledgeMessage(
            UUID queueId,
            UUID messageId,
            String sessionToken,
            String sharedSecret) {
        
        log.info("Acknowledging message: queueId={}, messageId={}", queueId, messageId);
        
        Response response = RestAssured.given()
            .baseUri(queueServiceUrl)
            .header("Authorization", environmentGenerator.generateAuthHeader(sessionToken, sharedSecret))
        .when()
            .get(PATH_PREFIX + "/queues/" + queueId + "/messages?deleteMessageId=" + messageId)
        .then()
            .extract().response();
        
        log.info("Message acknowledged: messageId={}, nextStatus={}", messageId, response.getStatusCode());
        return response;
    }
    
    /**
     * Deletes a queue.
     * 
     * @param queueId the queue ID
     * @param sessionToken the session token for authentication
     * @param sharedSecret the shared secret for authentication
     */
    public void deleteQueue(
            UUID queueId,
            String sessionToken,
            String sharedSecret) {
        
        log.info("Deleting queue: queueId={}", queueId);
        
        RestAssured.given()
            .baseUri(queueServiceUrl)
            .header("Authorization", environmentGenerator.generateAuthHeader(sessionToken, sharedSecret))
        .when()
            .delete(PATH_PREFIX + "/queues/" + queueId)
        .then()
            .statusCode(204);
        
        log.info("Queue deleted: queueId={}", queueId);
    }
}
