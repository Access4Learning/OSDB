// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EventDistributionResponse DTO.
 * Tests factory methods and status determination logic.
 */
class EventDistributionResponseTest {

    @Test
    void shouldCreateResponseWithAllFields() {
        // Given
        String messageId = "msg-123";
        Instant timestamp = Instant.now();
        int subscriberCount = 3;
        DeliveryStatus status = DeliveryStatus.SUCCESS;
        List<QueueDeliveryResult> results = List.of(
            QueueDeliveryResult.success("queue-1"),
            QueueDeliveryResult.success("queue-2"),
            QueueDeliveryResult.success("queue-3")
        );

        // When
        EventDistributionResponse response = new EventDistributionResponse(
            messageId, timestamp, subscriberCount, status, results
        );

        // Then
        assertThat(response.messageId()).isEqualTo(messageId);
        assertThat(response.timestamp()).isEqualTo(timestamp);
        assertThat(response.subscriberCount()).isEqualTo(subscriberCount);
        assertThat(response.deliveryStatus()).isEqualTo(status);
        assertThat(response.deliveryResults()).hasSize(3);
    }

    @Test
    void shouldDefaultDeliveryResultsToEmptyListWhenNull() {
        // Given
        String messageId = "msg-123";
        Instant timestamp = Instant.now();

        // When
        EventDistributionResponse response = new EventDistributionResponse(
            messageId, timestamp, 0, DeliveryStatus.NO_SUBSCRIBERS, null
        );

        // Then
        assertThat(response.deliveryResults()).isNotNull();
        assertThat(response.deliveryResults()).isEmpty();
    }

    @Test
    void shouldCreateNoSubscribersResponse() {
        // Given
        String messageId = "msg-123";
        Instant timestamp = Instant.now();

        // When
        EventDistributionResponse response = EventDistributionResponse.noSubscribers(messageId, timestamp);

        // Then
        assertThat(response.messageId()).isEqualTo(messageId);
        assertThat(response.timestamp()).isEqualTo(timestamp);
        assertThat(response.subscriberCount()).isZero();
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.NO_SUBSCRIBERS);
        assertThat(response.deliveryResults()).isEmpty();
    }

    @Test
    void shouldDetermineSuccessStatusWhenAllQueuesSucceed() {
        // Given
        String messageId = "msg-123";
        Instant timestamp = Instant.now();
        List<QueueDeliveryResult> results = List.of(
            QueueDeliveryResult.success("queue-1"),
            QueueDeliveryResult.success("queue-2"),
            QueueDeliveryResult.success("queue-3")
        );

        // When
        EventDistributionResponse response = EventDistributionResponse.fromResults(
            messageId, timestamp, 3, results
        );

        // Then
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(response.subscriberCount()).isEqualTo(3);
        assertThat(response.deliveryResults()).hasSize(3);
    }

    @Test
    void shouldDeterminePartialStatusWhenSomeQueuesSucceed() {
        // Given
        String messageId = "msg-123";
        Instant timestamp = Instant.now();
        List<QueueDeliveryResult> results = List.of(
            QueueDeliveryResult.success("queue-1"),
            QueueDeliveryResult.failure("queue-2", "Connection timeout"),
            QueueDeliveryResult.success("queue-3")
        );

        // When
        EventDistributionResponse response = EventDistributionResponse.fromResults(
            messageId, timestamp, 3, results
        );

        // Then
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.PARTIAL);
        assertThat(response.subscriberCount()).isEqualTo(3);
        assertThat(response.deliveryResults()).hasSize(3);
    }

    @Test
    void shouldDetermineFailedStatusWhenAllQueuesFail() {
        // Given
        String messageId = "msg-123";
        Instant timestamp = Instant.now();
        List<QueueDeliveryResult> results = List.of(
            QueueDeliveryResult.failure("queue-1", "Service unavailable"),
            QueueDeliveryResult.failure("queue-2", "Connection timeout"),
            QueueDeliveryResult.failure("queue-3", "Queue not found")
        );

        // When
        EventDistributionResponse response = EventDistributionResponse.fromResults(
            messageId, timestamp, 3, results
        );

        // Then
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(response.subscriberCount()).isEqualTo(3);
        assertThat(response.deliveryResults()).hasSize(3);
    }

    @Test
    void shouldReturnNoSubscribersWhenResultsAreEmpty() {
        // Given
        String messageId = "msg-123";
        Instant timestamp = Instant.now();
        List<QueueDeliveryResult> results = List.of();

        // When
        EventDistributionResponse response = EventDistributionResponse.fromResults(
            messageId, timestamp, 0, results
        );

        // Then
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.NO_SUBSCRIBERS);
        assertThat(response.subscriberCount()).isZero();
        assertThat(response.deliveryResults()).isEmpty();
    }

    @Test
    void shouldHandleSingleSuccessfulQueue() {
        // Given
        String messageId = "msg-123";
        Instant timestamp = Instant.now();
        List<QueueDeliveryResult> results = List.of(
            QueueDeliveryResult.success("queue-1")
        );

        // When
        EventDistributionResponse response = EventDistributionResponse.fromResults(
            messageId, timestamp, 1, results
        );

        // Then
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(response.subscriberCount()).isEqualTo(1);
        assertThat(response.deliveryResults()).hasSize(1);
    }

    @Test
    void shouldHandleSingleFailedQueue() {
        // Given
        String messageId = "msg-123";
        Instant timestamp = Instant.now();
        List<QueueDeliveryResult> results = List.of(
            QueueDeliveryResult.failure("queue-1", "Error")
        );

        // When
        EventDistributionResponse response = EventDistributionResponse.fromResults(
            messageId, timestamp, 1, results
        );

        // Then
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(response.subscriberCount()).isEqualTo(1);
        assertThat(response.deliveryResults()).hasSize(1);
    }

    @Test
    void shouldHandleManyQueuesWithMixedResults() {
        // Given
        String messageId = "msg-123";
        Instant timestamp = Instant.now();
        List<QueueDeliveryResult> results = List.of(
            QueueDeliveryResult.success("queue-1"),
            QueueDeliveryResult.success("queue-2"),
            QueueDeliveryResult.success("queue-3"),
            QueueDeliveryResult.failure("queue-4", "Error 1"),
            QueueDeliveryResult.failure("queue-5", "Error 2")
        );

        // When
        EventDistributionResponse response = EventDistributionResponse.fromResults(
            messageId, timestamp, 5, results
        );

        // Then
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.PARTIAL);
        assertThat(response.subscriberCount()).isEqualTo(5);
        assertThat(response.deliveryResults()).hasSize(5);
    }
}
