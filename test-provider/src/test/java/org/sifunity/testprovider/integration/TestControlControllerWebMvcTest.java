// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.testprovider.config.ProviderConfig;
import org.sifunity.testprovider.controller.TestControlController;
import org.sifunity.testprovider.dto.ConfigureRequest;
import org.sifunity.testprovider.service.DelayedPollRunner;
import org.sifunity.testprovider.service.RuntimeConfigService;
import org.sifunity.testprovider.service.RuntimeConfigServiceImpl;
import org.sifunity.testprovider.service.TestDataGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * {@code @WebMvcTest}/MockMvc integration tests for {@link TestControlController}.
 *
 * <p>The controller slice is wired with the real {@link RuntimeConfigServiceImpl} (backed by a fresh
 * {@link ProviderConfig}) and a real {@link DelayedPollRunner} whose outbound {@link RestClient} is
 * stubbed by a {@link MockRestServiceServer}. This exercises the full controller → service → config
 * path without a live broker.</p>
 *
 * <p>The slice does not apply {@code server.servlet.context-path} (/sif/provider), so paths are used
 * directly under {@code /test-control}.</p>
 *
 * <p>Covers the masked status read, a full configure round-trip with masked fields, the per-field 400
 * for a missing required field, poll gating when unconfigured, and a triggered poll reporting
 * {@code polled: true} once a complete credential set and {@code queueId} are configured.</p>
 *
 * Validates: Requirements 1.1, 1.7, 2.1, 2.2, 3.1, 4.8, 6.1, 6.2
 */
@WebMvcTest(TestControlController.class)
@Import(TestControlControllerWebMvcTest.TestConfig.class)
class TestControlControllerWebMvcTest {

    private static final String CONFIGURE = "/test-control/configure";
    private static final String STATUS = "/test-control/status";
    private static final String POLL = "/test-control/poll";

    private static final String GATEWAY = "http://localhost:8080";
    private static final String DOMAIN = "d-1";
    private static final String SOLUTION = "s-1";
    private static final String QUEUE = "q-123";
    private static final String SESSION_TOKEN = "session-token-1234";
    private static final String SHARED_SECRET = "shared-secret-5678";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProviderConfig providerConfig;

    @Autowired
    private MockRestServiceServer brokerServer;

    @BeforeEach
    void resetState() {
        // The ProviderConfig singleton is shared across the slice context; reset it to a clean,
        // unconfigured baseline before every test so cases do not leak state into one another.
        providerConfig.setGatewayUrl(GATEWAY);
        providerConfig.setSessionToken(null);
        providerConfig.setSharedSecret(null);
        providerConfig.setDomainId(null);
        providerConfig.setSolutionId(null);
        providerConfig.setQueueId(null);
        brokerServer.reset();
    }

    private String json(ConfigureRequest request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    @Test
    @DisplayName("GET /test-control/status returns masked secrets, never the raw values")
    void statusReturnsMaskedSecrets() throws Exception {
        // Configure first so there are secrets to mask.
        mockMvc.perform(post(CONFIGURE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ConfigureRequest(GATEWAY, SESSION_TOKEN, SHARED_SECRET, DOMAIN, SOLUTION, null))))
                .andExpect(status().isOk());

        mockMvc.perform(get(STATUS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.sessionTokenMasked").value("sess****1234"))
                .andExpect(jsonPath("$.sharedSecretMasked").value("shar****5678"))
                // raw secrets must never appear anywhere in the serialized response
                .andExpect(content().string(not(containsString(SESSION_TOKEN))))
                .andExpect(content().string(not(containsString(SHARED_SECRET))));
    }

    @Test
    @DisplayName("POST /test-control/configure full body -> configured:true with masked fields")
    void configureFullBodyReturnsConfiguredTrueWithMaskedFields() throws Exception {
        mockMvc.perform(post(CONFIGURE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ConfigureRequest(GATEWAY, SESSION_TOKEN, SHARED_SECRET, DOMAIN, SOLUTION, QUEUE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.gatewayUrl").value(GATEWAY))
                .andExpect(jsonPath("$.domainId").value(DOMAIN))
                .andExpect(jsonPath("$.solutionId").value(SOLUTION))
                .andExpect(jsonPath("$.queueIdConfigured").value(true))
                .andExpect(jsonPath("$.delayedPollingEnabled").value(true))
                .andExpect(jsonPath("$.sessionTokenMasked").value("sess****1234"))
                .andExpect(jsonPath("$.sharedSecretMasked").value("shar****5678"))
                .andExpect(content().string(not(containsString(SESSION_TOKEN))))
                .andExpect(content().string(not(containsString(SHARED_SECRET))));
    }

    @Test
    @DisplayName("POST /test-control/configure missing a required field -> 400 identifying the field")
    void configureMissingRequiredFieldReturns400() throws Exception {
        // Omit solutionId.
        mockMvc.perform(post(CONFIGURE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ConfigureRequest(GATEWAY, SESSION_TOKEN, SHARED_SECRET, DOMAIN, null, QUEUE))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.solutionId").exists());
    }

    @Test
    @DisplayName("POST /test-control/poll when not configured -> { polled:false, messageProcessed:false }")
    void pollWhenNotConfiguredReportsDisabled() throws Exception {
        mockMvc.perform(post(POLL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.polled").value(false))
                .andExpect(jsonPath("$.messageProcessed").value(false));
        // No expectations were registered on the broker server, so no queue interaction must occur.
        brokerServer.verify();
    }

    @Test
    @DisplayName("After configuring complete set + queueId, delayedPollingEnabled is true and a triggered poll returns polled:true")
    void configureCompleteThenTriggeredPollReturnsPolledTrue() throws Exception {
        // Configure a complete credential set plus a queueId -> delayed polling enabled.
        mockMvc.perform(post(CONFIGURE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ConfigureRequest(GATEWAY, SESSION_TOKEN, SHARED_SECRET, DOMAIN, SOLUTION, QUEUE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delayedPollingEnabled").value(true));

        // Stub the broker queue GET for the one poll cycle (empty queue -> a cycle ran, nothing processed).
        String queueUrl = GATEWAY + "/" + DOMAIN + "/" + SOLUTION + "/queues/" + QUEUE + "/messages";
        brokerServer.expect(requestTo(queueUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        mockMvc.perform(post(POLL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.polled").value(true))
                .andExpect(jsonPath("$.messageProcessed").value(false));

        brokerServer.verify();
    }

    /**
     * Wires the real service + runner into the controller slice. The runner's {@link RestClient} is built
     * from a {@link RestClient.Builder} that has been bound to a {@link MockRestServiceServer}, so all
     * outbound broker calls are intercepted and asserted by the test.
     */
    @TestConfiguration
    static class TestConfig {

        @Bean
        ProviderConfig providerConfig() {
            return new ProviderConfig();
        }

        @Bean
        RestClient.Builder brokerRestClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        MockRestServiceServer brokerServer(RestClient.Builder brokerRestClientBuilder) {
            return MockRestServiceServer.bindTo(brokerRestClientBuilder).build();
        }

        @Bean
        DelayedPollRunner delayedPollRunner(ProviderConfig providerConfig,
                                            RestClient.Builder brokerRestClientBuilder,
                                            MockRestServiceServer brokerServer) {
            // Depending on brokerServer ensures the mock is bound to the builder before the client is built.
            return new DelayedPollRunner(providerConfig, new TestDataGenerator(), new ObjectMapper(),
                    brokerRestClientBuilder.build());
        }

        @Bean
        RuntimeConfigService runtimeConfigService(ProviderConfig providerConfig, DelayedPollRunner delayedPollRunner) {
            return new RuntimeConfigServiceImpl(providerConfig, Optional.of(delayedPollRunner));
        }
    }
}
