// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for LoggingUtils.
 * Tests correlation ID management, request context management, and MDC cleanup.
 */
class LoggingUtilsTest {

    @BeforeEach
    void setUp() {
        // Clear MDC before each test to ensure clean state
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        // Clear MDC after each test to prevent context leakage
        MDC.clear();
    }

    // ========== setCorrelationId(String) Tests ==========

    @Test
    void setCorrelationId_withString_setsValueInMDC() {
        String correlationId = "test-correlation-id";
        
        LoggingUtils.setCorrelationId(correlationId);
        
        assertThat(MDC.get("correlationId")).isEqualTo(correlationId);
    }

    @Test
    void setCorrelationId_withUuidString_setsValueInMDC() {
        String correlationId = "550e8400-e29b-41d4-a716-446655440000";
        
        LoggingUtils.setCorrelationId(correlationId);
        
        assertThat(MDC.get("correlationId")).isEqualTo(correlationId);
    }

    @Test
    void setCorrelationId_withEmptyString_setsEmptyValueInMDC() {
        LoggingUtils.setCorrelationId("");
        
        assertThat(MDC.get("correlationId")).isEqualTo("");
    }

    @Test
    void setCorrelationId_withNull_setsNullInMDC() {
        LoggingUtils.setCorrelationId((String) null);
        
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void setCorrelationId_overwritesPreviousValue() {
        LoggingUtils.setCorrelationId("first-id");
        LoggingUtils.setCorrelationId("second-id");
        
        assertThat(MDC.get("correlationId")).isEqualTo("second-id");
    }

    @Test
    void setCorrelationId_withSpecialCharacters_setsValueInMDC() {
        String correlationId = "test-id_123!@#$%";
        
        LoggingUtils.setCorrelationId(correlationId);
        
        assertThat(MDC.get("correlationId")).isEqualTo(correlationId);
    }

    @Test
    void setCorrelationId_withWhitespace_setsValueInMDC() {
        String correlationId = "test id with spaces";
        
        LoggingUtils.setCorrelationId(correlationId);
        
        assertThat(MDC.get("correlationId")).isEqualTo(correlationId);
    }

    @Test
    void setCorrelationId_withLongString_setsValueInMDC() {
        String correlationId = "a".repeat(1000);
        
        LoggingUtils.setCorrelationId(correlationId);
        
        assertThat(MDC.get("correlationId")).isEqualTo(correlationId);
    }

    // ========== setCorrelationId(UUID) Tests ==========

    @Test
    void setCorrelationId_withUuid_setsStringValueInMDC() {
        UUID correlationId = UUID.randomUUID();
        
        LoggingUtils.setCorrelationId(correlationId);
        
        assertThat(MDC.get("correlationId")).isEqualTo(correlationId.toString());
    }

    @Test
    void setCorrelationId_withNilUuid_setsValueInMDC() {
        UUID correlationId = new UUID(0L, 0L);
        
        LoggingUtils.setCorrelationId(correlationId);
        
        assertThat(MDC.get("correlationId")).isEqualTo("00000000-0000-0000-0000-000000000000");
    }

    @Test
    void setCorrelationId_withUuidNull_throwsNullPointerException() {
        assertThatThrownBy(() -> LoggingUtils.setCorrelationId((UUID) null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void setCorrelationId_withMultipleUuids_overwritesPreviousValue() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        
        LoggingUtils.setCorrelationId(firstId);
        LoggingUtils.setCorrelationId(secondId);
        
        assertThat(MDC.get("correlationId")).isEqualTo(secondId.toString());
    }

    // ========== getCorrelationId() Tests ==========

    @Test
    void getCorrelationId_whenSet_returnsValue() {
        String correlationId = "test-correlation-id";
        LoggingUtils.setCorrelationId(correlationId);
        
        String result = LoggingUtils.getCorrelationId();
        
        assertThat(result).isEqualTo(correlationId);
    }

    @Test
    void getCorrelationId_whenNotSet_returnsNull() {
        String result = LoggingUtils.getCorrelationId();
        
        assertThat(result).isNull();
    }

    @Test
    void getCorrelationId_afterClear_returnsNull() {
        LoggingUtils.setCorrelationId("test-id");
        LoggingUtils.clearContext();
        
        String result = LoggingUtils.getCorrelationId();
        
        assertThat(result).isNull();
    }

    @Test
    void getCorrelationId_afterSetToNull_returnsNull() {
        LoggingUtils.setCorrelationId("test-id");
        LoggingUtils.setCorrelationId((String) null);
        
        String result = LoggingUtils.getCorrelationId();
        
        assertThat(result).isNull();
    }

    @Test
    void getCorrelationId_afterSetToEmptyString_returnsEmptyString() {
        LoggingUtils.setCorrelationId("");
        
        String result = LoggingUtils.getCorrelationId();
        
        assertThat(result).isEqualTo("");
    }

    // ========== setRequestContext() Tests ==========

    @Test
    void setRequestContext_withAllParameters_setsAllValuesInMDC() {
        String environmentId = "env-123";
        String serviceName = "StudentPersonal";
        String zoneId = "default";
        String contextId = "context-1";
        
        LoggingUtils.setRequestContext(environmentId, serviceName, zoneId, contextId);
        
        assertThat(MDC.get("environmentId")).isEqualTo(environmentId);
        assertThat(MDC.get("serviceName")).isEqualTo(serviceName);
        assertThat(MDC.get("zoneId")).isEqualTo(zoneId);
        assertThat(MDC.get("contextId")).isEqualTo(contextId);
    }

    @Test
    void setRequestContext_withNullEnvironmentId_doesNotSetEnvironmentId() {
        LoggingUtils.setRequestContext(null, "StudentPersonal", "default", "context-1");
        
        assertThat(MDC.get("environmentId")).isNull();
        assertThat(MDC.get("serviceName")).isEqualTo("StudentPersonal");
        assertThat(MDC.get("zoneId")).isEqualTo("default");
        assertThat(MDC.get("contextId")).isEqualTo("context-1");
    }

    @Test
    void setRequestContext_withNullServiceName_doesNotSetServiceName() {
        LoggingUtils.setRequestContext("env-123", null, "default", "context-1");
        
        assertThat(MDC.get("environmentId")).isEqualTo("env-123");
        assertThat(MDC.get("serviceName")).isNull();
        assertThat(MDC.get("zoneId")).isEqualTo("default");
        assertThat(MDC.get("contextId")).isEqualTo("context-1");
    }

    @Test
    void setRequestContext_withNullZoneId_doesNotSetZoneId() {
        LoggingUtils.setRequestContext("env-123", "StudentPersonal", null, "context-1");
        
        assertThat(MDC.get("environmentId")).isEqualTo("env-123");
        assertThat(MDC.get("serviceName")).isEqualTo("StudentPersonal");
        assertThat(MDC.get("zoneId")).isNull();
        assertThat(MDC.get("contextId")).isEqualTo("context-1");
    }

    @Test
    void setRequestContext_withNullContextId_doesNotSetContextId() {
        LoggingUtils.setRequestContext("env-123", "StudentPersonal", "default", null);
        
        assertThat(MDC.get("environmentId")).isEqualTo("env-123");
        assertThat(MDC.get("serviceName")).isEqualTo("StudentPersonal");
        assertThat(MDC.get("zoneId")).isEqualTo("default");
        assertThat(MDC.get("contextId")).isNull();
    }

    @Test
    void setRequestContext_withAllNulls_doesNotSetAnyValues() {
        LoggingUtils.setRequestContext(null, null, null, null);
        
        assertThat(MDC.get("environmentId")).isNull();
        assertThat(MDC.get("serviceName")).isNull();
        assertThat(MDC.get("zoneId")).isNull();
        assertThat(MDC.get("contextId")).isNull();
    }

    @Test
    void setRequestContext_overwritesPreviousValues() {
        LoggingUtils.setRequestContext("env-1", "Service1", "zone-1", "context-1");
        LoggingUtils.setRequestContext("env-2", "Service2", "zone-2", "context-2");
        
        assertThat(MDC.get("environmentId")).isEqualTo("env-2");
        assertThat(MDC.get("serviceName")).isEqualTo("Service2");
        assertThat(MDC.get("zoneId")).isEqualTo("zone-2");
        assertThat(MDC.get("contextId")).isEqualTo("context-2");
    }

    @Test
    void setRequestContext_withEmptyStrings_setsEmptyValues() {
        LoggingUtils.setRequestContext("", "", "", "");
        
        assertThat(MDC.get("environmentId")).isEqualTo("");
        assertThat(MDC.get("serviceName")).isEqualTo("");
        assertThat(MDC.get("zoneId")).isEqualTo("");
        assertThat(MDC.get("contextId")).isEqualTo("");
    }

    @Test
    void setRequestContext_withWhitespace_setsWhitespaceValues() {
        LoggingUtils.setRequestContext("   ", "   ", "   ", "   ");
        
        assertThat(MDC.get("environmentId")).isEqualTo("   ");
        assertThat(MDC.get("serviceName")).isEqualTo("   ");
        assertThat(MDC.get("zoneId")).isEqualTo("   ");
        assertThat(MDC.get("contextId")).isEqualTo("   ");
    }

    @Test
    void setRequestContext_withSpecialCharacters_setsValues() {
        LoggingUtils.setRequestContext("env!@#", "service$%^", "zone&*(", "context)_+");
        
        assertThat(MDC.get("environmentId")).isEqualTo("env!@#");
        assertThat(MDC.get("serviceName")).isEqualTo("service$%^");
        assertThat(MDC.get("zoneId")).isEqualTo("zone&*(");
        assertThat(MDC.get("contextId")).isEqualTo("context)_+");
    }

    @Test
    void setRequestContext_withLongStrings_setsValues() {
        String longString = "a".repeat(1000);
        
        LoggingUtils.setRequestContext(longString, longString, longString, longString);
        
        assertThat(MDC.get("environmentId")).isEqualTo(longString);
        assertThat(MDC.get("serviceName")).isEqualTo(longString);
        assertThat(MDC.get("zoneId")).isEqualTo(longString);
        assertThat(MDC.get("contextId")).isEqualTo(longString);
    }

    @Test
    void setRequestContext_partialUpdate_updatesOnlyNonNullValues() {
        // Set initial values
        LoggingUtils.setRequestContext("env-1", "Service1", "zone-1", "context-1");
        
        // Update only some values (others are null)
        LoggingUtils.setRequestContext("env-2", null, "zone-2", null);
        
        // Non-null values should be updated, null values should leave previous values intact
        assertThat(MDC.get("environmentId")).isEqualTo("env-2");
        assertThat(MDC.get("serviceName")).isEqualTo("Service1"); // Not updated
        assertThat(MDC.get("zoneId")).isEqualTo("zone-2");
        assertThat(MDC.get("contextId")).isEqualTo("context-1"); // Not updated
    }

    // ========== clearContext() Tests ==========

    @Test
    void clearContext_removesAllMDCValues() {
        LoggingUtils.setCorrelationId("test-id");
        LoggingUtils.setRequestContext("env-123", "StudentPersonal", "default", "context-1");
        
        LoggingUtils.clearContext();
        
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("environmentId")).isNull();
        assertThat(MDC.get("serviceName")).isNull();
        assertThat(MDC.get("zoneId")).isNull();
        assertThat(MDC.get("contextId")).isNull();
    }

    @Test
    void clearContext_whenNoValuesSet_doesNotThrow() {
        LoggingUtils.clearContext();
        
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void clearContext_calledMultipleTimes_doesNotThrow() {
        LoggingUtils.setCorrelationId("test-id");
        
        LoggingUtils.clearContext();
        LoggingUtils.clearContext();
        LoggingUtils.clearContext();
        
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void clearContext_removesCustomMDCValues() {
        // Set some custom MDC values directly
        MDC.put("customKey1", "value1");
        MDC.put("customKey2", "value2");
        LoggingUtils.setCorrelationId("test-id");
        
        LoggingUtils.clearContext();
        
        // All values should be cleared, including custom ones
        assertThat(MDC.get("customKey1")).isNull();
        assertThat(MDC.get("customKey2")).isNull();
        assertThat(MDC.get("correlationId")).isNull();
    }

    // ========== Integration Tests ==========

    @Test
    void fullWorkflow_setCorrelationIdAndContext_thenClear() {
        // Set correlation ID
        UUID correlationId = UUID.randomUUID();
        LoggingUtils.setCorrelationId(correlationId);
        assertThat(LoggingUtils.getCorrelationId()).isEqualTo(correlationId.toString());
        
        // Set request context
        LoggingUtils.setRequestContext("env-123", "StudentPersonal", "default", "context-1");
        assertThat(MDC.get("environmentId")).isEqualTo("env-123");
        assertThat(MDC.get("serviceName")).isEqualTo("StudentPersonal");
        assertThat(MDC.get("zoneId")).isEqualTo("default");
        assertThat(MDC.get("contextId")).isEqualTo("context-1");
        
        // Clear context
        LoggingUtils.clearContext();
        assertThat(LoggingUtils.getCorrelationId()).isNull();
        assertThat(MDC.get("environmentId")).isNull();
        assertThat(MDC.get("serviceName")).isNull();
        assertThat(MDC.get("zoneId")).isNull();
        assertThat(MDC.get("contextId")).isNull();
    }

    @Test
    void multipleRequests_contextIsolation() {
        // First request
        LoggingUtils.setCorrelationId("request-1");
        LoggingUtils.setRequestContext("env-1", "Service1", "zone-1", "context-1");
        assertThat(LoggingUtils.getCorrelationId()).isEqualTo("request-1");
        assertThat(MDC.get("environmentId")).isEqualTo("env-1");
        
        // Clear context
        LoggingUtils.clearContext();
        
        // Second request
        LoggingUtils.setCorrelationId("request-2");
        LoggingUtils.setRequestContext("env-2", "Service2", "zone-2", "context-2");
        assertThat(LoggingUtils.getCorrelationId()).isEqualTo("request-2");
        assertThat(MDC.get("environmentId")).isEqualTo("env-2");
        
        // Verify no leakage from first request
        assertThat(MDC.get("environmentId")).isNotEqualTo("env-1");
    }

    @Test
    void setRequestContext_withoutCorrelationId_works() {
        LoggingUtils.setRequestContext("env-123", "StudentPersonal", "default", null);
        
        assertThat(MDC.get("environmentId")).isEqualTo("env-123");
        assertThat(MDC.get("serviceName")).isEqualTo("StudentPersonal");
        assertThat(MDC.get("zoneId")).isEqualTo("default");
        assertThat(LoggingUtils.getCorrelationId()).isNull();
    }

    @Test
    void setCorrelationId_withoutRequestContext_works() {
        LoggingUtils.setCorrelationId("test-id");
        
        assertThat(LoggingUtils.getCorrelationId()).isEqualTo("test-id");
        assertThat(MDC.get("environmentId")).isNull();
        assertThat(MDC.get("serviceName")).isNull();
        assertThat(MDC.get("zoneId")).isNull();
        assertThat(MDC.get("contextId")).isNull();
    }

    // ========== Utility Class Tests ==========

    @Test
    void testUtilityClass_cannotBeInstantiated() {
        assertThatThrownBy(() -> {
            // Use reflection to try to instantiate the utility class
            var constructor = LoggingUtils.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        })
        .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
        .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    // ========== Edge Case Tests ==========

    @Test
    void setRequestContext_withUuidStrings_setsValues() {
        String envId = UUID.randomUUID().toString();
        String contextId = UUID.randomUUID().toString();
        
        LoggingUtils.setRequestContext(envId, "StudentPersonal", "default", contextId);
        
        assertThat(MDC.get("environmentId")).isEqualTo(envId);
        assertThat(MDC.get("contextId")).isEqualTo(contextId);
    }

    @Test
    void setRequestContext_withVeryLongServiceName_setsValue() {
        String longServiceName = "StudentPersonalInformationManagementSystemService";
        
        LoggingUtils.setRequestContext("env-123", longServiceName, "default", null);
        
        assertThat(MDC.get("serviceName")).isEqualTo(longServiceName);
    }

    @Test
    void setRequestContext_withNumericStrings_setsValues() {
        LoggingUtils.setRequestContext("12345", "67890", "11111", "22222");
        
        assertThat(MDC.get("environmentId")).isEqualTo("12345");
        assertThat(MDC.get("serviceName")).isEqualTo("67890");
        assertThat(MDC.get("zoneId")).isEqualTo("11111");
        assertThat(MDC.get("contextId")).isEqualTo("22222");
    }

    @Test
    void setCorrelationId_thenSetRequestContext_bothPresent() {
        LoggingUtils.setCorrelationId("correlation-123");
        LoggingUtils.setRequestContext("env-123", "StudentPersonal", "default", "context-1");
        
        assertThat(LoggingUtils.getCorrelationId()).isEqualTo("correlation-123");
        assertThat(MDC.get("environmentId")).isEqualTo("env-123");
        assertThat(MDC.get("serviceName")).isEqualTo("StudentPersonal");
        assertThat(MDC.get("zoneId")).isEqualTo("default");
        assertThat(MDC.get("contextId")).isEqualTo("context-1");
    }

    @Test
    void setRequestContext_thenSetCorrelationId_bothPresent() {
        LoggingUtils.setRequestContext("env-123", "StudentPersonal", "default", "context-1");
        LoggingUtils.setCorrelationId("correlation-123");
        
        assertThat(LoggingUtils.getCorrelationId()).isEqualTo("correlation-123");
        assertThat(MDC.get("environmentId")).isEqualTo("env-123");
        assertThat(MDC.get("serviceName")).isEqualTo("StudentPersonal");
        assertThat(MDC.get("zoneId")).isEqualTo("default");
        assertThat(MDC.get("contextId")).isEqualTo("context-1");
    }
}
