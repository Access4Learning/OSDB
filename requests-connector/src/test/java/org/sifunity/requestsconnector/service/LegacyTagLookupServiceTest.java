// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.requestsconnector.client.EnvironmentServiceClient;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LegacyTagLookupService.
 *
 * <p>Validates Requirements 6.1, 6.2, 6.3, 6.4:</p>
 * <ul>
 *   <li>6.1: Query entity tag from Environment Service</li>
 *   <li>6.2: Return false (modern format) when tag absent</li>
 *   <li>6.3: Cache lookup results with configurable TTL</li>
 *   <li>6.4: Reflect tag changes after cache TTL expires</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LegacyTagLookupService Unit Tests")
class LegacyTagLookupServiceTest {

    @Mock
    private EnvironmentServiceClient environmentServiceClient;

    private static final String DOMAIN_ID = "testDomain";
    private static final String SOLUTION_ID = "testSolution";
    private static final String ENTITY_TYPE = "ENVIRONMENT";
    private static final String TAG_KEY = "SIF_LEGACY_MATRIX_PARAMS";

    private LegacyTagLookupService service;

    @BeforeEach
    void setUp() {
        // Use short TTL (100ms) for cache expiration tests
        service = new LegacyTagLookupService(environmentServiceClient, Duration.ofMillis(100));
    }

    @Test
    @DisplayName("Should return true when tag exists with value 'true'")
    void shouldReturnTrueWhenTagExistsWithValueTrue() {
        // Given
        UUID environmentId = UUID.randomUUID();
        when(environmentServiceClient.getEntityTagValue(
                DOMAIN_ID, SOLUTION_ID, ENTITY_TYPE, environmentId, TAG_KEY))
                .thenReturn(Optional.of("true"));

        // When
        boolean result = service.isLegacyEnvironment(DOMAIN_ID, SOLUTION_ID, environmentId);

        // Then
        assertThat(result).isTrue();
        verify(environmentServiceClient, times(1))
                .getEntityTagValue(DOMAIN_ID, SOLUTION_ID, ENTITY_TYPE, environmentId, TAG_KEY);
    }

    @Test
    @DisplayName("Should return false when tag is absent")
    void shouldReturnFalseWhenTagAbsent() {
        // Given
        UUID environmentId = UUID.randomUUID();
        when(environmentServiceClient.getEntityTagValue(
                DOMAIN_ID, SOLUTION_ID, ENTITY_TYPE, environmentId, TAG_KEY))
                .thenReturn(Optional.empty());

        // When
        boolean result = service.isLegacyEnvironment(DOMAIN_ID, SOLUTION_ID, environmentId);

        // Then
        assertThat(result).isFalse();
        verify(environmentServiceClient, times(1))
                .getEntityTagValue(DOMAIN_ID, SOLUTION_ID, ENTITY_TYPE, environmentId, TAG_KEY);
    }

    @Test
    @DisplayName("Should cache result and not re-query within TTL")
    void shouldCacheResultAndNotReQueryWithinTtl() {
        // Given
        UUID environmentId = UUID.randomUUID();
        when(environmentServiceClient.getEntityTagValue(
                DOMAIN_ID, SOLUTION_ID, ENTITY_TYPE, environmentId, TAG_KEY))
                .thenReturn(Optional.of("true"));

        // When - call twice with same environmentId
        boolean first = service.isLegacyEnvironment(DOMAIN_ID, SOLUTION_ID, environmentId);
        boolean second = service.isLegacyEnvironment(DOMAIN_ID, SOLUTION_ID, environmentId);

        // Then - client called only once (cache hit on second call)
        assertThat(first).isTrue();
        assertThat(second).isTrue();
        verify(environmentServiceClient, times(1))
                .getEntityTagValue(DOMAIN_ID, SOLUTION_ID, ENTITY_TYPE, environmentId, TAG_KEY);
    }

    @Test
    @DisplayName("Should refresh after TTL expires")
    void shouldRefreshAfterTtlExpires() throws InterruptedException {
        // Given
        UUID environmentId = UUID.randomUUID();
        when(environmentServiceClient.getEntityTagValue(
                DOMAIN_ID, SOLUTION_ID, ENTITY_TYPE, environmentId, TAG_KEY))
                .thenReturn(Optional.of("true"))
                .thenReturn(Optional.empty());

        // When - first call populates cache
        boolean first = service.isLegacyEnvironment(DOMAIN_ID, SOLUTION_ID, environmentId);
        assertThat(first).isTrue();

        // Wait for TTL to expire (100ms TTL + buffer)
        Thread.sleep(200);

        // Second call should re-query after TTL expiration
        boolean second = service.isLegacyEnvironment(DOMAIN_ID, SOLUTION_ID, environmentId);

        // Then - client called twice (cache expired, refreshed with new value)
        assertThat(second).isFalse();
        verify(environmentServiceClient, times(2))
                .getEntityTagValue(DOMAIN_ID, SOLUTION_ID, ENTITY_TYPE, environmentId, TAG_KEY);
    }

    @Test
    @DisplayName("Should return false when tag value is not 'true'")
    void shouldReturnFalseWhenTagValueIsNotTrue() {
        // Given
        UUID environmentId = UUID.randomUUID();
        when(environmentServiceClient.getEntityTagValue(
                DOMAIN_ID, SOLUTION_ID, ENTITY_TYPE, environmentId, TAG_KEY))
                .thenReturn(Optional.of("false"));

        // When
        boolean result = service.isLegacyEnvironment(DOMAIN_ID, SOLUTION_ID, environmentId);

        // Then
        assertThat(result).isFalse();
    }
}
