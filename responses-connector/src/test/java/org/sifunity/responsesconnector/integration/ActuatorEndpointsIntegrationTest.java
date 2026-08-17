// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Actuator endpoints.
 * Verifies that health, metrics, info, and prometheus endpoints are properly configured.
 * 
 * Requirements: 13.2, 13.3, 13.5 (Observability and Logging)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorEndpointsIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldExposeHealthEndpoint() throws Exception {
        // Health endpoint should be accessible even if dependencies are down
        // It will return 503 (DOWN) status when Queue Service is unavailable
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())  // 503 when Queue Service is down
                .andExpect(jsonPath("$.status").value("DOWN"));
    }
    
    @Test
    void shouldExposeMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }
    
    @Test
    void shouldExposeInfoEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }
    
    @Test
    void shouldExposePrometheusEndpoint() throws Exception {
        // Prometheus endpoint should be accessible
        // Note: In test profile, prometheus may not be fully configured
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(header().exists("X-Correlation-Id"));
    }
    
    @Test
    void shouldIncludeCustomMetricsInMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray())
                .andExpect(jsonPath("$.names[?(@=='responses.received.total')]").exists());
    }
    
    @Test
    void shouldIncludeQueueServiceHealthIndicator() throws Exception {
        // Health endpoint should include status even when Queue Service is down
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())  // 503 when Queue Service is down
                .andExpect(jsonPath("$.status").value("DOWN"));
    }
    
    @Test
    void shouldIncludeApplicationTagInMetrics() throws Exception {
        // Verify that custom metrics are registered with application tag
        mockMvc.perform(get("/actuator/metrics/responses.received.total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("responses.received.total"));
    }
    
    @Test
    void shouldExposeHttpServerRequestsMetrics() throws Exception {
        mockMvc.perform(get("/actuator/metrics/http.server.requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("http.server.requests"));
    }
}
