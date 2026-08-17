// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.dto.SifCollection;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.controller.GlobalExceptionHandler;
import org.sifunity.broker.environment.controller.ZoneController;
import org.sifunity.broker.environment.dto.ContextCreateBody;
import org.sifunity.broker.environment.dto.ContextCreateRequest;
import org.sifunity.broker.environment.dto.ContextCreateRequestWrapper;
import org.sifunity.broker.environment.dto.ContextDTO;
import org.sifunity.broker.environment.dto.ZoneCreateBody;
import org.sifunity.broker.environment.dto.ZoneCreateRequest;
import org.sifunity.broker.environment.dto.ZoneCreateRequestWrapper;
import org.sifunity.broker.environment.dto.ZoneDTO;
import org.sifunity.broker.environment.exception.DuplicateZoneException;
import org.sifunity.broker.environment.service.ZoneService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Preservation Property Tests for Zone Deletion fix.
 *
 * <p><strong>CRITICAL: These tests MUST PASS on unfixed code.</strong>
 * They capture the baseline behavior that must remain unchanged after
 * the zone deletion fix is applied.</p>
 *
 * <p>Preservation scope: all requests where {@code NOT isBugCondition(input)} —
 * i.e., any request that is NOT a DELETE to /{domainId}/{solutionId}/zones/{zoneId}.</p>
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>POST create-zone returns 201 with correct body</li>
 *   <li>GET list-zones returns 200</li>
 *   <li>POST create-context returns 201 with correct body</li>
 *   <li>GET list-contexts returns 200</li>
 *   <li>SifBrokerException maps to correct HTTP status</li>
 *   <li>MethodArgumentNotValidException maps to 400</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6</strong></p>
 */
class ZoneDeletionPreservationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== Property 1: Zone Creation returns 201 ==========

    /**
     * Property: Preservation — POST create-zone returns 201 Created.
     *
     * <p>For any valid zone creation request by an admin caller,
     * POST to /{domainId}/{solutionId}/zones/zone returns 201 Created.</p>
     *
     * <p><strong>Validates: Requirements 3.3</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: POST create-zone returns 201 Created")
    void createZoneReturns201(
            @ForAll("zoneIds") String zoneId,
            @ForAll("descriptions") String description) throws Exception {

        ZoneService zoneService = mock(ZoneService.class);
        ZoneController controller = new ZoneController(zoneService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Mock service returns created zone
        ZoneDTO createdZone = new ZoneDTO(
                UUID.randomUUID(), GlobalAdminConstants.GLOBAL_SOLUTION, zoneId, description, Instant.now());
        when(zoneService.createZone(anyString(), any(ZoneCreateRequest.class))).thenReturn(createdZone);

        String requestBody = objectMapper.writeValueAsString(
                new ZoneCreateRequestWrapper(new ZoneCreateBody(zoneId, description)));

        // Admin caller (global admin domain/solution) — SIF singular subpath, wrapped response
        mockMvc.perform(post("/" + GlobalAdminConstants.GLOBAL_DOMAIN + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/zones/zone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.zone.id").value(zoneId));
    }

    // ========== Property 2: List Zones returns 200 ==========

    /**
     * Property: Preservation — GET list-zones returns 200 OK.
     *
     * <p>For any GET request to /{domainId}/{solutionId}/zones,
     * the response is 200 OK.</p>
     *
     * <p><strong>Validates: Requirements 3.3</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: GET list-zones returns 200 OK")
    void listZonesReturns200(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        ZoneService zoneService = mock(ZoneService.class);
        ZoneController controller = new ZoneController(zoneService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Mock service returns zone list
        ZoneDTO zone = new ZoneDTO(UUID.randomUUID(), solutionId, "test-zone", "A zone", Instant.now());
        when(zoneService.listZonesBySolution(anyString(), anyString())).thenReturn(List.of(zone));

        mockMvc.perform(get("/" + domainId + "/" + solutionId + "/zones"))
                .andExpect(status().isOk());
    }

    // ========== Property 3: Context Creation returns 201 ==========

    /**
     * Property: Preservation — POST create-context returns 201 Created.
     *
     * <p>For any valid context creation request by an admin caller,
     * POST to /{domainId}/{solutionId}/zones/{zoneId}/contexts/context returns 201 Created.</p>
     *
     * <p><strong>Validates: Requirements 3.3</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: POST create-context returns 201 Created")
    void createContextReturns201(
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll("descriptions") String description) throws Exception {

        ZoneService zoneService = mock(ZoneService.class);
        ZoneController controller = new ZoneController(zoneService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Mock service returns created context
        ContextDTO createdContext = new ContextDTO(
                UUID.randomUUID(), zoneId, contextId, description, Instant.now());
        when(zoneService.createContext(anyString(), anyString(), anyString(), any(ContextCreateRequest.class)))
                .thenReturn(createdContext);

        String requestBody = objectMapper.writeValueAsString(
                new ContextCreateRequestWrapper(new ContextCreateBody(contextId, description)));

        // Admin caller (global admin domain/solution) — SIF singular subpath, wrapped response
        mockMvc.perform(post("/" + GlobalAdminConstants.GLOBAL_DOMAIN + "/" + GlobalAdminConstants.GLOBAL_SOLUTION
                        + "/zones/" + zoneId + "/contexts/context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.context.id").value(contextId));
    }

    // ========== Property 4: List Contexts returns 200 ==========

    /**
     * Property: Preservation — GET list-contexts returns 200 OK.
     *
     * <p>For any GET request to /{domainId}/{solutionId}/zones/{zoneId}/contexts,
     * the response is 200 OK.</p>
     *
     * <p><strong>Validates: Requirements 3.3</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: GET list-contexts returns 200 OK")
    void listContextsReturns200(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("zoneIds") String zoneId) throws Exception {

        ZoneService zoneService = mock(ZoneService.class);
        ZoneController controller = new ZoneController(zoneService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Mock service returns context list
        ContextDTO context = new ContextDTO(UUID.randomUUID(), zoneId, "DEFAULT", "Default context", Instant.now());
        when(zoneService.listContexts(anyString(), anyString(), anyString())).thenReturn(List.of(context));

        mockMvc.perform(get("/" + domainId + "/" + solutionId + "/zones/" + zoneId + "/contexts"))
                .andExpect(status().isOk());
    }

    // ========== Property 5: SifBrokerException maps to correct status ==========

    /**
     * Property: Preservation — SifBrokerException maps to its declared HTTP status.
     *
     * <p>When ZoneService throws a SifBrokerException (e.g., DuplicateZoneException → 409),
     * the GlobalExceptionHandler maps it to the exception's declared HTTP status code.</p>
     *
     * <p><strong>Validates: Requirements 3.5</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: SifBrokerException maps to declared HTTP status")
    void sifBrokerExceptionMapsToCorrectStatus(
            @ForAll("zoneIds") String zoneId) throws Exception {

        ZoneService zoneService = mock(ZoneService.class);
        ZoneController controller = new ZoneController(zoneService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // DuplicateZoneException → 409 Conflict
        when(zoneService.createZone(anyString(), any(ZoneCreateRequest.class)))
                .thenThrow(new DuplicateZoneException(GlobalAdminConstants.GLOBAL_SOLUTION, zoneId));

        String requestBody = objectMapper.writeValueAsString(
                new ZoneCreateRequestWrapper(new ZoneCreateBody(zoneId, "duplicate zone")));

        mockMvc.perform(post("/" + GlobalAdminConstants.GLOBAL_DOMAIN + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/zones/zone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.subCode").value("ZONE_002"));
    }

    // ========== Property 6: Invalid request body returns 400 ==========

    /**
     * Property: Preservation — MethodArgumentNotValidException maps to 400.
     *
     * <p>When the request body fails Bean Validation (e.g., missing zoneId),
     * the GlobalExceptionHandler returns 400 Bad Request with VAL_001.</p>
     *
     * <p><strong>Validates: Requirements 3.5</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: Invalid request body returns 400 Bad Request")
    void invalidRequestBodyReturns400(
            @ForAll("invalidZoneIds") String invalidZoneId) throws Exception {

        ZoneService zoneService = mock(ZoneService.class);
        ZoneController controller = new ZoneController(zoneService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Send invalid zone ID (under the canonical zone.id) that fails @Pattern/@NotBlank validation
        String requestBody = objectMapper.writeValueAsString(
                new ZoneCreateRequestWrapper(new ZoneCreateBody(invalidZoneId, null)));

        mockMvc.perform(post("/" + GlobalAdminConstants.GLOBAL_DOMAIN + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/zones/zone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.subCode").value("VAL_001"));
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> domainIds() {
        return Arbitraries.of("test-domain", "school-district-1", "state-edu", "catranscript");
    }

    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.of("prod-solution", "test-solution", "staging", "pilot");
    }

    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.of("zone1", "default-zone", "test-zone", "production-zone", "mockdxterazone");
    }

    @Provide
    Arbitrary<String> contextIds() {
        return Arbitraries.of("DEFAULT", "ctx-test", "ctx-staging", "reporting-ctx");
    }

    @Provide
    Arbitrary<String> descriptions() {
        return Arbitraries.of("Test zone", "Production zone", null, "Staging environment zone");
    }

    @Provide
    Arbitrary<String> invalidZoneIds() {
        return Arbitraries.of("_invalid", "-bad-start", "has spaces", "special!chars", "");
    }
}
