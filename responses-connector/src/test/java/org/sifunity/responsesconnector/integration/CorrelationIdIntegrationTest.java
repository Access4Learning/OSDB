// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for correlation ID support.
 * Verifies that correlation IDs are properly extracted, generated, and propagated.
 * 
 * Requirements: 13.1, 13.6 (Observability and Logging)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorrelationIdIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    
    @Test
    void shouldPreserveCorrelationIdFromRequest() throws Exception {
        // Given
        String correlationId = UUID.randomUUID().toString();
        
        // When/Then - Use metrics endpoint which always returns 200
        mockMvc.perform(get("/actuator/metrics")
                        .header(CORRELATION_ID_HEADER, correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string(CORRELATION_ID_HEADER, correlationId));
    }
    
    @Test
    void shouldGenerateCorrelationIdWhenNotProvided() throws Exception {
        // When - Use metrics endpoint which always returns 200
        MvcResult result = mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CORRELATION_ID_HEADER))
                .andReturn();
        
        // Then
        String correlationId = result.getResponse().getHeader(CORRELATION_ID_HEADER);
        assertThat(correlationId).isNotNull();
        assertThat(correlationId).isNotBlank();
        // Verify it's a valid UUID format
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }
    
    @Test
    void shouldGenerateNewCorrelationIdForEachRequest() throws Exception {
        // When - Use metrics endpoint which always returns 200
        MvcResult result1 = mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CORRELATION_ID_HEADER))
                .andReturn();
        
        MvcResult result2 = mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CORRELATION_ID_HEADER))
                .andReturn();
        
        // Then
        String correlationId1 = result1.getResponse().getHeader(CORRELATION_ID_HEADER);
        String correlationId2 = result2.getResponse().getHeader(CORRELATION_ID_HEADER);
        
        assertThat(correlationId1).isNotEqualTo(correlationId2);
    }
    
    @Test
    void shouldIncludeCorrelationIdInResponseForAllEndpoints() throws Exception {
        // Test multiple endpoints - use metrics and info which always return 200
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CORRELATION_ID_HEADER));
        
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CORRELATION_ID_HEADER));
    }
}
