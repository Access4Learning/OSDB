// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.health.JaegerHealthIndicator;
import org.sifunity.broker.common.health.LogstashHealthIndicator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for health endpoints.
 * Tests liveness, readiness, and component health indicators.
 * Uses H2 in-memory database configured in test profile.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthEndpointIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private RestTemplate restTemplate;
    
    @MockBean
    private JaegerHealthIndicator jaegerHealthIndicator;
    
    @MockBean
    private LogstashHealthIndicator logstashHealthIndicator;
    
    @BeforeEach
    void setUp() {
        // Mock RestTemplate to return successful health check for Queue Service
        // This allows the REAL QueueServiceHealthIndicator to report UP status
        when(restTemplate.getForEntity(eq("http://localhost:8082/actuator/health"), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"status\":\"UP\"}"));
        
        // Configure default mock behavior for other health indicators
        when(jaegerHealthIndicator.health()).thenReturn(Health.up().build());
        when(logstashHealthIndicator.health()).thenReturn(Health.up().build());
    }
    
    @Test
    void livenessProbe_ReturnsUp() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
    
    @Test
    void readinessProbe_WhenAllDependenciesAvailable_ReturnsUp() throws Exception {
        // Act & Assert - Mock is already configured in setUp()
        MvcResult result = mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andReturn();
        
        // Additional verification
        String content = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(content);
        assertThat(json.get("status").asText()).isEqualTo("UP");
    }
    
    @Test
    void healthEndpoint_ReturnsDetailedHealthInformation() throws Exception {
        // Act & Assert - Mock is already configured in setUp()
        MvcResult result = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").exists())
                .andReturn();
        
        // Verify components are present
        String content = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(content);
        JsonNode components = json.get("components");
        
        // Database health should be present
        assertThat(components.has("db")).isTrue();
        assertThat(components.get("db").get("status").asText()).isEqualTo("UP");
        
        // Liveness state should be present
        assertThat(components.has("livenessState")).isTrue();
        
        // Readiness state should be present
        assertThat(components.has("readinessState")).isTrue();
        
        // Queue Service health should be present (component name is "queueService")
        assertThat(components.has("queueService")).isTrue();
    }
    
    @Test
    void healthEndpoint_WhenQueueServiceUnavailable_ShowsPartialHealth() throws Exception {
        // Arrange - Configure RestTemplate mock to throw exception (simulating Queue Service down)
        when(restTemplate.getForEntity(eq("http://localhost:8082/actuator/health"), eq(String.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection refused"));
        
        // Act & Assert
        MvcResult result = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andReturn();
        
        // Verify Queue Service is marked as down
        String content = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(content);
        JsonNode components = json.get("components");
        
        // Database should still be up
        assertThat(components.get("db").get("status").asText()).isEqualTo("UP");
        
        // Queue Service should be down (component name is "queueService")
        JsonNode queueServiceHealth = components.get("queueService");
        assertThat(queueServiceHealth).isNotNull();
        assertThat(queueServiceHealth.get("status").asText()).isEqualTo("DOWN");
        assertThat(queueServiceHealth.get("details").get("queue-service").asText())
                .isEqualTo("Unavailable");
    }
    
    @Test
    void healthEndpoint_DatabaseHealthIndicator_ShowsDetails() throws Exception {
        // Act & Assert - Mock is already configured in setUp()
        MvcResult result = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn();
        
        // Verify database details
        String content = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(content);
        JsonNode dbHealth = json.get("components").get("db");
        
        assertThat(dbHealth.get("status").asText()).isEqualTo("UP");
        assertThat(dbHealth.get("details")).isNotNull();
        assertThat(dbHealth.get("details").has("database")).isTrue();
    }
    
    @Test
    void healthEndpoint_QueueServiceHealthIndicator_ShowsDetails() throws Exception {
        // Act & Assert - Mock is already configured in setUp()
        MvcResult result = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn();
        
        // Verify Queue Service details (component name is "queueService")
        String content = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(content);
        JsonNode queueServiceHealth = json.get("components").get("queueService");
        
        assertThat(queueServiceHealth).isNotNull();
        assertThat(queueServiceHealth.get("status").asText()).isEqualTo("UP");
        assertThat(queueServiceHealth.get("details").get("queue-service").asText())
                .isEqualTo("Available");
        assertThat(queueServiceHealth.get("details").has("url")).isTrue();
    }
}
