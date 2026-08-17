// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.ArgumentCaptor;
import org.sifunity.broker.environment.service.QueueServiceClient;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based test for queue list request construction.
 *
 * <p><strong>Property 1: Queue list request construction from environment entity</strong></p>
 *
 * <p>For any environment with a valid solutionUuid (resolving to a Solution with a solutionId
 * and a Domain with a domainId), the QueueServiceClient SHALL construct a GET request to
 * {@code {baseUrl}/{domainId}/{solutionId}/queues} with {@code X-Environment-Id} set to the
 * environment's UUID and {@code X-Environment-Fingerprint} set to {@code "broker-services"}.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2</strong></p>
 */
class QueueListRequestConstructionPropertyTest {

    private static final String BASE_URL = "http://queue-service:8080";

    private RestTemplate restTemplate;
    private QueueServiceClient queueServiceClient;

    @BeforeProperty
    void setUp() {
        restTemplate = mock(RestTemplate.class);

        RestTemplateBuilder restTemplateBuilder = mock(RestTemplateBuilder.class);
        when(restTemplateBuilder.setConnectTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);

        ObjectMapper objectMapper = new ObjectMapper();

        queueServiceClient = new QueueServiceClient(
                BASE_URL,
                restTemplateBuilder,
                5000,
                10000,
                objectMapper);
    }

    /**
     * Property 1: Queue list request construction from environment entity
     *
     * <p><strong>Validates: Requirements 1.1, 1.2</strong></p>
     *
     * <p>Generates random domainId/solutionId/environmentId combinations, mocks RestTemplate,
     * and verifies the URL pattern and headers are correctly constructed.</p>
     */
    @Property(tries = 20)
    @Tag("Feature: environment-delete-queue-cleanup, Property 1: Queue list request construction")
    void queueListRequestConstructedCorrectly(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("environmentIds") UUID environmentId) {

        // Reset mock to isolate each try from previous invocations
        reset(restTemplate);

        // Mock RestTemplate.exchange() to return empty queue list so method completes
        String emptyResponse = "{\"queues\":{\"queue\":[]}}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(emptyResponse, HttpStatus.OK));

        // Call the method under test
        queueServiceClient.deleteQueuesForEnvironment(environmentId, domainId, solutionId);

        // Capture the arguments passed to restTemplate.exchange()
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Void>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        verify(restTemplate, times(1)).exchange(urlCaptor.capture(), eq(HttpMethod.GET), entityCaptor.capture(), eq(String.class));

        // Verify URL is {baseUrl}/{domainId}/{solutionId}/queues
        String expectedUrl = BASE_URL + "/" + domainId + "/" + solutionId + "/queues";
        assertThat(urlCaptor.getValue()).isEqualTo(expectedUrl);

        // Verify X-Environment-Id header equals environment UUID
        HttpEntity<Void> capturedEntity = entityCaptor.getValue();
        assertThat(capturedEntity.getHeaders().getFirst("X-Environment-Id"))
                .isEqualTo(environmentId.toString());

        // Verify X-Environment-Fingerprint header equals "broker-services"
        assertThat(capturedEntity.getHeaders().getFirst("X-Environment-Fingerprint"))
                .isEqualTo("broker-services");
    }

    // ========== Arbitraries ==========

    /**
     * Generates random alphanumeric domain IDs (3–20 characters).
     */
    @Provide
    Arbitrary<String> domainIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(3)
                .ofMaxLength(20);
    }

    /**
     * Generates random alphanumeric solution IDs (3–20 characters).
     */
    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(3)
                .ofMaxLength(20);
    }

    /**
     * Generates random UUIDs for environment IDs.
     */
    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
}
