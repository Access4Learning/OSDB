// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.dto.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 6: Null Field Omission in JSON Serialization.
 *
 * <p>For any SifEnvironmentResponse where optional fields (instanceId, userToken,
 * applicationInfo) are null, serializing to JSON shall not include those field keys.</p>
 *
 * <p>Tag: Feature: sif-environment-response, Property 6: Null Field Omission</p>
 *
 * <p><strong>Validates: Requirements 9.6</strong></p>
 */
class NullFieldOmissionPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Provide
    Arbitrary<SifEnvironmentResponse> responsesWithNullOptionalFields() {
        Arbitrary<String> instanceIds = Arbitraries.of("inst-1", "inst-2").injectNull(0.5);
        Arbitrary<String> userTokens = Arbitraries.of("token-a", "token-b").injectNull(0.5);
        Arbitrary<ApplicationInfoDTO> appInfos = SifEnvironmentResponseArbitraries
                .validApplicationInfoDTOs().injectNull(0.5);

        return Combinators.combine(instanceIds, userTokens, appInfos)
                .as((instanceId, userToken, appInfo) -> new SifEnvironmentResponse(
                        UUID.randomUUID(),
                        EnvironmentType.DIRECT,
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        null, // solutionId
                        new DefaultZoneDTO("zone-1"),
                        "SIF_HMACSHA256",
                        instanceId,
                        userToken,
                        "TestConsumer",
                        appInfo,
                        new InfrastructureServicesDTO(List.of(
                                new InfrastructureServiceDTO("environment", "http://localhost:8081")
                        )),
                        new ProvisionedZonesDTO(List.of())
                ));
    }

    @Property(tries = 10)
    @Tag("Feature: sif-environment-response, Property 6: Null Field Omission")
    void nullOptionalFieldsOmittedFromJson(
            @ForAll("responsesWithNullOptionalFields") SifEnvironmentResponse response) throws Exception {

        String json = objectMapper.writeValueAsString(response);
        ObjectNode node = (ObjectNode) objectMapper.readTree(json);

        if (response.instanceId() == null) {
            assertThat(node.has("instanceId")).isFalse();
        } else {
            assertThat(node.has("instanceId")).isTrue();
        }

        if (response.userToken() == null) {
            assertThat(node.has("userToken")).isFalse();
        } else {
            assertThat(node.has("userToken")).isTrue();
        }

        if (response.applicationInfo() == null) {
            assertThat(node.has("applicationInfo")).isFalse();
        } else {
            assertThat(node.has("applicationInfo")).isTrue();
        }

        if (response.solutionId() == null) {
            assertThat(node.has("solutionId")).isFalse();
        }
    }
}
