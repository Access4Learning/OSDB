// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for routing exception classes.
 * Tests ProviderNotFoundException, QueueNotFoundException, and SubscriptionNotFoundException.
 */
class RoutingExceptionsTest {

    // ProviderNotFoundException Tests

    @Test
    void providerNotFoundException_hasCorrectErrorCode() {
        ProviderNotFoundException exception = new ProviderNotFoundException("Provider not found");

        assertThat(exception.getErrorCode()).isEqualTo("ROUTE_001");
    }

    @Test
    void providerNotFoundException_hasNotFoundStatus() {
        ProviderNotFoundException exception = new ProviderNotFoundException("No provider available");

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void providerNotFoundException_preservesMessage() {
        String message = "No provider registered for service 'StudentPersonal' in zone 'default'";
        ProviderNotFoundException exception = new ProviderNotFoundException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void providerNotFoundException_extendsSifBrokerException() {
        ProviderNotFoundException exception = new ProviderNotFoundException("Test message");

        assertThat(exception).isInstanceOf(SifBrokerException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void providerNotFoundException_withServiceAndZone() {
        String message = "No provider found for service 'SchoolInfo' in zone 'production' and context 'NSW'";
        ProviderNotFoundException exception = new ProviderNotFoundException(message);

        assertThat(exception.getMessage()).contains("SchoolInfo");
        assertThat(exception.getMessage()).contains("production");
        assertThat(exception.getMessage()).contains("NSW");
        assertThat(exception.getErrorCode()).isEqualTo("ROUTE_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // QueueNotFoundException Tests

    @Test
    void queueNotFoundException_hasCorrectErrorCode() {
        QueueNotFoundException exception = new QueueNotFoundException("Queue not found");

        assertThat(exception.getErrorCode()).isEqualTo("ROUTE_002");
    }

    @Test
    void queueNotFoundException_hasNotFoundStatus() {
        QueueNotFoundException exception = new QueueNotFoundException("Queue does not exist");

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void queueNotFoundException_preservesMessage() {
        String message = "Queue with ID '123e4567-e89b-12d3-a456-426614174000' not found";
        QueueNotFoundException exception = new QueueNotFoundException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void queueNotFoundException_extendsSifBrokerException() {
        QueueNotFoundException exception = new QueueNotFoundException("Test message");

        assertThat(exception).isInstanceOf(SifBrokerException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void queueNotFoundException_withQueueId() {
        String queueId = "550e8400-e29b-41d4-a716-446655440000";
        String message = "Queue with ID '" + queueId + "' does not exist or has been deleted";
        QueueNotFoundException exception = new QueueNotFoundException(message);

        assertThat(exception.getMessage()).contains(queueId);
        assertThat(exception.getErrorCode()).isEqualTo("ROUTE_002");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void queueNotFoundException_withAccessDenied() {
        String message = "Queue 'my-queue' not found or not accessible to environment 'env-123'";
        QueueNotFoundException exception = new QueueNotFoundException(message);

        assertThat(exception.getMessage()).contains("not accessible");
        assertThat(exception.getErrorCode()).isEqualTo("ROUTE_002");
    }

    // SubscriptionNotFoundException Tests

    @Test
    void subscriptionNotFoundException_hasCorrectErrorCode() {
        SubscriptionNotFoundException exception = new SubscriptionNotFoundException("Subscription not found");

        assertThat(exception.getErrorCode()).isEqualTo("ROUTE_003");
    }

    @Test
    void subscriptionNotFoundException_hasNotFoundStatus() {
        SubscriptionNotFoundException exception = new SubscriptionNotFoundException("Subscription does not exist");

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void subscriptionNotFoundException_preservesMessage() {
        String message = "Subscription with ID '789e4567-e89b-12d3-a456-426614174000' not found";
        SubscriptionNotFoundException exception = new SubscriptionNotFoundException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void subscriptionNotFoundException_extendsSifBrokerException() {
        SubscriptionNotFoundException exception = new SubscriptionNotFoundException("Test message");

        assertThat(exception).isInstanceOf(SifBrokerException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void subscriptionNotFoundException_withSubscriptionId() {
        String subscriptionId = "660e8400-e29b-41d4-a716-446655440000";
        String message = "Subscription with ID '" + subscriptionId + "' not found";
        SubscriptionNotFoundException exception = new SubscriptionNotFoundException(message);

        assertThat(exception.getMessage()).contains(subscriptionId);
        assertThat(exception.getErrorCode()).isEqualTo("ROUTE_003");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void subscriptionNotFoundException_withServiceAndZone() {
        String message = "No subscription found for service 'StudentPersonal' in zone 'default' and context 'NSW'";
        SubscriptionNotFoundException exception = new SubscriptionNotFoundException(message);

        assertThat(exception.getMessage()).contains("StudentPersonal");
        assertThat(exception.getMessage()).contains("default");
        assertThat(exception.getErrorCode()).isEqualTo("ROUTE_003");
    }

    // Cross-Exception Tests

    @Test
    void allRoutingExceptions_haveNotFoundStatus() {
        ProviderNotFoundException providerException = new ProviderNotFoundException("msg");
        QueueNotFoundException queueException = new QueueNotFoundException("msg");
        SubscriptionNotFoundException subscriptionException = new SubscriptionNotFoundException("msg");

        assertThat(providerException.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(queueException.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(subscriptionException.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void allRoutingExceptions_haveUniqueErrorCodes() {
        ProviderNotFoundException providerException = new ProviderNotFoundException("msg");
        QueueNotFoundException queueException = new QueueNotFoundException("msg");
        SubscriptionNotFoundException subscriptionException = new SubscriptionNotFoundException("msg");

        assertThat(providerException.getErrorCode()).isEqualTo("ROUTE_001");
        assertThat(queueException.getErrorCode()).isEqualTo("ROUTE_002");
        assertThat(subscriptionException.getErrorCode()).isEqualTo("ROUTE_003");

        // Verify all codes are unique
        assertThat(providerException.getErrorCode()).isNotEqualTo(queueException.getErrorCode());
        assertThat(providerException.getErrorCode()).isNotEqualTo(subscriptionException.getErrorCode());
        assertThat(queueException.getErrorCode()).isNotEqualTo(subscriptionException.getErrorCode());
    }

    @Test
    void allRoutingExceptions_extendSifBrokerException() {
        ProviderNotFoundException providerException = new ProviderNotFoundException("msg");
        QueueNotFoundException queueException = new QueueNotFoundException("msg");
        SubscriptionNotFoundException subscriptionException = new SubscriptionNotFoundException("msg");

        assertThat(providerException).isInstanceOf(SifBrokerException.class);
        assertThat(queueException).isInstanceOf(SifBrokerException.class);
        assertThat(subscriptionException).isInstanceOf(SifBrokerException.class);
    }

    @Test
    void allRoutingExceptions_areRuntimeExceptions() {
        ProviderNotFoundException providerException = new ProviderNotFoundException("msg");
        QueueNotFoundException queueException = new QueueNotFoundException("msg");
        SubscriptionNotFoundException subscriptionException = new SubscriptionNotFoundException("msg");

        assertThat(providerException).isInstanceOf(RuntimeException.class);
        assertThat(queueException).isInstanceOf(RuntimeException.class);
        assertThat(subscriptionException).isInstanceOf(RuntimeException.class);
    }

    @Test
    void routingExceptions_canBeDistinguishedByErrorCode() {
        // Simulate catching a generic SifBrokerException and identifying the specific type
        SifBrokerException providerException = new ProviderNotFoundException("Provider not found");
        SifBrokerException queueException = new QueueNotFoundException("Queue not found");
        SifBrokerException subscriptionException = new SubscriptionNotFoundException("Subscription not found");

        // Can identify exception type by error code
        assertThat(providerException.getErrorCode()).startsWith("ROUTE_");
        assertThat(queueException.getErrorCode()).startsWith("ROUTE_");
        assertThat(subscriptionException.getErrorCode()).startsWith("ROUTE_");

        // Can distinguish between specific routing errors
        if (providerException.getErrorCode().equals("ROUTE_001")) {
            assertThat(providerException).isInstanceOf(ProviderNotFoundException.class);
        }
        if (queueException.getErrorCode().equals("ROUTE_002")) {
            assertThat(queueException).isInstanceOf(QueueNotFoundException.class);
        }
        if (subscriptionException.getErrorCode().equals("ROUTE_003")) {
            assertThat(subscriptionException).isInstanceOf(SubscriptionNotFoundException.class);
        }
    }

    @Test
    void routingExceptions_supportDetailedMessages() {
        String detailedProviderMessage = "No provider registered for service 'StudentPersonal' in zone 'production' and context 'NSW'";
        String detailedQueueMessage = "Queue with ID '550e8400-e29b-41d4-a716-446655440000' not found for environment 'env-123'";
        String detailedSubscriptionMessage = "Subscription with ID '660e8400-e29b-41d4-a716-446655440000' not found or has expired";

        ProviderNotFoundException providerException = new ProviderNotFoundException(detailedProviderMessage);
        QueueNotFoundException queueException = new QueueNotFoundException(detailedQueueMessage);
        SubscriptionNotFoundException subscriptionException = new SubscriptionNotFoundException(detailedSubscriptionMessage);

        assertThat(providerException.getMessage()).contains("StudentPersonal");
        assertThat(queueException.getMessage()).contains("550e8400");
        assertThat(subscriptionException.getMessage()).contains("expired");
    }

    @Test
    void routingExceptions_indicateResourceNotFound() {
        // All routing exceptions should indicate that a resource was not found
        ProviderNotFoundException providerException = new ProviderNotFoundException("Provider not available");
        QueueNotFoundException queueException = new QueueNotFoundException("Queue does not exist");
        SubscriptionNotFoundException subscriptionException = new SubscriptionNotFoundException("Subscription not found");

        // All should have 404 status
        assertThat(providerException.getHttpStatus().value()).isEqualTo(404);
        assertThat(queueException.getHttpStatus().value()).isEqualTo(404);
        assertThat(subscriptionException.getHttpStatus().value()).isEqualTo(404);

        // All should have ROUTE_ error code prefix
        assertThat(providerException.getErrorCode()).startsWith("ROUTE_");
        assertThat(queueException.getErrorCode()).startsWith("ROUTE_");
        assertThat(subscriptionException.getErrorCode()).startsWith("ROUTE_");
    }

    @Test
    void routingExceptions_canBeUsedInErrorHandling() {
        // Simulate a generic error handler that catches SifBrokerException
        try {
            throw new ProviderNotFoundException("No provider for StudentPersonal");
        } catch (SifBrokerException e) {
            // Error handler can extract error code and HTTP status
            assertThat(e.getErrorCode()).isEqualTo("ROUTE_001");
            assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(e.getMessage()).contains("StudentPersonal");
        }

        try {
            throw new QueueNotFoundException("Queue abc-123 not found");
        } catch (SifBrokerException e) {
            assertThat(e.getErrorCode()).isEqualTo("ROUTE_002");
            assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(e.getMessage()).contains("abc-123");
        }

        try {
            throw new SubscriptionNotFoundException("Subscription xyz-789 not found");
        } catch (SifBrokerException e) {
            assertThat(e.getErrorCode()).isEqualTo("ROUTE_003");
            assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(e.getMessage()).contains("xyz-789");
        }
    }
}
