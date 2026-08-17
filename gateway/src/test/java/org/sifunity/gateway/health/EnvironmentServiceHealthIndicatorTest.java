// SPDX-License-Identifier: MIT
package org.sifunity.gateway.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EnvironmentServiceHealthIndicator.
 * 
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Health check returns UP when Environment Service is available</li>
 *   <li>Health check returns DOWN when Environment Service is unavailable</li>
 *   <li>Health check includes Environment Service URL in details</li>
 * </ul>
 * 
 * <p>Requirements: 10.1, 10.2</p>
 */
@ExtendWith(MockitoExtension.class)
class EnvironmentServiceHealthIndicatorTest {
    
    @Mock
    private WebClient.Builder webClientBuilder;
    
    @Mock
    private WebClient webClient;
    
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    
    @Mock
    private WebClient.ResponseSpec responseSpec;
    
    private static final String ENVIRONMENT_SERVICE_URL = "http://localhost:8081";
    
    private EnvironmentServiceHealthIndicator healthIndicator;
    
    @BeforeEach
    void setUp() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        
        healthIndicator = new EnvironmentServiceHealthIndicator(
                webClientBuilder,
                ENVIRONMENT_SERVICE_URL
        );
    }
    
    /**
     * Test that health check returns UP when Environment Service is available.
     * 
     * <p>Validates: Requirements 10.1, 10.2</p>
     */
    @Test
    void healthCheckShouldReturnUpWhenEnvironmentServiceIsAvailable() {
        // Arrange
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(
                org.springframework.http.ResponseEntity.ok().build()
        ));
        
        // Act & Assert
        StepVerifier.create(healthIndicator.health())
                .assertNext(health -> {
                    assertThat(health.getStatus()).isEqualTo(Status.UP);
                    assertThat(health.getDetails()).containsEntry("environment-service", "Available");
                    assertThat(health.getDetails()).containsEntry("url", ENVIRONMENT_SERVICE_URL);
                })
                .verifyComplete();
    }
    
    /**
     * Test that health check returns DOWN when Environment Service is unavailable.
     * 
     * <p>Validates: Requirements 10.1, 10.2</p>
     */
    @Test
    void healthCheckShouldReturnDownWhenEnvironmentServiceIsUnavailable() {
        // Arrange
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(
                Mono.error(new RuntimeException("Connection refused"))
        );
        
        // Act & Assert
        // Note: The implementation catches errors with onErrorReturn(false) in checkEnvironmentServiceHealth(),
        // so the error detail is not included in the response
        StepVerifier.create(healthIndicator.health())
                .assertNext(health -> {
                    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
                    assertThat(health.getDetails()).containsEntry("environment-service", "Unavailable");
                    assertThat(health.getDetails()).containsEntry("url", ENVIRONMENT_SERVICE_URL);
                })
                .verifyComplete();
    }
    
    /**
     * Test that health check returns DOWN when Environment Service returns error status.
     * 
     * <p>Validates: Requirements 10.1, 10.2</p>
     */
    @Test
    void healthCheckShouldReturnDownWhenEnvironmentServiceReturnsError() {
        // Arrange
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(
                org.springframework.http.ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        ));
        
        // Act & Assert
        StepVerifier.create(healthIndicator.health())
                .assertNext(health -> {
                    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
                    assertThat(health.getDetails()).containsEntry("environment-service", "Unavailable");
                    assertThat(health.getDetails()).containsEntry("url", ENVIRONMENT_SERVICE_URL);
                })
                .verifyComplete();
    }
    
    /**
     * Test that health check handles timeout gracefully.
     * 
     * <p>Validates: Requirements 10.1, 10.2</p>
     */
    @Test
    void healthCheckShouldHandleTimeoutGracefully() {
        // Arrange
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(
                Mono.delay(java.time.Duration.ofSeconds(5))
                        .then(Mono.just(org.springframework.http.ResponseEntity.ok().build()))
        );
        
        // Act & Assert
        StepVerifier.create(healthIndicator.health())
                .assertNext(health -> {
                    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
                    assertThat(health.getDetails()).containsEntry("environment-service", "Unavailable");
                })
                .verifyComplete();
    }
}
