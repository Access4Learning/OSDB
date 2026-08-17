// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for QueueDeliveryResult DTO.
 * Tests factory methods and field values.
 */
class QueueDeliveryResultTest {

    @Test
    void shouldCreateResultWithAllFields() {
        // Given
        String queueId = "queue-123";
        DeliveryResultStatus status = DeliveryResultStatus.SUCCESS;
        String error = null;

        // When
        QueueDeliveryResult result = new QueueDeliveryResult(queueId, status, error);

        // Then
        assertThat(result.queueId()).isEqualTo(queueId);
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.error()).isNull();
    }

    @Test
    void shouldCreateSuccessResultUsingFactoryMethod() {
        // Given
        String queueId = "queue-123";

        // When
        QueueDeliveryResult result = QueueDeliveryResult.success(queueId);

        // Then
        assertThat(result.queueId()).isEqualTo(queueId);
        assertThat(result.status()).isEqualTo(DeliveryResultStatus.SUCCESS);
        assertThat(result.error()).isNull();
    }

    @Test
    void shouldCreateFailureResultUsingFactoryMethod() {
        // Given
        String queueId = "queue-123";
        String error = "Connection timeout";

        // When
        QueueDeliveryResult result = QueueDeliveryResult.failure(queueId, error);

        // Then
        assertThat(result.queueId()).isEqualTo(queueId);
        assertThat(result.status()).isEqualTo(DeliveryResultStatus.FAILED);
        assertThat(result.error()).isEqualTo(error);
    }

    @Test
    void shouldHandleNullErrorInSuccessResult() {
        // Given
        String queueId = "queue-123";

        // When
        QueueDeliveryResult result = new QueueDeliveryResult(
            queueId, DeliveryResultStatus.SUCCESS, null
        );

        // Then
        assertThat(result.error()).isNull();
    }

    @Test
    void shouldHandleNonNullErrorInFailureResult() {
        // Given
        String queueId = "queue-123";
        String error = "Service unavailable";

        // When
        QueueDeliveryResult result = new QueueDeliveryResult(
            queueId, DeliveryResultStatus.FAILED, error
        );

        // Then
        assertThat(result.error()).isEqualTo(error);
    }

    @Test
    void shouldHandleEmptyErrorMessage() {
        // Given
        String queueId = "queue-123";
        String error = "";

        // When
        QueueDeliveryResult result = QueueDeliveryResult.failure(queueId, error);

        // Then
        assertThat(result.error()).isEmpty();
    }

    @Test
    void shouldHandleLongErrorMessage() {
        // Given
        String queueId = "queue-123";
        String error = "A very long error message that describes in detail what went wrong " +
                       "during the queue delivery operation including stack traces and context";

        // When
        QueueDeliveryResult result = QueueDeliveryResult.failure(queueId, error);

        // Then
        assertThat(result.error()).isEqualTo(error);
    }
}
