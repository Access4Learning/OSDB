// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.testprovider.config.ProviderConfig;
import org.sifunity.testprovider.dto.PollResult;
import org.sifunity.testprovider.service.DelayedPollRunner;
import org.sifunity.testprovider.service.TestDataGenerator;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link DelayedPollRunner#triggerOnce()} covering the four poll outcomes.
 *
 * <p>The runner builds its broker calls through an injected {@link RestClient}; here that client is
 * backed by a {@link MockRestServiceServer} so the queue GET and responses POST can be stubbed and
 * asserted without a live broker.</p>
 *
 * <p>Covers: disabled config performs no queue call and reports {@code (false,false)}; a message present
 * yields {@code messageProcessed:true}; an empty queue yields {@code messageProcessed:false}; a transport
 * error is contained as {@code (true,false)}.</p>
 *
 * Validates: Requirements 4.2, 4.3, 4.4
 */
class DelayedPollRunnerTriggerOnceTest {

    private static final String GATEWAY = "http://localhost:8080";
    private static final String DOMAIN = "d-1";
    private static final String SOLUTION = "s-1";
    private static final String QUEUE = "q-123";
    private static final String QUEUE_URL = GATEWAY + "/" + DOMAIN + "/" + SOLUTION + "/queues/" + QUEUE + "/messages";
    private static final String RESPONSES_URL = GATEWAY + "/" + DOMAIN + "/" + SOLUTION + "/responses";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    /** Build a fully-credentialed config (complete creds + queueId) so delayed polling is enabled. */
    private ProviderConfig enabledConfig() {
        ProviderConfig config = new ProviderConfig();
        config.setGatewayUrl(GATEWAY);
        config.setSessionToken("session-token-value");
        config.setSharedSecret("shared-secret-value");
        config.setDomainId(DOMAIN);
        config.setSolutionId(SOLUTION);
        config.setQueueId(QUEUE);
        return config;
    }

    private DelayedPollRunner runnerFor(ProviderConfig config) {
        return new DelayedPollRunner(config, new TestDataGenerator(), objectMapper, restClientBuilder.build());
    }

    @Test
    @DisplayName("Disabled — blank queueId: no queue call, returns (false,false)")
    void disabledBlankQueueId_noQueueCall() {
        ProviderConfig config = enabledConfig();
        config.setQueueId("   "); // blank => delayed polling disabled

        PollResult result = runnerFor(config).triggerOnce();

        assertThat(result.polled()).isFalse();
        assertThat(result.messageProcessed()).isFalse();
        server.verify(); // zero expectations registered => zero calls made
    }

    @Test
    @DisplayName("Disabled — incomplete credentials: no queue call, returns (false,false)")
    void disabledIncompleteCredentials_noQueueCall() {
        ProviderConfig config = enabledConfig();
        config.setSessionToken(null); // incomplete credentials => disabled

        PollResult result = runnerFor(config).triggerOnce();

        assertThat(result.polled()).isFalse();
        assertThat(result.messageProcessed()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("Enabled + message present: polls queue, submits response, returns (true,true)")
    void enabledWithMessage_messageProcessed() {
        server.expect(once(), requestTo(QUEUE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .body("{\"TESTOBJECT\":{\"RefId\":\"abc\"}}")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("messageid", "msg-1")
                        .header("serviceName", "TESTOBJECT")
                        .header("zoneId", "zone-1")
                        .header("contextId", "ctx-1")
                        .header("queueId", "consumer-q")
                        .header("requestId", "req-1")
                        .header("relativeServicePath", "TESTOBJECT"));

        server.expect(once(), requestTo(RESPONSES_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED));

        PollResult result = runnerFor(enabledConfig()).triggerOnce();

        assertThat(result.polled()).isTrue();
        assertThat(result.messageProcessed()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("Enabled + empty queue (204): polls queue, returns (true,false)")
    void enabledEmptyQueue_notProcessed() {
        server.expect(once(), requestTo(QUEUE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        PollResult result = runnerFor(enabledConfig()).triggerOnce();

        assertThat(result.polled()).isTrue();
        assertThat(result.messageProcessed()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("Enabled + transport error (500): error contained, returns (true,false)")
    void enabledTransportError_contained() {
        server.expect(once(), requestTo(QUEUE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        PollResult result = runnerFor(enabledConfig()).triggerOnce();

        assertThat(result.polled()).isTrue();
        assertThat(result.messageProcessed()).isFalse();
        server.verify();
    }
}
