// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AlertLevel;
import org.sifunity.broker.common.constant.Exchange;
import org.sifunity.broker.environment.controller.AlertsController;
import org.sifunity.broker.environment.controller.GlobalExceptionHandler;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.service.AlertsService;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Preservation Property Tests for Alerts Service.
 *
 * <p><strong>CRITICAL: These tests MUST PASS on unfixed code.</strong>
 * They capture the baseline correct behavior that must remain unchanged
 * after the null-guard fix is applied.</p>
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Valid wrapped POST to /alerts/alert returns 201 Created</li>
 *   <li>GET /alerts returns 200 OK</li>
 *   <li>GET /alerts/{id} returns 200 OK</li>
 *   <li>PUT /alerts returns 405 Method Not Allowed with ALERT_006</li>
 *   <li>DELETE /alerts/{id} returns 405 Method Not Allowed with ALERT_006</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.4</strong></p>
 *
 * @see AlertsController
 */
class AlertsPreservationTest {

    private static final String DOMAIN_ID = "test-domain";
    private static final String SOLUTION_ID = "test-solution";
    private static final String BASE_PATH = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/alerts";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== Property 1: Valid wrapped POST returns 201 ==========

    /**
     * Property: Preservation — Valid wrapped POST returns 201 Created.
     *
     * <p>For any valid wrapped alert body with required fields (reporter, exchange, level),
     * POST to /alerts/alert returns 201 Created with the alert response.</p>
     *
     * <p><strong>Validates: Requirements 3.1</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: Valid wrapped POST /alerts/alert returns 201 Created")
    void validWrappedPostReturns201(
            @ForAll("reporters") String reporter,
            @ForAll("exchanges") Exchange exchange,
            @ForAll("alertLevels") AlertLevel level) throws Exception {

        // Set up mocks and controller
        AlertsService alertsService = mock(AlertsService.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        AlertsController controller = new AlertsController(alertsService, authorizationService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        Environment mockEnvironment = mock(Environment.class);
        when(mockEnvironment.getId()).thenReturn(UUID.randomUUID());

        // Mock service to return a valid AlertDTO
        AlertDTO returnedAlert = new AlertDTO(
                UUID.randomUUID(), reporter, null, exchange, level,
                null, null, null, null, null, null, null, null, null);

        when(alertsService.createAlert(
                anyString(), anyString(), any(AlertCreateBody.class),
                any(Environment.class), anyBoolean()))
                .thenReturn(returnedAlert);
        when(authorizationService.isAdmin(any())).thenReturn(false);

        // Build valid wrapped request body
        String requestBody = objectMapper.writeValueAsString(
                new AlertCreateRequest(new AlertCreateBody(
                        reporter, exchange, level,
                        null, null, null, null, null, null, null, null, null, null)));

        // Act & Assert
        mockMvc.perform(post(BASE_PATH + "/alert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .requestAttr("requester", mockEnvironment))
                .andExpect(status().isCreated());
    }

    // ========== Property 2: GET collection returns 200 ==========

    /**
     * Property: Preservation — GET /alerts returns 200 OK.
     *
     * <p>For any GET request to the alerts collection endpoint,
     * the response is 200 OK.</p>
     *
     * <p><strong>Validates: Requirements 3.2</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: GET /alerts returns 200 OK")
    void getCollectionReturns200(
            @ForAll("reporters") String reporter,
            @ForAll("exchanges") Exchange exchange,
            @ForAll("alertLevels") AlertLevel level) throws Exception {

        // Set up mocks and controller
        AlertsService alertsService = mock(AlertsService.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        AlertsController controller = new AlertsController(alertsService, authorizationService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        Environment mockEnvironment = mock(Environment.class);
        when(mockEnvironment.getId()).thenReturn(UUID.randomUUID());

        // Mock service to return a list with one alert
        AlertDTO alert = new AlertDTO(
                UUID.randomUUID(), reporter, null, exchange, level,
                null, null, null, null, null, null, null, null, null);

        when(alertsService.listAlerts(any(Environment.class), anyBoolean()))
                .thenReturn(List.of(alert));
        when(authorizationService.isAdmin(any())).thenReturn(false);

        // Act & Assert
        mockMvc.perform(get(BASE_PATH)
                        .requestAttr("requester", mockEnvironment))
                .andExpect(status().isOk());
    }

    // ========== Property 3: GET single returns 200 ==========

    /**
     * Property: Preservation — GET /alerts/{id} returns 200 OK.
     *
     * <p>For any GET request to a specific alert by ID,
     * the response is 200 OK when the alert exists.</p>
     *
     * <p><strong>Validates: Requirements 3.3</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: GET /alerts/{id} returns 200 OK")
    void getSingleReturns200(
            @ForAll("reporters") String reporter,
            @ForAll("exchanges") Exchange exchange,
            @ForAll("alertLevels") AlertLevel level) throws Exception {

        // Set up mocks and controller
        AlertsService alertsService = mock(AlertsService.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        AlertsController controller = new AlertsController(alertsService, authorizationService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        Environment mockEnvironment = mock(Environment.class);
        when(mockEnvironment.getId()).thenReturn(UUID.randomUUID());

        // Mock service to return a valid AlertDTO
        UUID alertId = UUID.randomUUID();
        AlertDTO alert = new AlertDTO(
                alertId, reporter, null, exchange, level,
                null, null, null, null, null, null, null, null, null);

        when(alertsService.getAlertById(any(UUID.class), any(Environment.class), anyBoolean()))
                .thenReturn(alert);
        when(authorizationService.isAdmin(any())).thenReturn(false);

        // Act & Assert
        mockMvc.perform(get(BASE_PATH + "/{id}", alertId)
                        .requestAttr("requester", mockEnvironment))
                .andExpect(status().isOk());
    }

    // ========== Property 4: PUT collection returns 405 ==========

    /**
     * Property: Preservation — PUT /alerts returns 405 with ALERT_006.
     *
     * <p>For any PUT request to the alerts collection, the response
     * is 405 Method Not Allowed with error code ALERT_006.</p>
     *
     * <p><strong>Validates: Requirements 3.4</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: PUT /alerts returns 405 with ALERT_006")
    void putCollectionReturns405(
            @ForAll("reporters") String reporter) throws Exception {

        // Set up controller with mocks
        AlertsService alertsService = mock(AlertsService.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        AlertsController controller = new AlertsController(alertsService, authorizationService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Act & Assert: PUT on collection → 405
        mockMvc.perform(put(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reporter\":\"" + reporter + "\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.subCode").value("ALERT_006"));
    }

    // ========== Property 5: DELETE single returns 405 ==========

    /**
     * Property: Preservation — DELETE /alerts/{id} returns 405 with ALERT_006.
     *
     * <p>For any DELETE request to a specific alert, the response
     * is 405 Method Not Allowed with error code ALERT_006.</p>
     *
     * <p><strong>Validates: Requirements 3.4</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: DELETE /alerts/{id} returns 405 with ALERT_006")
    void deleteSingleReturns405(
            @ForAll("reporters") String reporter) throws Exception {

        // Set up controller with mocks
        AlertsService alertsService = mock(AlertsService.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        AlertsController controller = new AlertsController(alertsService, authorizationService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UUID alertId = UUID.randomUUID();

        // Act & Assert: DELETE on single alert → 405
        mockMvc.perform(delete(BASE_PATH + "/{id}", alertId))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.subCode").value("ALERT_006"));
    }

    // ========== Test Data Generators ==========

    @Provide
    Arbitrary<String> reporters() {
        return Arbitraries.of(
                "StudentInfoSystem", "LMS-App", "Assessment-Platform",
                "GradebookService", "AttendanceTracker", "TransportManager",
                "FoodServiceApp", "LibrarySystem", "HealthRecords",
                "SpecialEdTracker", "ParentPortal", "StaffDirectory");
    }

    @Provide
    Arbitrary<Exchange> exchanges() {
        return Arbitraries.of(Exchange.values());
    }

    @Provide
    Arbitrary<AlertLevel> alertLevels() {
        return Arbitraries.of(AlertLevel.values());
    }
}
