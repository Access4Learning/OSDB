// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.config.JacksonConfiguration;
import org.sifunity.servicesconnector.client.QueueServiceClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for {@code QueueServiceClient.findQueueOwner} response handling
 * (services-connector).
 *
 * <p>Mirrors the requests-connector test of the same name. Both modules implement the lookup
 * independently, so both need this coverage — the hazards are per-module, not shared:</p>
 * <ol>
 *   <li><strong>The response is SIF 3.7 wrapped.</strong> {@code GET .../queues/{id}} returns
 *       {@code {"queue": {...}}}. Deserializing straight into a flat DTO yields a null
 *       {@code ownerId}, the lookup returns empty, and — because the gate is fail-closed —
 *       <em>every</em> DELAYED request 404s. Loud rather than insecure, but it presents as
 *       "the fix broke all delayed requests", so it is pinned by a test.</li>
 *   <li><strong>This module is WebClient-based.</strong> A 404 must be caught as
 *       {@code WebClientResponseException.NotFound}, not the RestClient equivalent that
 *       responses-connector uses, so "queue absent" stays distinguishable from
 *       "queue-service unreachable" in the logs.</li>
 *   <li><strong>The wrapper record is intentionally minimal.</strong> It maps only
 *       {@code id} and {@code ownerId}, while the real {@code QueueDTO} carries many more
 *       fields. That only works while unknown properties are ignored, so Property E pins it.</li>
 * </ol>
 *
 * <p><strong>Validates: Requirements 2.2, 2.3, 2.8</strong></p>
 */
@Tag("delayed-queue-ownership-fix")
class QueueOwnerLookupWrapperPropertyTest {

    /**
     * Exchange strategies using the real production ObjectMapper (strict: unknown properties
     * fail). Sourced from the production config class so a strictness change there is reflected
     * here instead of silently diverging.
     *
     * <p>This wiring is what makes Property E meaningful. Built with the plain
     * {@code ClientResponse.create(status)} factory, responses decode with Spring's default
     * mapper, which <em>ignores</em> unknown properties — so Property E passed while production,
     * using the strict {@link JacksonConfiguration} bean, rejected every real queue response with
     * {@code Unrecognized field "name"} and 404'd every DELAYED request.</p>
     */
    private static final ExchangeStrategies PRODUCTION_STRATEGIES = ExchangeStrategies.builder()
            .codecs(configurer -> {
                ObjectMapper productionMapper = new JacksonConfiguration().objectMapper();
                configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(productionMapper));
                configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(productionMapper));
            })
            .build();

    private static QueueServiceClient clientReturning(ClientResponse response) {
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> Mono.just(response));
        return new QueueServiceClient(builder, "http://queue-service", "test-token");
    }

    private static ClientResponse jsonResponse(HttpStatus status, String body) {
        return ClientResponse.create(status, PRODUCTION_STRATEGIES)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    /**
     * Property A: the SIF 3.7 {@code {"queue": {...}}} wrapper is parsed and ownerId extracted.
     */
    @Property(tries = 20)
    @Label("Wrapped SIF 3.7 queue response yields the ownerId")
    void wrappedResponseYieldsOwnerId(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID ownerId) {

        String json = "{\"queue\":{\"id\":\"" + queueId + "\",\"ownerId\":\"" + ownerId + "\"}}";

        Optional<UUID> owner = clientReturning(jsonResponse(HttpStatus.OK, json))
                .findQueueOwner("domain1", "solution1", queueId);

        assertThat(owner)
                .as("The {\"queue\":{...}} wrapper must be parsed — a flat DTO parse would yield empty "
                        + "and 404 every DELAYED request")
                .contains(ownerId);
    }

    /**
     * Property B: a 404 from queue-service yields empty (queue does not exist).
     */
    @Property(tries = 20)
    @Label("404 from queue-service yields empty (fail-closed input)")
    void notFoundYieldsEmpty(@ForAll("uuids") UUID queueId) {
        assertThat(clientReturning(jsonResponse(HttpStatus.NOT_FOUND, "{\"error\":{\"code\":404}}"))
                .findQueueOwner("domain1", "solution1", queueId))
                .isEmpty();
    }

    /**
     * Property C: a body missing {@code ownerId} yields empty rather than a bogus owner.
     */
    @Property(tries = 20)
    @Label("Malformed body (no ownerId) yields empty")
    void malformedBodyYieldsEmpty(@ForAll("uuids") UUID queueId) {
        String json = "{\"queue\":{\"id\":\"" + queueId + "\"}}";

        assertThat(clientReturning(jsonResponse(HttpStatus.OK, json))
                .findQueueOwner("domain1", "solution1", queueId))
                .isEmpty();
    }

    /**
     * Property D: a 5xx from queue-service yields empty (fail closed, never a guess).
     */
    @Property(tries = 20)
    @Label("5xx from queue-service yields empty (fail-closed input)")
    void serverErrorYieldsEmpty(@ForAll("uuids") UUID queueId) {
        assertThat(clientReturning(jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR, "{}"))
                .findQueueOwner("domain1", "solution1", queueId))
                .isEmpty();
    }

    /**
     * Property E: a realistic full queue body — with all the fields the minimal wrapper record
     * does NOT map — still parses. The record only declares {@code id} and {@code ownerId}, so
     * this pins the unknown-property tolerance it silently depends on.
     */
    @Property(tries = 20)
    @Label("Full queue body with unmapped fields still yields the ownerId")
    void fullBodyWithUnmappedFieldsYieldsOwnerId(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID ownerId,
            @ForAll("uuids") UUID solutionUuid) {

        String json = "{\"queue\":{"
                + "\"id\":\"" + queueId + "\","
                + "\"ownerId\":\"" + ownerId + "\","
                + "\"name\":\"cert-queue\","
                + "\"created\":\"2026-07-29T12:00:00Z\","
                + "\"lastAccessed\":\"2026-07-29T12:05:00Z\","
                + "\"lastModified\":\"2026-07-29T12:05:00Z\","
                + "\"maxConcurrentConnections\":4,"
                + "\"maxRetries\":3,"
                + "\"polling\":\"IMMEDIATE\","
                + "\"queueUri\":\"http://queue-service/queues/" + queueId + "\","
                + "\"retryDelayMs\":5000,"
                + "\"solutionUuid\":\"" + solutionUuid + "\""
                + "}}";

        assertThat(clientReturning(jsonResponse(HttpStatus.OK, json))
                .findQueueOwner("domain1", "solution1", queueId))
                .as("Unmapped QueueDTO fields must be ignored, not cause a parse failure")
                .contains(ownerId);
    }

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
}
