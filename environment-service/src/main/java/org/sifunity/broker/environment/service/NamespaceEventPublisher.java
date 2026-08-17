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
import org.sifunity.broker.environment.dto.NamespaceDTO;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes namespace change events to the events-connector using broadcast fanout.
 *
 * <p>Like {@link CodeSetEventPublisher}, this publisher uses the standard
 * events-connector broadcast: namespace events go to ALL subscribers of the
 * {@code namespaces} service. Namespaces are not ownership-scoped — all consumers
 * need to know about changes.</p>
 *
 * <p>Event flow:</p>
 * <ol>
 *   <li>Build event payload with the namespace object and action type</li>
 *   <li>POST to {@code /{domainId}/{solutionId}/events} on the events-connector
 *       with headers: {@code serviceName=namespaces}, {@code serviceType=UTILITY},
 *       {@code zoneId=environment-global}</li>
 *   <li>Events-connector handles subscription matching and queue delivery</li>
 * </ol>
 *
 * <p>Error handling: catches all exceptions, logs warning, does not propagate
 * (best-effort). Event publication failure never blocks the originating
 * create/update/delete operation.</p>
 *
 * <p><strong>Requirements:</strong> 6.1, 6.2, 6.3, 6.4, 6.5, 6.6</p>
 *
 * @see NamespacesService
 */
@Service
public class NamespaceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NamespaceEventPublisher.class);

    private static final String NAMESPACES_SERVICE_NAME = "namespaces";
    private static final String ENVIRONMENT_GLOBAL_ZONE_ID = "environment-global";
    private static final String DEFAULT_CONTEXT_ID = "DEFAULT";
    private static final String BROKER_ENVIRONMENT_NAME = "BROKER_SERVICES";

    private final BrokerInfrastructureConfig infrastructureConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public NamespaceEventPublisher(BrokerInfrastructureConfig infrastructureConfig,
                                   RestTemplate restTemplate,
                                   ObjectMapper objectMapper) {
        this.infrastructureConfig = infrastructureConfig;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a namespace CREATE event via the events-connector broadcast.
     *
     * @param namespaceDTO the created namespace DTO
     * @param domainId     the domain ID from the request path
     * @param solutionId   the solution ID from the request path
     */
    public void publishNamespaceCreatedEvent(NamespaceDTO namespaceDTO,
                                             String domainId, String solutionId) {
        publishEvent(namespaceDTO, "CREATE", domainId, solutionId);
    }

    /**
     * Publishes a namespace UPDATE event via the events-connector broadcast.
     *
     * @param namespaceDTO the updated namespace DTO
     * @param domainId     the domain ID from the request path
     * @param solutionId   the solution ID from the request path
     */
    public void publishNamespaceUpdatedEvent(NamespaceDTO namespaceDTO,
                                             String domainId, String solutionId) {
        publishEvent(namespaceDTO, "UPDATE", domainId, solutionId);
    }

    /**
     * Publishes a namespace DELETE event via the events-connector broadcast.
     *
     * @param namespaceDTO the deleted namespace DTO
     * @param domainId     the domain ID from the request path
     * @param solutionId   the solution ID from the request path
     */
    public void publishNamespaceDeletedEvent(NamespaceDTO namespaceDTO,
                                             String domainId, String solutionId) {
        publishEvent(namespaceDTO, "DELETE", domainId, solutionId);
    }

    /**
     * Publishes a namespace event to the events-connector.
     *
     * @param namespaceDTO the namespace DTO for the event payload
     * @param eventAction  the action type: CREATE, UPDATE, or DELETE
     * @param domainId     the domain ID from the request path
     * @param solutionId   the solution ID from the request path
     */
    private void publishEvent(NamespaceDTO namespaceDTO, String eventAction,
                              String domainId, String solutionId) {
        log.debug("Publishing namespace {} event: id={}, domainId={}, solutionId={}",
                eventAction, namespaceDTO.id(), domainId, solutionId);

        try {
            String url = infrastructureConfig.eventsConnector()
                    + "/" + domainId + "/" + solutionId + "/events";

            // Build event payload with action and namespace object
            Map<String, Object> eventPayload = buildEventPayload(namespaceDTO, eventAction);

            // Set required headers for events-connector
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("serviceName", NAMESPACES_SERVICE_NAME);
            headers.set("zoneId", ENVIRONMENT_GLOBAL_ZONE_ID);
            headers.set("contextId", DEFAULT_CONTEXT_ID);
            headers.set("serviceType", "UTILITY");
            headers.set("eventAction", eventAction);
            headers.set("X-Environment-Id", UUID.randomUUID().toString());
            headers.set("X-Environment-Name", BROKER_ENVIRONMENT_NAME);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(eventPayload, headers);

            restTemplate.postForEntity(url, request, Void.class);

            log.info("Namespace {} event published: id={}, domainId={}, solutionId={}",
                    eventAction, namespaceDTO.id(), domainId, solutionId);

        } catch (Exception e) {
            log.warn("Namespace {} event publication failed (best-effort): id={}, error={}",
                    eventAction, namespaceDTO.id(), e.getMessage());
        }
    }

    /**
     * Builds the event payload containing the namespace object and action type.
     *
     * @param namespaceDTO the namespace DTO
     * @param eventAction  the action type (CREATE, UPDATE, DELETE)
     * @return the event payload map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildEventPayload(NamespaceDTO namespaceDTO, String eventAction) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventAction", eventAction);
        payload.put("namespace", objectMapper.convertValue(namespaceDTO, Map.class));
        return payload;
    }
}
