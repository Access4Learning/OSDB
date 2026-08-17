// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PermissionCheckResponse.
 * 
 * Tests the PermissionCheckResponse record structure and behavior.
 */
@DisplayName("PermissionCheckResponse Tests")
class PermissionCheckResponseTest {

    @Test
    @DisplayName("Should create PermissionCheckResponse with granted true")
    void shouldCreatePermissionCheckResponseWithGrantedTrue() {
        // Given/When
        PermissionCheckResponse response = new PermissionCheckResponse(true);
        
        // Then
        assertThat(response.granted()).isTrue();
    }

    @Test
    @DisplayName("Should create PermissionCheckResponse with granted false")
    void shouldCreatePermissionCheckResponseWithGrantedFalse() {
        // Given/When
        PermissionCheckResponse response = new PermissionCheckResponse(false);
        
        // Then
        assertThat(response.granted()).isFalse();
    }

    @Test
    @DisplayName("Should support equality comparison")
    void shouldSupportEqualityComparison() {
        // Given
        PermissionCheckResponse response1 = new PermissionCheckResponse(true);
        PermissionCheckResponse response2 = new PermissionCheckResponse(true);
        PermissionCheckResponse response3 = new PermissionCheckResponse(false);
        
        // Then
        assertThat(response1).isEqualTo(response2);
        assertThat(response1).isNotEqualTo(response3);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToStringRepresentation() {
        // Given
        PermissionCheckResponse responseTrue = new PermissionCheckResponse(true);
        PermissionCheckResponse responseFalse = new PermissionCheckResponse(false);
        
        // When
        String toStringTrue = responseTrue.toString();
        String toStringFalse = responseFalse.toString();
        
        // Then
        assertThat(toStringTrue).contains("PermissionCheckResponse");
        assertThat(toStringTrue).contains("true");
        assertThat(toStringFalse).contains("PermissionCheckResponse");
        assertThat(toStringFalse).contains("false");
    }

    @Test
    @DisplayName("Should create convenience factory methods")
    void shouldCreateConvenienceFactoryMethods() {
        // This test demonstrates how the response could be used in practice
        
        // Given - simulating permission granted scenario
        boolean hasApprovedAcl = true;
        PermissionCheckResponse grantedResponse = new PermissionCheckResponse(hasApprovedAcl);
        
        // Given - simulating permission denied scenario
        boolean hasNoApprovedAcl = false;
        PermissionCheckResponse deniedResponse = new PermissionCheckResponse(hasNoApprovedAcl);
        
        // Then
        assertThat(grantedResponse.granted()).isTrue();
        assertThat(deniedResponse.granted()).isFalse();
    }
}