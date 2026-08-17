// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.unit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.AlertLevel;
import org.sifunity.broker.common.constant.Exchange;
import org.sifunity.broker.environment.dto.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests verifying the JSON serialization format of alert response wrappers.
 *
 * <p>Single alerts are wrapped under {@code "alert"} key. Collections are wrapped
 * under {@code "alerts"} containing an {@code "alert"} array.</p>
 *
 * <p><strong>Validates: Requirements 7.6, 7.7</strong></p>
 */
@DisplayName("Alert Response Wrapper Format")
class AlertResponseWrapperFormatTest {

    private ObjectMapper objectMapper;
    private AlertDTO sampleAlert;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Jackson needs to discover modules for proper enum serialization
        objectMapper.findAndRegisterModules();

        sampleAlert = new AlertDTO(
                UUID.randomUUID(),
                "TestApp",
                "DataValidation",
                Exchange.REQUEST,
                AlertLevel.ERROR,
                "Invalid student record",
                "msg-123",
                null,
                "java.lang.IllegalArgumentException",
                "/StudentPersonal/Name",
                4,
                11,
                "ERR-4011",
                new SifExtendedElements(List.of(
                        new SifExtendedElement("reportingEnvironmentFingerprint", "fp-xyz"),
                        new SifExtendedElement("createdAt", "2026-04-22T01:30:00.000Z")
                ))
        );
    }

    /**
     * Requirement 7.6: Single alert wrapped under "alert" key.
     */
    @Test
    @DisplayName("AlertResponse serializes with top-level 'alert' key")
    void alertResponse_wrapsUnderAlertKey() throws Exception {
        AlertResponse response = new AlertResponse(sampleAlert);

        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        // Top-level must have "alert" key
        assertThat(root.has("alert")).isTrue();
        assertThat(root.size()).isEqualTo(1);

        // Alert fields accessible under "alert"
        JsonNode alertNode = root.get("alert");
        assertThat(alertNode.get("id").asText()).isEqualTo(sampleAlert.id().toString());
        assertThat(alertNode.get("reporter").asText()).isEqualTo("TestApp");
        assertThat(alertNode.get("exchange").asText()).isEqualTo("REQUEST");
        assertThat(alertNode.get("level").asText()).isEqualTo("ERROR");
    }

    /**
     * Requirement 7.7: Collection wrapped under "alerts" → "alert" array.
     */
    @Test
    @DisplayName("AlertCollectionResponse serializes with 'alerts.alert' array")
    void alertCollectionResponse_wrapsUnderAlertsAlertArray() throws Exception {
        AlertDTO secondAlert = new AlertDTO(
                UUID.randomUUID(),
                "AnotherApp",
                null,
                Exchange.EVENT,
                AlertLevel.WARNING,
                "Timeout occurred",
                null, null, null, null, null, null, null, null
        );

        AlertCollectionResponse response = new AlertCollectionResponse(
                new AlertCollection(List.of(sampleAlert, secondAlert)));

        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        // Top-level must have "alerts" key
        assertThat(root.has("alerts")).isTrue();
        assertThat(root.size()).isEqualTo(1);

        // "alerts" must contain "alert" array
        JsonNode alertsNode = root.get("alerts");
        assertThat(alertsNode.has("alert")).isTrue();
        assertThat(alertsNode.size()).isEqualTo(1);

        JsonNode alertArray = alertsNode.get("alert");
        assertThat(alertArray.isArray()).isTrue();
        assertThat(alertArray.size()).isEqualTo(2);

        // Verify array elements
        assertThat(alertArray.get(0).get("reporter").asText()).isEqualTo("TestApp");
        assertThat(alertArray.get(1).get("reporter").asText()).isEqualTo("AnotherApp");
    }

    /**
     * Requirement 7.7: Empty collection still has correct structure.
     */
    @Test
    @DisplayName("Empty collection serializes with 'alerts.alert' as empty array")
    void emptyCollection_serializesWithCorrectStructure() throws Exception {
        AlertCollectionResponse response = new AlertCollectionResponse(
                new AlertCollection(List.of()));

        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.has("alerts")).isTrue();
        JsonNode alertsNode = root.get("alerts");
        assertThat(alertsNode.has("alert")).isTrue();
        assertThat(alertsNode.get("alert").isArray()).isTrue();
        assertThat(alertsNode.get("alert").size()).isEqualTo(0);
    }

    /**
     * Requirement 7.6: SIF_ExtendedElements serialized with correct PESC-JSON casing.
     */
    @Test
    @DisplayName("SIF_ExtendedElements uses PESC-JSON casing (Name uppercase, value lowercase)")
    void extendedElements_usesPescJsonCasing() throws Exception {
        AlertResponse response = new AlertResponse(sampleAlert);

        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        JsonNode extElements = root.get("alert").get("SIF_ExtendedElements");
        assertThat(extElements).isNotNull();

        JsonNode elementArray = extElements.get("SIF_ExtendedElement");
        assertThat(elementArray.isArray()).isTrue();
        assertThat(elementArray.size()).isEqualTo(2);

        // Verify PESC-JSON casing: "Name" (uppercase N) and "value" (lowercase v)
        JsonNode firstElement = elementArray.get(0);
        assertThat(firstElement.has("Name")).isTrue();
        assertThat(firstElement.has("value")).isTrue();
        assertThat(firstElement.get("Name").asText()).isEqualTo("reportingEnvironmentFingerprint");
        assertThat(firstElement.get("value").asText()).isEqualTo("fp-xyz");
    }
}
