// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.queueservice.controller.CascadeCleanupController;
import org.sifunity.broker.queueservice.dto.CascadeCleanupRequest;
import org.sifunity.broker.queueservice.exception.GlobalExceptionHandler;
import org.sifunity.broker.queueservice.service.CascadeCleanupService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for CascadeCleanupController using standalone MockMvc.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CascadeCleanupController Unit Tests")
class CascadeCleanupControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CascadeCleanupService cascadeCleanupService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        CascadeCleanupController controller = new CascadeCleanupController(cascadeCleanupService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST with domainId only should call submitDomainCleanup and return 202")
    void domainCleanup_returns202() throws Exception {
        mockMvc.perform(post("/internal/cascade-cleanup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CascadeCleanupRequest("domain-123", null))))
                .andExpect(status().isAccepted());

        verify(cascadeCleanupService).submitDomainCleanup("domain-123");
        verify(cascadeCleanupService, never()).submitSolutionCleanup(any(), any());
    }

    @Test
    @DisplayName("POST with domainId and solutionId should call submitSolutionCleanup and return 202")
    void solutionCleanup_returns202() throws Exception {
        mockMvc.perform(post("/internal/cascade-cleanup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CascadeCleanupRequest("domain-123", "solution-456"))))
                .andExpect(status().isAccepted());

        verify(cascadeCleanupService).submitSolutionCleanup("domain-123", "solution-456");
        verify(cascadeCleanupService, never()).submitDomainCleanup(any());
    }

    @Test
    @DisplayName("POST with null domainId should return 400")
    void nullDomainId_returns400() throws Exception {
        mockMvc.perform(post("/internal/cascade-cleanup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"domainId": null, "solutionId": "solution-456"}
                            """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cascadeCleanupService);
    }

    @Test
    @DisplayName("POST with blank domainId should return 400")
    void blankDomainId_returns400() throws Exception {
        mockMvc.perform(post("/internal/cascade-cleanup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"domainId": "   ", "solutionId": null}
                            """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cascadeCleanupService);
    }

    @Test
    @DisplayName("POST with empty domainId should return 400")
    void emptyDomainId_returns400() throws Exception {
        mockMvc.perform(post("/internal/cascade-cleanup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"domainId": "", "solutionId": null}
                            """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cascadeCleanupService);
    }
}
