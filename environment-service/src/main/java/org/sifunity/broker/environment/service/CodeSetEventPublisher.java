// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.sifunity.broker.environment.config.BrokerInfrastructureConfig;
import org.sifunity.broker.environment.dto.CodeSetDTO;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes code set change events to the events-connector using broadcast fanout.
 *
 * <p>Unlike {@link AlertEventPublisher} (which uses targeted delivery to specific
 * subscriber queues based on ownership), this publisher uses the standard
 * events-connector broadcast: code set events go to ALL subscribers of the
 * {@code codeSets} service. Code sets are not ownership-scoped — all consumers
 * need to know about changes.</p>
 *
 * <p>Event flow:</p>
 * <ol>
 *   <li>Build event payload with the code set object and action type</li>
 *   <li>POST to {@code /{domainId}/{solutionId}/events} on the events-connector
 *       with headers: {@code serviceName=codeSets}, {@code zoneId=environment-global}</li>
 *   <li>Events-connector handles subscription matching and queue delivery</li>
 * </ol>
 *
 * <p>Error handling: catches all exceptions, logs warning, does not propagate
 * (best-effort). Event publication failure never blocks the originating
 * create/update/delete operation.</p>
 *
 * <p><strong>Requirements:</strong> 6.1, 6.2, 6.3, 6.4, 6.5, 6.6</p>
 *
 * @see CodeSetsService
 */
@Service
public class CodeSetEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CodeSetEventPublisher.class);

    private static final String CODE_SETS_SERVICE_NAME = "codeSets";
    private static final String ENVIRONMENT_GLOBAL_ZONE_ID = "environment-global";
    private static final String DEFAULT_CONTEXT_ID = "DEFAULT";
    private static final String BROKER_ENVIRONMENT_NAME = "BROKER_SERVICES";

    private final BrokerInfrastructureConfig infrastructureConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CodeSetEventPublisher(BrokerInfrastructureConfig infrastructureConfig,
                                 RestTemplate restTemplate,
                                 ObjectMapper objectMapper) {
        this.infrastructureConfig = infrastructureConfig;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a code set CREATE event via the events-connector broadcast.
     *
     * @param codeSetDTO the created code set DTO
     * @param domainId   the domain ID from the request path
     * @param solutionId the solution ID from the request path
     */
    public void publishCodeSetCreatedEvent(CodeSetDTO codeSetDTO,
                                           String domainId, String solutionId) {
        publishEvent(codeSetDTO, "CREATE", domainId, solutionId);
    }

    /**
     * Publishes a code set UPDATE event via the events-connector broadcast.
     *
     * @param codeSetDTO the updated code set DTO
     * @param domainId   the domain ID from the request path
     * @param solutionId the solution ID from the request path
     */
    public void publishCodeSetUpdatedEvent(CodeSetDTO codeSetDTO,
                                           String domainId, String solutionId) {
        publishEvent(codeSetDTO, "UPDATE", domainId, solutionId);
    }

    /**
     * Publishes a code set DELETE event via the events-connector broadcast.
     *
     * @param codeSetDTO the deleted code set DTO
     * @param domainId   the domain ID from the request path
     * @param solutionId the solution ID from the request path
     */
    public void publishCodeSetDeletedEvent(CodeSetDTO codeSetDTO,
                                           String domainId, String solutionId) {
        publishEvent(codeSetDTO, "DELETE", domainId, solutionId);
    }

    /**
     * Publishes a code set event to the events-connector.
     *
     * <p>Builds the event payload with the code set object and action type,
     * then POSTs to the events-connector endpoint. The events-connector
     * handles subscription matching and fan-out delivery to all subscriber queues.</p>
     *
     * @param codeSetDTO  the code set DTO for the event payload
     * @param eventAction the action type: CREATE, UPDATE, or DELETE
     * @param domainId    the domain ID from the request path
     * @param solutionId  the solution ID from the request path
     */
    private void publishEvent(CodeSetDTO codeSetDTO, String eventAction,
                              String domainId, String solutionId) {
        log.debug("Publishing code set {} event: id={}, domainId={}, solutionId={}",
                eventAction, codeSetDTO.id(), domainId, solutionId);

        try {
            String url = infrastructureConfig.eventsConnector()
                    + "/" + domainId + "/" + solutionId + "/events";

            // Build event payload with action and code set object
            Map<String, Object> eventPayload = buildEventPayload(codeSetDTO, eventAction);

            // Set required headers for events-connector
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("serviceName", CODE_SETS_SERVICE_NAME);
            headers.set("zoneId", ENVIRONMENT_GLOBAL_ZONE_ID);
            headers.set("contextId", DEFAULT_CONTEXT_ID);
            headers.set("serviceType", "UTILITY");
            headers.set("eventAction", eventAction);
            headers.set("X-Environment-Id", UUID.randomUUID().toString());
            headers.set("X-Environment-Name", BROKER_ENVIRONMENT_NAME);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(eventPayload, headers);

            restTemplate.postForEntity(url, request, Void.class);

            log.info("Code set {} event published: id={}, domainId={}, solutionId={}",
                    eventAction, codeSetDTO.id(), domainId, solutionId);

        } catch (Exception e) {
            log.warn("Code set {} event publication failed (best-effort): id={}, error={}",
                    eventAction, codeSetDTO.id(), e.getMessage());
        }
    }

    /**
     * Builds the event payload containing the code set object and action type.
     *
     * @param codeSetDTO  the code set DTO
     * @param eventAction the action type (CREATE, UPDATE, DELETE)
     * @return the event payload map
     */
    private Map<String, Object> buildEventPayload(CodeSetDTO codeSetDTO, String eventAction) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventAction", eventAction);
        payload.put("codeSet", objectMapper.convertValue(codeSetDTO, Map.class));
        return payload;
    }
}
