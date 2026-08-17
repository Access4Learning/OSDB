// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.environment.dto.ContextCreateBody;
import org.sifunity.broker.environment.dto.ContextCreateRequest;
import org.sifunity.broker.environment.dto.ContextCreateRequestWrapper;
import org.sifunity.broker.environment.dto.ContextDTO;
import org.sifunity.broker.environment.dto.ZoneCreateBody;
import org.sifunity.broker.environment.dto.ZoneCreateRequest;
import org.sifunity.broker.environment.dto.ZoneCreateRequestWrapper;
import org.sifunity.broker.environment.dto.ZoneDTO;
import org.sifunity.broker.environment.service.ZoneService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.sifunity.broker.environment.controller.ZoneController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Preservation Property Tests for Security Enforcement - Zone/Context Operations
 *
 * <p><strong>CRITICAL: These tests are EXPECTED TO PASS on unfixed code.</strong>
 * They capture the baseline behavior that must remain unchanged after the security fix.
 *
 * <p><strong>Purpose:</strong>
 * Verifies that admin zone/context creation and authenticated read operations continue
 * to work correctly after the security enforcement fix is applied.
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.7, 3.8</strong>
 *
 * @see ZoneController
 */
class SecurityEnforcementPreservationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Property 2.1: Preservation - Admin POST zones returns 201
     *
     * <p><strong>Validates: Requirement 3.1</strong>
     *
     * <p>For all admin environments, POST to zones endpoint returns 201 Created.
     * The ZoneController currently has no admin guard, so any caller gets 201.
     * After the fix, admin callers must still get 201.
     *
     * @param zoneId randomly generated valid zone ID
     * @param description randomly generated zone description (nullable)
     */
    @Property(tries = 20)
    @Label("Preservation: Admin POST zones returns 201 Created")
    void adminPostZonesReturns201(
            @ForAll("validZoneIds") String zoneId,
            @ForAll("zoneDescriptions") String description) throws Exception {

        // Arrange
        ZoneService mockZoneService = mock(ZoneService.class);
        ZoneController controller = new ZoneController(mockZoneService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        String domainId = "GLOBAL_DOMAIN";
        String solutionId = "GLOBAL_SOLUTION";

        ZoneDTO createdZone = new ZoneDTO(
                UUID.randomUUID(), solutionId, zoneId, description, Instant.now());
        when(mockZoneService.createZone(eq(domainId), any(ZoneCreateRequest.class))).thenReturn(createdZone);

        ZoneCreateRequestWrapper request =
                new ZoneCreateRequestWrapper(new ZoneCreateBody(zoneId, description));

        // Act & Assert: admin environment creates zone via SIF singular subpath → 201 Created
        mockMvc.perform(post("/{domainId}/{solutionId}/zones/zone", domainId, solutionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    /**
     * Property 2.2: Preservation - Admin POST contexts returns 201
     *
     * <p><strong>Validates: Requirement 3.2</strong>
     *
     * <p>For all admin environments, POST to zones/{zoneId}/contexts endpoint
     * returns 201 Created. After the fix, admin callers must still get 201.
     *
     * @param contextId randomly generated valid context ID
     * @param zoneId randomly generated parent zone ID
     */
    @Property(tries = 20)
    @Label("Preservation: Admin POST contexts returns 201 Created")
    void adminPostContextsReturns201(
            @ForAll("validContextIds") String contextId,
            @ForAll("validZoneIds") String zoneId) throws Exception {

        // Arrange
        ZoneService mockZoneService = mock(ZoneService.class);
        ZoneController controller = new ZoneController(mockZoneService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        String domainId = "GLOBAL_DOMAIN";
        String solutionId = "GLOBAL_SOLUTION";

        ContextDTO createdContext = new ContextDTO(
                UUID.randomUUID(), zoneId, contextId, "Test context", Instant.now());
        when(mockZoneService.createContext(eq(domainId), eq(solutionId), eq(zoneId), any(ContextCreateRequest.class)))
                .thenReturn(createdContext);

        ContextCreateRequestWrapper request =
                new ContextCreateRequestWrapper(new ContextCreateBody(contextId, "Test context"));

        // Act & Assert: admin environment creates context via SIF singular subpath → 201 Created
        mockMvc.perform(post("/{domainId}/{solutionId}/zones/{zoneId}/contexts/context",
                        domainId, solutionId, zoneId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    /**
     * Property 2.6: Preservation - Any authenticated environment GET zones returns 200
     *
     * <p><strong>Validates: Requirement 3.7</strong>
     *
     * <p>For all authenticated environments, GET zones returns 200 with zone list.
     * Read access to zones is not admin-restricted and must remain open.
     *
     * @param domainId randomly generated domain ID
     * @param solutionId randomly generated solution ID
     */
    @Property(tries = 20)
    @Label("Preservation: Any authenticated env GET zones returns 200")
    void anyAuthenticatedEnvGetZonesReturns200(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        // Arrange
        ZoneService mockZoneService = mock(ZoneService.class);
        ZoneController controller = new ZoneController(mockZoneService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        List<ZoneDTO> zones = List.of(
                new ZoneDTO(UUID.randomUUID(), solutionId, "zone1", "Zone 1", Instant.now()));
        when(mockZoneService.listZonesBySolution(domainId, solutionId)).thenReturn(zones);

        // Act & Assert: any environment can list zones → 200 OK
        mockMvc.perform(get("/{domainId}/{solutionId}/zones", domainId, solutionId))
                .andExpect(status().isOk());
    }

    /**
     * Property 2.7: Preservation - Any authenticated environment GET contexts returns 200
     *
     * <p><strong>Validates: Requirement 3.8</strong>
     *
     * <p>For all authenticated environments, GET zones/{zoneId}/contexts returns 200
     * with context list. Read access to contexts is not admin-restricted.
     *
     * @param domainId randomly generated domain ID
     * @param solutionId randomly generated solution ID
     * @param zoneId randomly generated zone ID
     */
    @Property(tries = 20)
    @Label("Preservation: Any authenticated env GET contexts returns 200")
    void anyAuthenticatedEnvGetContextsReturns200(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("validZoneIds") String zoneId) throws Exception {

        // Arrange
        ZoneService mockZoneService = mock(ZoneService.class);
        ZoneController controller = new ZoneController(mockZoneService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        List<ContextDTO> contexts = List.of(
                new ContextDTO(UUID.randomUUID(), zoneId, "ctx1", "Context 1", Instant.now()));
        when(mockZoneService.listContexts(domainId, solutionId, zoneId)).thenReturn(contexts);

        // Act & Assert: any environment can list contexts → 200 OK
        mockMvc.perform(get("/{domainId}/{solutionId}/zones/{zoneId}/contexts",
                        domainId, solutionId, zoneId))
                .andExpect(status().isOk());
    }

    // ========== Test Data Generators ==========

    @Provide
    Arbitrary<String> validZoneIds() {
        return Arbitraries.of(
                "default", "zone1", "zone-prod", "us-east-1", "staging-zone",
                "dev-zone-1", "qa-region", "test123", "prod-us-west", "zone-A1");
    }

    @Provide
    Arbitrary<String> validContextIds() {
        return Arbitraries.of(
                "DEFAULT", "context1", "ctx-prod", "ctx-staging", "test-context",
                "dev-ctx-1", "qa-context", "ctx123", "prod-context", "ctx-A1");
    }

    @Provide
    Arbitrary<String> zoneDescriptions() {
        return Arbitraries.of(
                "Production zone", "Staging environment", "Development zone",
                "Test zone for QA", null, "Default zone", "Regional zone",
                "Sandbox environment", "Integration testing", "Demo zone");
    }

    @Provide
    Arbitrary<String> domainIds() {
        return Arbitraries.of(
                "GLOBAL_DOMAIN", "test-domain", "domain-A", "prod-domain",
                "staging-domain", "dev-domain", "school-district-1",
                "state-education", "vendor-domain", "integration-domain");
    }

    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.of(
                "GLOBAL_SOLUTION", "test-solution", "solution-A", "prod-solution",
                "staging-solution", "dev-solution", "sis-integration",
                "lms-solution", "assessment-platform", "data-exchange");
    }
}
