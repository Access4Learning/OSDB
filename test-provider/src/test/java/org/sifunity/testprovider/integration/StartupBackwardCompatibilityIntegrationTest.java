// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.integration;

import org.junit.jupiter.api.Test;
import org.sifunity.testprovider.TestProviderApplication;
import org.sifunity.testprovider.config.ProviderConfig;
import org.sifunity.testprovider.service.DelayedPollRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying backward-compatible startup and the unchanged IMMEDIATE choreography.
 *
 * <p>Boots the full {@link TestProviderApplication} on a random port with the embedded servlet context
 * path ({@code /sif/provider}) applied, and asserts:</p>
 * <ul>
 *   <li><strong>Startup binding works</strong> — {@link ProviderConfig} is populated from
 *       {@code application.yml}/env at process start (default {@code gatewayUrl} present), so existing
 *       startup configuration keeps working (Requirements 5.1, 5.4).</li>
 *   <li><strong>No background polling</strong> — the app declares no {@code @EnableScheduling} and the
 *       {@link DelayedPollRunner} is a plain on-demand component that declares no {@code @Scheduled}
 *       method, so no poll cycle runs without an explicit trigger (Requirement 5.2).</li>
 *   <li><strong>IMMEDIATE endpoints stay public</strong> — {@code POST/GET /{serviceName}} respond with
 *       HTTP 200 without any credential and while the runtime configuration is empty
 *       (Requirements 7.1, 7.2).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StartupBackwardCompatibilityIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProviderConfig providerConfig;

    @Autowired
    private DelayedPollRunner delayedPollRunner;

    /** Embedded server applies server.servlet.context-path, so all calls are prefixed with it. */
    private String url(String path) {
        return "http://localhost:" + port + "/sif/provider" + path;
    }

    // ---------------------------------------------------------------------
    // Requirement 5.1 / 5.4 — startup binding populates ProviderConfig
    // ---------------------------------------------------------------------

    @Test
    void startupConfigBindsProviderConfigFromApplicationYml() {
        // gatewayUrl is bound from application.yml (default http://localhost:8080) — startup binding works.
        assertThat(providerConfig.getGatewayUrl()).isEqualTo("http://localhost:8080");

        // With no credentials supplied via env, the runtime configuration is "empty": delayed polling
        // cannot be enabled and no complete credential set is present.
        assertThat(providerConfig.hasCompleteCredentials()).isFalse();
        assertThat(providerConfig.getQueueId()).isNull();
    }

    // ---------------------------------------------------------------------
    // Requirement 5.2 — no background polling occurs without an explicit trigger
    // ---------------------------------------------------------------------

    @Test
    void noBackgroundSchedulingIsConfigured() {
        // The application must not enable Spring scheduling.
        assertThat(TestProviderApplication.class.isAnnotationPresent(EnableScheduling.class))
                .as("TestProviderApplication must not enable scheduling")
                .isFalse();

        // The poll runner is present as a plain on-demand component...
        assertThat(delayedPollRunner)
                .as("DelayedPollRunner bean must be present (always instantiated, no @ConditionalOnProperty)")
                .isNotNull();

        // ...and owns no @Scheduled task — it runs only on explicit trigger.
        for (Method method : DelayedPollRunner.class.getDeclaredMethods()) {
            assertThat(method.isAnnotationPresent(Scheduled.class))
                    .as("DelayedPollRunner.%s must not be @Scheduled", method.getName())
                    .isFalse();
        }
    }

    // ---------------------------------------------------------------------
    // Requirement 7.1 / 7.2 — IMMEDIATE endpoints respond without credentials,
    // while the runtime configuration is empty
    // ---------------------------------------------------------------------

    @Test
    void immediatePostRespondsWithoutCredentials() {
        // No auth header set; runtime config has no credentials.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{\"name\":\"test\"}", headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(url("/StudentPersonals"), request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getStatusCode().value()).isNotEqualTo(401);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void immediateGetRespondsWithoutCredentials() {
        // No auth header set; runtime config has no credentials.
        ResponseEntity<Map> response =
                restTemplate.getForEntity(url("/StudentPersonals"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getStatusCode().value()).isNotEqualTo(401);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void immediateEndpointsStayPublicWhileConfigEmpty() {
        // Re-assert the precondition: configuration is empty at this point (no configure call made).
        assertThat(providerConfig.hasCompleteCredentials()).isFalse();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> getRequest = new HttpEntity<>(headers);

        ResponseEntity<Map> getResponse = restTemplate.exchange(
                url("/SchoolInfos"), HttpMethod.GET, getRequest, Map.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
