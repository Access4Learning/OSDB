// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.ProviderRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.ProviderService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationBody;

/**
 * Property 29: SIF error response format
 * 
 * <p>Validates: Requirements 10.5</p>
 * 
 * <p>This property test verifies that all error conditions return responses
 * conforming to the SIF 3.7 error format with required fields:
 * - code: error code identifying the error type
 * - message: human-readable error message
 * - scope: error scope (typically "BROKER")
 * - timestamp: when the error occurred
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Transactional
@Tag("environment-service-property-29")
public class ErrorResponseFormatPropertyTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private org.sifunity.broker.environment.service.ZoneService zoneService;

    @Autowired
    private org.sifunity.broker.environment.service.ContextServiceService contextServiceService;
    
    private final Random random = new Random();

    private TestEntityHierarchy hierarchy;

    @BeforeEach
    void setUp() {
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
    }
    
    /**
     * Property 29: SIF error response format
     */
    @RepeatedTest(10)
    void errorResponsesShouldConformToSifFormat() throws Exception {
        int scenario = random.nextInt(2);
        
        switch (scenario) {
            case 0 -> testValidationError();
            case 1 -> testConflictError();
        }
    }
    
    /**
     * Test validation error (HTTP 400) - missing required fields
     */
    private void testValidationError() throws Exception {
        String invalidJson = "{}";
        
        MvcResult result = mockMvc.perform(post(hierarchy.resourceBasePath() + "/environments/environment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andReturn();
        
        verifyErrorResponseFormat(result);
    }
    
    /**
     * Test conflict error (HTTP 409) - duplicate provider registration in same environment
     */
    private void testConflictError() throws Exception {
        // Create a BROKERED environment
        String appName = "test-app-" + UUID.randomUUID();
        EnvironmentRegistrationRequest envRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(hierarchy.getDomainId(), envRequest);
        UUID environmentId = envResponse.environment().id();

        // Register a provider via REST (first registration) using the real zone/context
        String serviceName = "TestService" + UUID.randomUUID().toString().substring(0, 8);
        UUID contextServiceUuid = contextServiceService.resolveOrCreate(
                hierarchy.getContextUuid(), serviceName, "OBJECT");
        ProviderRegistrationRequest providerRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of("https://api.example.com/test"),
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY)))))));
        String providerJson = objectMapper.writeValueAsString(providerRequest);

        mockMvc.perform(post(hierarchy.resourceBasePath() + "/providers/provider")
                .contentType(MediaType.APPLICATION_JSON)
                .content(providerJson))
                .andExpect(status().isCreated());

        // Attempt duplicate provider registration in same environment via REST â€” should get 409
        MvcResult result = mockMvc.perform(post(hierarchy.resourceBasePath() + "/providers/provider")
                .contentType(MediaType.APPLICATION_JSON)
                .content(providerJson))
                .andExpect(status().isConflict())
                .andReturn();
        
        verifyErrorResponseFormat(result);
    }
    
    /**
     * Verify that the error response conforms to SIF error format
     */
    private void verifyErrorResponseFormat(MvcResult result) throws Exception {
        String responseBody = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(responseBody);

        assertThat(root.has("error"))
                .as("Error response must be wrapped under top-level 'error' key")
                .isTrue();
        JsonNode errorResponse = root.get("error");

        assertThat(errorResponse.has("id"))
                .as("Error response must have 'id' field")
                .isTrue();
        assertThat(errorResponse.has("code"))
                .as("Error response must have 'code' field")
                .isTrue();
        assertThat(errorResponse.has("subCode"))
                .as("Error response must have 'subCode' field")
                .isTrue();
        assertThat(errorResponse.has("message"))
                .as("Error response must have 'message' field")
                .isTrue();
        assertThat(errorResponse.has("scope"))
                .as("Error response must have 'scope' field")
                .isTrue();
        assertThat(errorResponse.has("timestamp"))
                .as("SIF 3.7 errorCommon does not include 'timestamp' — must be absent from wire")
                .isFalse();

        assertThat(errorResponse.get("code").isInt())
                .as("'code' field must be an integer HTTP status")
                .isTrue();

        assertThat(errorResponse.get("subCode").isTextual())
                .as("'subCode' field must be a string")
                .isTrue();
        assertThat(errorResponse.get("subCode").asText())
                .as("'subCode' field must not be empty")
                .isNotBlank();

        assertThat(errorResponse.get("message").isTextual())
                .as("'message' field must be a string")
                .isTrue();
        assertThat(errorResponse.get("message").asText())
                .as("'message' field must not be empty")
                .isNotBlank();
        
        assertThat(errorResponse.get("scope").isTextual())
                .as("'scope' field must be a string")
                .isTrue();
        assertThat(errorResponse.get("scope").asText())
                .as("'scope' field should be 'BROKER'")
                .isEqualTo("BROKER");
    }
}