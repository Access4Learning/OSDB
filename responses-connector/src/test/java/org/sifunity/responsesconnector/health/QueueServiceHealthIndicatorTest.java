// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.health;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for QueueServiceHealthIndicator.
 *
 * Requirements: 13.2 (Observability and Logging)
 */
class QueueServiceHealthIndicatorTest {

    private WireMockServer wireMockServer;
    private QueueServiceHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));

        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .build();

        healthIndicator = new QueueServiceHealthIndicator(restClient);
        ReflectionTestUtils.setField(healthIndicator, "queueServiceUrl", wireMockServer.baseUrl());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldReturnUpWhenQueueServiceIsHealthy() {
        wireMockServer.stubFor(get(urlEqualTo("/actuator/health"))
            .willReturn(aResponse().withStatus(200)));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("service", "queue-service");
        assertThat(health.getDetails()).containsEntry("url", wireMockServer.baseUrl());
    }

    @Test
    void shouldReturnDownWhenQueueServiceReturnsNon2xxStatus() {
        wireMockServer.stubFor(get(urlEqualTo("/actuator/health"))
            .willReturn(aResponse().withStatus(503)));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("service", "queue-service");
        assertThat(health.getDetails()).containsEntry("url", wireMockServer.baseUrl());
        assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    void shouldReturnDownWhenQueueServiceIsUnreachable() {
        wireMockServer.stop(); // Make it unreachable

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("service", "queue-service");
        assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    void shouldIncludeErrorDetailsInDownStatus() {
        wireMockServer.stubFor(get(urlEqualTo("/actuator/health"))
            .willReturn(aResponse().withStatus(500)));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("service", "queue-service");
        assertThat(health.getDetails()).containsKey("error");
    }
}
