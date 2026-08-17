// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.eventsconnector.client.SubscriptionServiceClient;
import org.sifunity.eventsconnector.exception.SubscriptionServiceException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SubscriptionQueryService.
 * Tests subscription querying with mocked client using contextUuid.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionQueryServiceTest {

    @Mock
    private SubscriptionServiceClient subscriptionServiceClient;

    @InjectMocks
    private SubscriptionQueryService subscriptionQueryService;

    @Test
    void shouldQuerySubscriptionsSuccessfully() {
        // Arrange
        String serviceName = "StudentPersonal";
        UUID contextUuid = UUID.randomUUID();

        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();

        List<SubscriptionDTO> expectedSubscriptions = List.of(
            new SubscriptionDTO(UUID.randomUUID(), queueId1, null, serviceName, null, null, SubscriptionType.EVENT, Instant.now()),
            new SubscriptionDTO(UUID.randomUUID(), queueId2, null, serviceName, null, null, SubscriptionType.EVENT, Instant.now())
        );

        when(subscriptionServiceClient.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), eq(contextUuid)))
            .thenReturn(expectedSubscriptions);

        // Act
        List<SubscriptionDTO> result = subscriptionQueryService.querySubscriptions("iowa", "production", serviceName, contextUuid);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result).isEqualTo(expectedSubscriptions);

        verify(subscriptionServiceClient).querySubscriptions("iowa", "production", serviceName, contextUuid);
    }

    @Test
    void shouldQuerySubscriptionsWithNullContextUuid() {
        // Arrange
        String serviceName = "StudentPersonal";

        when(subscriptionServiceClient.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), isNull()))
            .thenReturn(List.of());

        // Act
        List<SubscriptionDTO> result = subscriptionQueryService.querySubscriptions("iowa", "production", serviceName, null);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(subscriptionServiceClient).querySubscriptions("iowa", "production", serviceName, null);
    }

    @Test
    void shouldReturnEmptyListWhenNoSubscriptionsFound() {
        // Arrange
        String serviceName = "StudentPersonal";
        UUID contextUuid = UUID.randomUUID();

        when(subscriptionServiceClient.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), eq(contextUuid)))
            .thenReturn(List.of());

        // Act
        List<SubscriptionDTO> result = subscriptionQueryService.querySubscriptions("iowa", "production", serviceName, contextUuid);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(subscriptionServiceClient).querySubscriptions("iowa", "production", serviceName, contextUuid);
    }

    @Test
    void shouldPropagateSubscriptionServiceException() {
        // Arrange
        String serviceName = "StudentPersonal";
        UUID contextUuid = UUID.randomUUID();

        SubscriptionServiceException expectedException = new SubscriptionServiceException(
            "Subscription Service unavailable", new RuntimeException("Connection refused")
        );

        when(subscriptionServiceClient.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), eq(contextUuid)))
            .thenThrow(expectedException);

        // Act & Assert
        assertThatThrownBy(() -> subscriptionQueryService.querySubscriptions("iowa", "production", serviceName, contextUuid))
            .isInstanceOf(SubscriptionServiceException.class)
            .hasMessageContaining("Subscription Service unavailable");

        verify(subscriptionServiceClient).querySubscriptions("iowa", "production", serviceName, contextUuid);
    }

    @Test
    void shouldWrapUnexpectedExceptionInSubscriptionServiceException() {
        // Arrange
        String serviceName = "StudentPersonal";
        UUID contextUuid = UUID.randomUUID();

        when(subscriptionServiceClient.querySubscriptions(eq("iowa"), eq("production"), eq(serviceName), eq(contextUuid)))
            .thenThrow(new RuntimeException("Unexpected error"));

        // Act & Assert
        assertThatThrownBy(() -> subscriptionQueryService.querySubscriptions("iowa", "production", serviceName, contextUuid))
            .isInstanceOf(SubscriptionServiceException.class)
            .hasMessageContaining("Failed to query subscriptions from Subscription Service")
            .hasCauseInstanceOf(RuntimeException.class);

        verify(subscriptionServiceClient).querySubscriptions("iowa", "production", serviceName, contextUuid);
    }

    @Test
    void shouldThrowExceptionForNullServiceName() {
        // Act & Assert
        assertThatThrownBy(() -> subscriptionQueryService.querySubscriptions("iowa", "production", null, UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("serviceName must not be null or blank");

        verify(subscriptionServiceClient, never()).querySubscriptions(anyString(), anyString(), anyString(), any(UUID.class));
    }

    @Test
    void shouldThrowExceptionForBlankServiceName() {
        // Act & Assert
        assertThatThrownBy(() -> subscriptionQueryService.querySubscriptions("iowa", "production", "", UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("serviceName must not be null or blank");

        verify(subscriptionServiceClient, never()).querySubscriptions(anyString(), anyString(), anyString(), any(UUID.class));
    }
}
