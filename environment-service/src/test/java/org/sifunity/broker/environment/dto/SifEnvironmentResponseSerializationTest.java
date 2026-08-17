// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.EnvironmentType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SIF 3.7 environment response JSON serialization.
 *
 * Covers: JSON field names match OpenAPI schema, null fields omitted,
 * {@code @JsonUnwrapped} on registration response.
 */
@DisplayName("SifEnvironmentResponse Serialization Tests")
class SifEnvironmentResponseSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Test
    @DisplayName("JSON field names match SIF 3.7 OpenAPI schema")
    void jsonFieldNamesMatchSchema() throws Exception {
        SifEnvironmentResponse response = buildFullResponse();
        String json = objectMapper.writeValueAsString(response);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("id")).isTrue();
        assertThat(node.has("type")).isTrue();
        assertThat(node.has("fingerprint")).isTrue();
        assertThat(node.has("sessionToken")).isTrue();
        assertThat(node.has("defaultZone")).isTrue();
        assertThat(node.has("authenticationMethod")).isTrue();
        assertThat(node.has("consumerName")).isTrue();
        assertThat(node.has("infrastructureServices")).isTrue();
        assertThat(node.has("provisionedZones")).isTrue();

        assertThat(node.get("defaultZone").has("id")).isTrue();
        assertThat(node.get("infrastructureServices").has("infrastructureService")).isTrue();
        assertThat(node.get("provisionedZones").has("provisionedZone")).isTrue();
    }

    @Test
    @DisplayName("Null optional fields are omitted from JSON")
    void nullFieldsOmitted() throws Exception {
        SifEnvironmentResponse response = new SifEnvironmentResponse(
                UUID.randomUUID(), EnvironmentType.BROKERED, "fp", "token",
                null, new DefaultZoneDTO("z1"), "SIF_HMACSHA256",
                null, null, "consumer",
                null, new InfrastructureServicesDTO(List.of()),
                new ProvisionedZonesDTO(List.of())
        );
        String json = objectMapper.writeValueAsString(response);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("solutionId")).isFalse();
        assertThat(node.has("instanceId")).isFalse();
        assertThat(node.has("userToken")).isFalse();
        assertThat(node.has("applicationInfo")).isFalse();
    }

    @Test
    @DisplayName("Registration response wraps environment fields under 'environment' key (SIF 3.7)")
    void registrationResponseWrapsEnvironmentFields() throws Exception {
        SifEnvironmentResponse envResponse = buildFullResponse();
        SifEnvironmentRegistrationResponse regResponse =
                SifEnvironmentRegistrationResponse.of(envResponse, "shared-secret-value");

        String json = objectMapper.writeValueAsString(regResponse);
        JsonNode node = objectMapper.readTree(json);

        // Top-level should have "environment" wrapper key
        assertThat(node.has("environment")).isTrue();

        // Environment fields should be inside the "environment" key
        JsonNode envNode = node.get("environment");
        assertThat(envNode.has("id")).isTrue();
        assertThat(envNode.has("type")).isTrue();
        assertThat(envNode.has("fingerprint")).isTrue();
        assertThat(envNode.has("consumerName")).isTrue();
        assertThat(envNode.has("sharedSecret")).isTrue();
        assertThat(envNode.get("sharedSecret").asText()).isEqualTo("shared-secret-value");
    }

    @Test
    @DisplayName("ApplicationInfo with nested ProductIdentity serializes correctly")
    void applicationInfoSerializesCorrectly() throws Exception {
        ApplicationInfoDTO appInfo = new ApplicationInfoDTO(
                "key-1", "3.7", "http://ns.example.com", "REST",
                new ProductIdentityDTO("Acme", "SIS", "2.0", null),
                null
        );
        SifEnvironmentResponse response = new SifEnvironmentResponse(
                UUID.randomUUID(), EnvironmentType.BROKERED, "fp", "token",
                null, new DefaultZoneDTO("z1"), "SIF_HMACSHA256",
                null, null, "consumer",
                appInfo, new InfrastructureServicesDTO(List.of()),
                new ProvisionedZonesDTO(List.of())
        );
        String json = objectMapper.writeValueAsString(response);
        JsonNode node = objectMapper.readTree(json);

        JsonNode ai = node.get("applicationInfo");
        assertThat(ai).isNotNull();
        assertThat(ai.get("applicationKey").asText()).isEqualTo("key-1");
        assertThat(ai.get("applicationProduct").get("vendorName").asText()).isEqualTo("Acme");
        // adapterProduct is null, should be omitted
        assertThat(ai.has("adapterProduct")).isFalse();
    }

    // ========== Helpers ==========

    private SifEnvironmentResponse buildFullResponse() {
        return new SifEnvironmentResponse(
                UUID.randomUUID(), EnvironmentType.BROKERED, "fingerprint-1",
                "session-token-1", "solution-1",
                new DefaultZoneDTO("default-zone"),
                "SIF_HMACSHA256", "instance-1", "user-token-1", "TestApp",
                new ApplicationInfoDTO("key", "3.7", "ns", "REST", null, null),
                new InfrastructureServicesDTO(List.of(
                        new InfrastructureServiceDTO("environment", "http://localhost:8081")
                )),
                new ProvisionedZonesDTO(List.of())
        );
    }
}
