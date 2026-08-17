// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for QueueServiceHealthIndicator.
 */
@ExtendWith(MockitoExtension.class)
class QueueServiceHealthIndicatorTest {
    
    @Mock
    private RestTemplate restTemplate;
    
    private QueueServiceHealthIndicator healthIndicator;
    
    private static final String QUEUE_SERVICE_URL = "http://localhost:8082";
    
    @BeforeEach
    void setUp() {
        healthIndicator = new QueueServiceHealthIndicator(restTemplate, QUEUE_SERVICE_URL);
    }
    
    @Test
    void health_WhenQueueServiceIsAvailable_ReturnsUp() {
        // Arrange
        String healthUrl = QUEUE_SERVICE_URL + "/actuator/health";
        ResponseEntity<String> response = new ResponseEntity<>("{\"status\":\"UP\"}", HttpStatus.OK);
        when(restTemplate.getForEntity(eq(healthUrl), eq(String.class))).thenReturn(response);
        
        // Act
        Health health = healthIndicator.health();
        
        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("queue-service", "Available");
        assertThat(health.getDetails()).containsEntry("url", QUEUE_SERVICE_URL);
    }
    
    @Test
    void health_WhenQueueServiceIsUnavailable_ReturnsDown() {
        // Arrange
        String healthUrl = QUEUE_SERVICE_URL + "/actuator/health";
        when(restTemplate.getForEntity(eq(healthUrl), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));
        
        // Act
        Health health = healthIndicator.health();
        
        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("queue-service", "Unavailable");
        assertThat(health.getDetails()).containsEntry("url", QUEUE_SERVICE_URL);
        assertThat(health.getDetails()).containsKey("error");
    }
    
    @Test
    void health_WhenQueueServiceReturnsError_ReturnsDown() {
        // Arrange
        String healthUrl = QUEUE_SERVICE_URL + "/actuator/health";
        when(restTemplate.getForEntity(eq(healthUrl), eq(String.class)))
                .thenThrow(new RestClientException("Internal Server Error"));
        
        // Act
        Health health = healthIndicator.health();
        
        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("queue-service", "Unavailable");
        assertThat(health.getDetails()).containsKey("error");
    }
    
    @Test
    void health_WhenUnexpectedExceptionOccurs_ReturnsDown() {
        // Arrange
        String healthUrl = QUEUE_SERVICE_URL + "/actuator/health";
        when(restTemplate.getForEntity(eq(healthUrl), eq(String.class)))
                .thenThrow(new RuntimeException("Unexpected error"));
        
        // Act
        Health health = healthIndicator.health();
        
        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("queue-service", "Unavailable");
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails().get("error")).asString().contains("Unexpected error");
    }
}
