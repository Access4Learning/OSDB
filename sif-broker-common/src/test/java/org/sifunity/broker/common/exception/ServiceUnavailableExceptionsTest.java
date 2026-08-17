// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for service unavailable exception classes.
 * Tests ProviderUnavailableException and ServiceUnavailableException.
 */
class ServiceUnavailableExceptionsTest {

    // ProviderUnavailableException Tests

    @Test
    void providerUnavailableException_hasCorrectErrorCode() {
        ProviderUnavailableException exception = new ProviderUnavailableException("Provider service is down");

        assertThat(exception.getErrorCode()).isEqualTo("SERVICE_001");
    }

    @Test
    void providerUnavailableException_hasServiceUnavailableStatus() {
        ProviderUnavailableException exception = new ProviderUnavailableException("Provider not responding");

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void providerUnavailableException_preservesMessage() {
        String message = "Provider service is temporarily unavailable";
        ProviderUnavailableException exception = new ProviderUnavailableException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void providerUnavailableException_extendsSifBrokerException() {
        ProviderUnavailableException exception = new ProviderUnavailableException("Test message");

        assertThat(exception).isInstanceOf(SifBrokerException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void providerUnavailableException_withProviderDown() {
        ProviderUnavailableException exception = new ProviderUnavailableException("Provider for StudentPersonal in zone 'default' is not responding");

        assertThat(exception.getMessage()).contains("StudentPersonal");
        assertThat(exception.getMessage()).contains("zone");
        assertThat(exception.getMessage()).contains("not responding");
        assertThat(exception.getErrorCode()).isEqualTo("SERVICE_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void providerUnavailableException_withMaintenance() {
        ProviderUnavailableException exception = new ProviderUnavailableException("Provider service is undergoing scheduled maintenance");

        assertThat(exception.getMessage()).contains("maintenance");
    }

    @Test
    void providerUnavailableException_withOverload() {
        ProviderUnavailableException exception = new ProviderUnavailableException("Provider is overloaded and rejecting requests");

        assertThat(exception.getMessage()).contains("overloaded");
        assertThat(exception.getMessage()).contains("rejecting");
    }

    @Test
    void providerUnavailableException_withTimeout() {
        ProviderUnavailableException exception = new ProviderUnavailableException("Provider request timed out after 30 seconds");

        assertThat(exception.getMessage()).contains("timed out");
        assertThat(exception.getMessage()).contains("30 seconds");
    }

    @Test
    void providerUnavailableException_withHealthCheckFailure() {
        ProviderUnavailableException exception = new ProviderUnavailableException("Provider health check failed: endpoint returned 503");

        assertThat(exception.getMessage()).contains("health check");
        assertThat(exception.getMessage()).contains("503");
    }

    // ServiceUnavailableException Tests

    @Test
    void serviceUnavailableException_hasCorrectErrorCode() {
        ServiceUnavailableException exception = new ServiceUnavailableException("QueueService", "Service is down");

        assertThat(exception.getErrorCode()).isEqualTo("SERVICE_002");
    }

    @Test
    void serviceUnavailableException_hasServiceUnavailableStatus() {
        ServiceUnavailableException exception = new ServiceUnavailableException("EnvironmentService", "Not responding");

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void serviceUnavailableException_formatsMessageWithServiceName() {
        String service = "QueueService";
        String message = "Database connection pool exhausted";
        ServiceUnavailableException exception = new ServiceUnavailableException(service, message);

        assertThat(exception.getMessage()).isEqualTo("Service 'QueueService' unavailable: Database connection pool exhausted");
    }

    @Test
    void serviceUnavailableException_extendsSifBrokerException() {
        ServiceUnavailableException exception = new ServiceUnavailableException("TestService", "Test message");

        assertThat(exception).isInstanceOf(SifBrokerException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void serviceUnavailableException_withQueueService() {
        ServiceUnavailableException exception = new ServiceUnavailableException("QueueService", "Service is down for deployment");

        assertThat(exception.getMessage()).contains("QueueService");
        assertThat(exception.getMessage()).contains("deployment");
        assertThat(exception.getErrorCode()).isEqualTo("SERVICE_002");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void serviceUnavailableException_withEnvironmentService() {
        ServiceUnavailableException exception = new ServiceUnavailableException("EnvironmentService", "Circuit breaker opened after 5 consecutive failures");

        assertThat(exception.getMessage()).contains("EnvironmentService");
        assertThat(exception.getMessage()).contains("Circuit breaker");
    }

    @Test
    void serviceUnavailableException_withServiceDiscoveryFailure() {
        ServiceUnavailableException exception = new ServiceUnavailableException("RoutingService", "Service discovery failed: no instances available");

        assertThat(exception.getMessage()).contains("RoutingService");
        assertThat(exception.getMessage()).contains("Service discovery");
        assertThat(exception.getMessage()).contains("no instances");
    }

    @Test
    void serviceUnavailableException_withDatabaseIssue() {
        ServiceUnavailableException exception = new ServiceUnavailableException("AclService", "Database connection timeout");

        assertThat(exception.getMessage()).contains("AclService");
        assertThat(exception.getMessage()).contains("Database");
    }

    // Cross-Exception Tests

    @Test
    void serviceUnavailableExceptions_haveSameHttpStatus() {
        ProviderUnavailableException providerException = new ProviderUnavailableException("msg");
        ServiceUnavailableException serviceException = new ServiceUnavailableException("service", "msg");

        assertThat(providerException.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(serviceException.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(providerException.getHttpStatus()).isEqualTo(serviceException.getHttpStatus());
    }

    @Test
    void serviceUnavailableExceptions_haveUniqueErrorCodes() {
        ProviderUnavailableException providerException = new ProviderUnavailableException("msg");
        ServiceUnavailableException serviceException = new ServiceUnavailableException("service", "msg");

        assertThat(providerException.getErrorCode()).isEqualTo("SERVICE_001");
        assertThat(serviceException.getErrorCode()).isEqualTo("SERVICE_002");
        assertThat(providerException.getErrorCode()).isNotEqualTo(serviceException.getErrorCode());
    }

    @Test
    void allServiceUnavailableExceptions_extendSifBrokerException() {
        ProviderUnavailableException providerException = new ProviderUnavailableException("msg");
        ServiceUnavailableException serviceException = new ServiceUnavailableException("service", "msg");

        assertThat(providerException).isInstanceOf(SifBrokerException.class);
        assertThat(serviceException).isInstanceOf(SifBrokerException.class);
    }

    @Test
    void allServiceUnavailableExceptions_areRuntimeExceptions() {
        ProviderUnavailableException providerException = new ProviderUnavailableException("msg");
        ServiceUnavailableException serviceException = new ServiceUnavailableException("service", "msg");

        assertThat(providerException).isInstanceOf(RuntimeException.class);
        assertThat(serviceException).isInstanceOf(RuntimeException.class);
    }

    @Test
    void serviceUnavailableExceptions_canBeDistinguishedByErrorCode() {
        // Simulate catching a generic SifBrokerException and identifying the specific type
        SifBrokerException providerException = new ProviderUnavailableException("Provider unavailable");
        SifBrokerException serviceException = new ServiceUnavailableException("service", "Service unavailable");

        // Can identify exception type by error code
        assertThat(providerException.getErrorCode()).startsWith("SERVICE_");
        assertThat(serviceException.getErrorCode()).startsWith("SERVICE_");

        // Can distinguish between specific service unavailable errors
        if (providerException.getErrorCode().equals("SERVICE_001")) {
            assertThat(providerException).isInstanceOf(ProviderUnavailableException.class);
        }
        if (serviceException.getErrorCode().equals("SERVICE_002")) {
            assertThat(serviceException).isInstanceOf(ServiceUnavailableException.class);
        }
    }

    @Test
    void serviceUnavailableExceptions_distinguishBetweenProviderAndInternalService() {
        // ProviderUnavailableException refers to external provider services
        ProviderUnavailableException providerException = new ProviderUnavailableException("External provider for StudentPersonal is down");

        // ServiceUnavailableException refers to internal broker services
        ServiceUnavailableException serviceException = new ServiceUnavailableException("QueueService", "Internal service is down");

        // Different error codes help distinguish the cases
        assertThat(providerException.getErrorCode()).isEqualTo("SERVICE_001");
        assertThat(serviceException.getErrorCode()).isEqualTo("SERVICE_002");

        // Messages provide context about which type of service is unavailable
        assertThat(providerException.getMessage()).contains("provider");
        assertThat(serviceException.getMessage()).contains("Service 'QueueService'");
    }

    @Test
    void serviceUnavailableExceptions_provideContextForDebugging() {
        // Test that exceptions provide enough context to understand what went wrong
        ProviderUnavailableException providerException = new ProviderUnavailableException(
            "Provider for StudentPersonal in zone 'school-district' returned 503 after 3 retry attempts"
        );
        ServiceUnavailableException serviceException = new ServiceUnavailableException(
            "QueueService",
            "All 3 instances failed health check, circuit breaker opened"
        );

        // Provider exception provides service, zone, and retry details
        assertThat(providerException.getMessage()).contains("StudentPersonal");
        assertThat(providerException.getMessage()).contains("zone");
        assertThat(providerException.getMessage()).contains("retry");

        // Service exception provides service name and failure details
        assertThat(serviceException.getMessage()).contains("QueueService");
        assertThat(serviceException.getMessage()).contains("instances");
        assertThat(serviceException.getMessage()).contains("circuit breaker");
    }

    @Test
    void serviceUnavailableExceptions_supportDetailedMessages() {
        String detailedProviderMessage = "Provider endpoint https://provider.example.com/StudentPersonal returned 503 Service Unavailable, retry-after: 120 seconds";
        String detailedServiceMessage = "Service discovery returned no healthy instances, last successful call: 5 minutes ago";

        ProviderUnavailableException providerException = new ProviderUnavailableException(detailedProviderMessage);
        ServiceUnavailableException serviceException = new ServiceUnavailableException("RoutingService", detailedServiceMessage);

        assertThat(providerException.getMessage()).contains("endpoint");
        assertThat(providerException.getMessage()).contains("503");
        assertThat(providerException.getMessage()).contains("retry-after");

        assertThat(serviceException.getMessage()).contains("RoutingService");
        assertThat(serviceException.getMessage()).contains("no healthy instances");
        assertThat(serviceException.getMessage()).contains("last successful call");
    }

    @Test
    void serviceUnavailableException_includesServiceNameInMessage() {
        // ServiceUnavailableException always includes the service name in the message
        ServiceUnavailableException exception = new ServiceUnavailableException("MyService", "is down");

        assertThat(exception.getMessage()).contains("MyService");
        assertThat(exception.getMessage()).startsWith("Service 'MyService' unavailable:");
    }

    @Test
    void providerUnavailableException_vsProviderNotFoundException() {
        // ProviderUnavailableException (503) - provider exists but is temporarily unavailable
        ProviderUnavailableException unavailableException = new ProviderUnavailableException("Provider is down");

        // ProviderNotFoundException (404) - no provider is registered
        ProviderNotFoundException notFoundException = new ProviderNotFoundException("No provider registered");

        // Different HTTP status codes indicate different scenarios
        assertThat(unavailableException.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(notFoundException.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        // 503 suggests retry may succeed, 404 suggests provider needs to register first
    }

    @Test
    void serviceUnavailableExceptions_indicateRetryStrategy() {
        // Both exceptions use 503 status, indicating client may retry
        ProviderUnavailableException providerException = new ProviderUnavailableException("Provider temporarily down");
        ServiceUnavailableException serviceException = new ServiceUnavailableException("service", "Service temporarily down");

        // 503 status indicates temporary unavailability
        assertThat(providerException.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(serviceException.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // Client should implement exponential backoff retry strategy
    }
}
