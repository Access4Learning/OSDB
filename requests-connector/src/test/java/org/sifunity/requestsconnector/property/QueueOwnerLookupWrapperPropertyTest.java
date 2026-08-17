// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.config.JacksonConfiguration;
import org.sifunity.requestsconnector.client.QueueServiceClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
 * Property tests for {@code QueueServiceClient.findQueueOwner} response handling.
 *
 * <p>These tests exist because of three specific hazards:</p>
 * <ol>
 *   <li><strong>The response is SIF 3.7 wrapped.</strong> {@code GET .../queues/{id}} returns
 *       {@code {"queue": {...}}}. Deserializing straight into a flat DTO yields a null
 *       {@code ownerId}, the lookup returns empty, and — because the gate is fail-closed —
 *       <em>every</em> DELAYED request 404s.</li>
 *   <li><strong>The wrapper record maps only two fields</strong> ({@code id}, {@code ownerId})
 *       while queue-service sends the full queue object. See the decoder note below.</li>
 *   <li><strong>The module is WebClient-based.</strong> A 404 must be caught as
 *       {@code WebClientResponseException.NotFound} so that "queue absent" stays
 *       distinguishable from "queue-service unreachable" in the logs.</li>
 * </ol>
 *
 * <h2>Why these tests decode with the production ObjectMapper</h2>
 * <p>An earlier version of this test built responses with {@code ClientResponse.create(status)},
 * which decodes using Spring's <em>default</em> mapper — and that mapper ignores unknown
 * properties. The tests therefore passed while production rejected every real response with
 * {@code Unrecognized field "name"} and 404'd every DELAYED request.</p>
 *
 * <p>Production is strict: {@link JacksonConfiguration} contributes an {@code ObjectMapper} bean
 * that explicitly enables {@code FAIL_ON_UNKNOWN_PROPERTIES}, Boot wires that bean into the
 * WebClient Jackson codec, and it overrides the {@code fail-on-unknown-properties: false} setting
 * in {@code application.yml} (that setting only tunes Boot's auto-configured mapper, which the bean
 * replaces). These tests decode through that same configuration class so the wire contract is
 * exercised as deployed rather than under friendlier defaults.</p>
 *
 * <p><strong>Validates: Requirements 2.2, 2.3</strong></p>
 */
@Tag("delayed-queue-ownership-fix")
class QueueOwnerLookupWrapperPropertyTest {

    /**
     * Exchange strategies using the real production ObjectMapper (strict: unknown properties
     * fail). Sourced from the production config class so that a change in strictness there is
     * reflected here instead of silently diverging.
     */
    private static final ExchangeStrategies PRODUCTION_STRATEGIES = ExchangeStrategies.builder()
            .codecs(configurer -> {
                ObjectMapper productionMapper = new JacksonConfiguration().objectMapper();
                configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(productionMapper));
                configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(productionMapper));
            })
            .build();

    private static ClientResponse jsonResponse(HttpStatusCode status, String body) {
        return ClientResponse.create(status, PRODUCTION_STRATEGIES)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static QueueServiceClient clientReturning(ClientResponse response) {
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> Mono.just(response));
        return new QueueServiceClient(builder, "http://queue-service", "test-token");
    }

    /**
     * The payload {@code queue-service} actually returns: the SIF 3.7 wrapper around the FULL
     * queue object, not just the two fields this client models.
     */
    private static String realQueueServicePayload(UUID queueId, UUID ownerId) {
        return """
               {"queue":{
                 "id":"%s",
                 "name":"consumer-queue",
                 "ownerId":"%s",
                 "polling":"IMMEDIATE",
                 "queueUri":"http://gateway/domain1/solution1/queues/%s/messages",
                 "maxConcurrentConnections":1,
                 "created":"2026-07-29T12:00:00Z",
                 "lastAccessed":"2026-07-29T12:05:00Z",
                 "lastModified":"2026-07-29T12:04:00Z",
                 "messageCount":3
               }}
               """.formatted(queueId, ownerId, queueId);
    }

    /**
     * Property A: the real, full SIF 3.7 queue payload is parsed and ownerId extracted under the
     * production strict mapper.
     *
     * <p>This is the regression guard for the production outage: the wrapper record must tolerate
     * every field queue-service sends, not only the ones it models.</p>
     */
    @Property(tries = 20)
    @Label("Full SIF 3.7 queue response (all fields) yields the ownerId")
    void fullResponseYieldsOwnerId(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID ownerId) {

        Optional<UUID> owner = clientReturning(
                jsonResponse(HttpStatus.OK, realQueueServicePayload(queueId, ownerId)))
                .findQueueOwner("domain1", "solution1", queueId);

        assertThat(owner)
                .as("The full queue payload must parse under the strict production mapper. If unknown "
                        + "fields are not ignored the lookup returns empty and the fail-closed gate "
                        + "404s every DELAYED request.")
                .contains(ownerId);
    }

    /**
     * Property A2: a field added to the queue object in a future release must not break the lookup.
     */
    @Property(tries = 20)
    @Label("A newly added, unmodelled queue field does not break the lookup")
    void unknownFutureFieldIsTolerated(
            @ForAll("uuids") UUID queueId,
            @ForAll("uuids") UUID ownerId) {

        String json = """
                      {"queue":{"id":"%s","ownerId":"%s","someFieldAddedNextQuarter":"surprise"}}
                      """.formatted(queueId, ownerId);

        assertThat(clientReturning(jsonResponse(HttpStatus.OK, json))
                .findQueueOwner("domain1", "solution1", queueId))
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

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
}
