// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.sifunity.broker.common.constant.AlertLevel;
import org.sifunity.broker.common.constant.Exchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Property 10: Enum serialization round-trip.
 *
 * <p><strong>Property:</strong> For any valid Exchange enum value and for any valid
 * AlertLevel enum value, serializing the value to its JSON string representation
 * and then deserializing it back SHALL produce the original enum value.</p>
 *
 * <p>Pure unit test using Jackson — no Spring context needed.</p>
 *
 * <p><strong>Validates: Requirements 7.9, 7.10</strong></p>
 */
@Tag("Feature: alerts-service, Property 10: Enum serialization round-trip")
class AlertEnumSerializationPropertyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Property 10: All Exchange enum values survive JSON serialization round-trip.
     *
     * <p><strong>Validates: Requirements 7.9</strong></p>
     */
    @RepeatedTest(3)
    void exchangeEnumSerializationRoundTrip() throws Exception {
        for (Exchange original : Exchange.values()) {
            // Serialize to JSON string
            String json = MAPPER.writeValueAsString(original);

            // Deserialize back
            Exchange deserialized = MAPPER.readValue(json, Exchange.class);

            assertThat(deserialized)
                    .as("Exchange.%s should survive JSON round-trip", original.name())
                    .isEqualTo(original);
        }
    }

    /**
     * Property 10: All AlertLevel enum values survive JSON serialization round-trip.
     *
     * <p><strong>Validates: Requirements 7.10</strong></p>
     */
    @RepeatedTest(3)
    void alertLevelEnumSerializationRoundTrip() throws Exception {
        for (AlertLevel original : AlertLevel.values()) {
            // Serialize to JSON string
            String json = MAPPER.writeValueAsString(original);

            // Deserialize back
            AlertLevel deserialized = MAPPER.readValue(json, AlertLevel.class);

            assertThat(deserialized)
                    .as("AlertLevel.%s should survive JSON round-trip", original.name())
                    .isEqualTo(original);
        }
    }
}
