// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.AccessType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PermissionCheckRequest.
 * 
 * Tests the PermissionCheckRequest record structure and behavior.
 */
@DisplayName("PermissionCheckRequest Tests")
class PermissionCheckRequestTest {

    @Test
    @DisplayName("Should create PermissionCheckRequest with all fields")
    void shouldCreatePermissionCheckRequestWithAllFields() {
        // Given
        UUID environmentId = UUID.randomUUID();
        String serviceName = "test-service";
        String zoneId = "test-zone";
        String contextId = "test-context";
        AccessType accessType = AccessType.QUERY;
        
        // When
        PermissionCheckRequest request = new PermissionCheckRequest(
            environmentId, serviceName, zoneId, contextId, accessType
        );
        
        // Then
        assertThat(request.environmentId()).isEqualTo(environmentId);
        assertThat(request.serviceName()).isEqualTo(serviceName);
        assertThat(request.zoneId()).isEqualTo(zoneId);
        assertThat(request.contextId()).isEqualTo(contextId);
        assertThat(request.accessType()).isEqualTo(accessType);
    }

    @Test
    @DisplayName("Should create PermissionCheckRequest with null contextId")
    void shouldCreatePermissionCheckRequestWithNullContextId() {
        // Given
        UUID environmentId = UUID.randomUUID();
        String serviceName = "test-service";
        String zoneId = "test-zone";
        String contextId = null;
        AccessType accessType = AccessType.PROVIDE;
        
        // When
        PermissionCheckRequest request = new PermissionCheckRequest(
            environmentId, serviceName, zoneId, contextId, accessType
        );
        
        // Then
        assertThat(request.environmentId()).isEqualTo(environmentId);
        assertThat(request.serviceName()).isEqualTo(serviceName);
        assertThat(request.zoneId()).isEqualTo(zoneId);
        assertThat(request.contextId()).isNull();
        assertThat(request.accessType()).isEqualTo(accessType);
    }

    @Test
    @DisplayName("Should support all access types")
    void shouldSupportAllAccessTypes() {
        // Given
        UUID environmentId = UUID.randomUUID();
        String serviceName = "test-service";
        String zoneId = "test-zone";
        String contextId = "test-context";
        
        // When/Then
        for (AccessType accessType : AccessType.values()) {
            PermissionCheckRequest request = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, contextId, accessType
            );
            assertThat(request.accessType()).isEqualTo(accessType);
        }
    }

    @Test
    @DisplayName("Should support equality comparison")
    void shouldSupportEqualityComparison() {
        // Given
        UUID environmentId = UUID.randomUUID();
        String serviceName = "test-service";
        String zoneId = "test-zone";
        String contextId = "test-context";
        AccessType accessType = AccessType.SUBSCRIBE;
        
        PermissionCheckRequest request1 = new PermissionCheckRequest(
            environmentId, serviceName, zoneId, contextId, accessType
        );
        PermissionCheckRequest request2 = new PermissionCheckRequest(
            environmentId, serviceName, zoneId, contextId, accessType
        );
        PermissionCheckRequest request3 = new PermissionCheckRequest(
            UUID.randomUUID(), serviceName, zoneId, contextId, accessType
        );
        
        // Then
        assertThat(request1).isEqualTo(request2);
        assertThat(request1).isNotEqualTo(request3);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToStringRepresentation() {
        // Given
        UUID environmentId = UUID.randomUUID();
        String serviceName = "test-service";
        String zoneId = "test-zone";
        String contextId = "test-context";
        AccessType accessType = AccessType.QUERY;
        
        PermissionCheckRequest request = new PermissionCheckRequest(
            environmentId, serviceName, zoneId, contextId, accessType
        );
        
        // When
        String toString = request.toString();
        
        // Then
        assertThat(toString).contains("PermissionCheckRequest");
        assertThat(toString).contains(environmentId.toString());
        assertThat(toString).contains(serviceName);
        assertThat(toString).contains(zoneId);
        assertThat(toString).contains(contextId);
        assertThat(toString).contains(accessType.toString());
    }

    @Test
    @DisplayName("Should handle null values")
    void shouldHandleNullValues() {
        // Given/When
        PermissionCheckRequest request = new PermissionCheckRequest(null, null, null, null, null);
        
        // Then
        assertThat(request.environmentId()).isNull();
        assertThat(request.serviceName()).isNull();
        assertThat(request.zoneId()).isNull();
        assertThat(request.contextId()).isNull();
        assertThat(request.accessType()).isNull();
    }
}