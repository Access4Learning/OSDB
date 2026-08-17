// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.eventsconnector.exception.EventValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for EventValidator.
 * Tests all validation branches for service name, zone ID, context ID, and payload.
 */
@DisplayName("EventValidator Tests")
class EventValidatorTest {

    private EventValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        validator = new EventValidator();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("validateServiceName")
    class ValidateServiceName {

        @Test
        @DisplayName("should throw when serviceName is null")
        void shouldThrowWhenNull() {
            assertThatThrownBy(() -> validator.validateServiceName(null))
                .isInstanceOf(EventValidationException.class)
                .hasMessageContaining("serviceName");
        }

        @Test
        @DisplayName("should throw when serviceName is empty")
        void shouldThrowWhenEmpty() {
            assertThatThrownBy(() -> validator.validateServiceName(""))
                .isInstanceOf(EventValidationException.class)
                .hasMessageContaining("serviceName");
        }

        @Test
        @DisplayName("should throw when serviceName is blank")
        void shouldThrowWhenBlank() {
            assertThatThrownBy(() -> validator.validateServiceName("   "))
                .isInstanceOf(EventValidationException.class)
                .hasMessageContaining("serviceName");
        }

        @Test
        @DisplayName("should not throw when serviceName is valid")
        void shouldNotThrowWhenValid() {
            validator.validateServiceName("StudentPersonal");
            // No exception = pass
        }
    }

    @Nested
    @DisplayName("validateZoneId")
    class ValidateZoneId {

        @Test
        @DisplayName("should throw when zoneId is null")
        void shouldThrowWhenNull() {
            assertThatThrownBy(() -> validator.validateZoneId(null))
                .isInstanceOf(EventValidationException.class)
                .hasMessageContaining("zoneId");
        }

        @Test
        @DisplayName("should throw when zoneId is empty")
        void shouldThrowWhenEmpty() {
            assertThatThrownBy(() -> validator.validateZoneId(""))
                .isInstanceOf(EventValidationException.class)
                .hasMessageContaining("zoneId");
        }

        @Test
        @DisplayName("should throw when zoneId is blank")
        void shouldThrowWhenBlank() {
            assertThatThrownBy(() -> validator.validateZoneId("   "))
                .isInstanceOf(EventValidationException.class)
                .hasMessageContaining("zoneId");
        }

        @Test
        @DisplayName("should not throw when zoneId is valid")
        void shouldNotThrowWhenValid() {
            validator.validateZoneId("zone-1");
            // No exception = pass
        }
    }

    @Nested
    @DisplayName("validateAndNormalizeContextId")
    class ValidateAndNormalizeContextId {

        @Test
        @DisplayName("should return DEFAULT when contextId is null")
        void shouldReturnDefaultWhenNull() {
            String result = validator.validateAndNormalizeContextId(null);
            assertThat(result).isEqualTo("DEFAULT");
        }

        @Test
        @DisplayName("should return DEFAULT when contextId is empty")
        void shouldReturnDefaultWhenEmpty() {
            String result = validator.validateAndNormalizeContextId("");
            assertThat(result).isEqualTo("DEFAULT");
        }

        @Test
        @DisplayName("should return DEFAULT when contextId is blank")
        void shouldReturnDefaultWhenBlank() {
            String result = validator.validateAndNormalizeContextId("   ");
            assertThat(result).isEqualTo("DEFAULT");
        }

        @Test
        @DisplayName("should return contextId as-is when provided")
        void shouldReturnContextIdWhenProvided() {
            String result = validator.validateAndNormalizeContextId("ctx-1");
            assertThat(result).isEqualTo("ctx-1");
        }
    }

    @Nested
    @DisplayName("validatePayload")
    class ValidatePayload {

        @Test
        @DisplayName("should throw when payload is null")
        void shouldThrowWhenNull() {
            assertThatThrownBy(() -> validator.validatePayload(null))
                .isInstanceOf(EventValidationException.class)
                .hasMessageContaining("payload");
        }

        @Test
        @DisplayName("should throw when payload is MissingNode")
        void shouldThrowWhenMissingNode() {
            assertThatThrownBy(() -> validator.validatePayload(MissingNode.getInstance()))
                .isInstanceOf(EventValidationException.class)
                .hasMessageContaining("payload");
        }

        @Test
        @DisplayName("should throw when payload is NullNode")
        void shouldThrowWhenNullNode() {
            assertThatThrownBy(() -> validator.validatePayload(NullNode.getInstance()))
                .isInstanceOf(EventValidationException.class)
                .hasMessageContaining("payload");
        }

        @Test
        @DisplayName("should not throw when payload is valid JSON object")
        void shouldNotThrowWhenValidObject() {
            JsonNode payload = objectMapper.createObjectNode().put("key", "value");
            validator.validatePayload(payload);
            // No exception = pass
        }

        @Test
        @DisplayName("should not throw when payload is valid JSON array")
        void shouldNotThrowWhenValidArray() {
            JsonNode payload = objectMapper.createArrayNode().add("item1");
            validator.validatePayload(payload);
            // No exception = pass
        }
    }

    @Nested
    @DisplayName("validateEvent")
    class ValidateEvent {

        @Test
        @DisplayName("should validate all fields and return normalized contextId")
        void shouldValidateAllFieldsAndReturnNormalizedContextId() {
            JsonNode payload = objectMapper.createObjectNode().put("test", "data");

            String result = validator.validateEvent("StudentPersonal", "zone-1", null, payload);

            assertThat(result).isEqualTo("DEFAULT");
        }

        @Test
        @DisplayName("should return provided contextId when not blank")
        void shouldReturnProvidedContextId() {
            JsonNode payload = objectMapper.createObjectNode().put("test", "data");

            String result = validator.validateEvent("StudentPersonal", "zone-1", "ctx-1", payload);

            assertThat(result).isEqualTo("ctx-1");
        }

        @Test
        @DisplayName("should throw when serviceName is invalid")
        void shouldThrowWhenServiceNameInvalid() {
            JsonNode payload = objectMapper.createObjectNode();

            assertThatThrownBy(() -> validator.validateEvent(null, "zone-1", "ctx-1", payload))
                .isInstanceOf(EventValidationException.class)
                .hasMessageContaining("serviceName");
        }

        @Test
        @DisplayName("should throw when zoneId is invalid")
        void shouldThrowWhenZoneIdInvalid() {
            JsonNode payload = objectMapper.createObjectNode();

            assertThatThrownBy(() -> validator.validateEvent("StudentPersonal", "", "ctx-1", payload))
                .isInstanceOf(EventValidationException.class)
                .hasMessageContaining("zoneId");
        }

        @Test
        @DisplayName("should throw when payload is invalid")
        void shouldThrowWhenPayloadInvalid() {
            assertThatThrownBy(() -> validator.validateEvent("StudentPersonal", "zone-1", "ctx-1", null))
                .isInstanceOf(EventValidationException.class)
                .hasMessageContaining("payload");
        }
    }
}
