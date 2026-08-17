// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TokenValidationRequest.
 * 
 * Tests the TokenValidationRequest record structure and behavior.
 */
@DisplayName("TokenValidationRequest Tests")
class TokenValidationRequestTest {

    @Test
    @DisplayName("Should create TokenValidationRequest with all fields")
    void shouldCreateTokenValidationRequestWithAllFields() {
        // Given
        String sessionToken = "550e8400-e29b-41d4-a716-446655440000";
        String timestamp = "2023-12-01T10:30:00Z";
        String hash = "dGVzdC1oYXNo";
        
        // When
        TokenValidationRequest request = new TokenValidationRequest(sessionToken, timestamp, hash);
        
        // Then
        assertThat(request.sessionToken()).isEqualTo(sessionToken);
        assertThat(request.timestamp()).isEqualTo(timestamp);
        assertThat(request.hash()).isEqualTo(hash);
    }

    @Test
    @DisplayName("Should support equality comparison")
    void shouldSupportEqualityComparison() {
        // Given
        String sessionToken = "550e8400-e29b-41d4-a716-446655440000";
        String timestamp = "2023-12-01T10:30:00Z";
        String hash = "dGVzdC1oYXNo";
        
        TokenValidationRequest request1 = new TokenValidationRequest(sessionToken, timestamp, hash);
        TokenValidationRequest request2 = new TokenValidationRequest(sessionToken, timestamp, hash);
        TokenValidationRequest request3 = new TokenValidationRequest("different-token", timestamp, hash);
        
        // Then
        assertThat(request1).isEqualTo(request2);
        assertThat(request1).isNotEqualTo(request3);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToStringRepresentation() {
        // Given
        String sessionToken = "550e8400-e29b-41d4-a716-446655440000";
        String timestamp = "2023-12-01T10:30:00Z";
        String hash = "dGVzdC1oYXNo";
        
        TokenValidationRequest request = new TokenValidationRequest(sessionToken, timestamp, hash);
        
        // When
        String toString = request.toString();
        
        // Then
        assertThat(toString).contains("TokenValidationRequest");
        assertThat(toString).contains(sessionToken);
        assertThat(toString).contains(timestamp);
        assertThat(toString).contains(hash);
    }

    @Test
    @DisplayName("Should handle null values")
    void shouldHandleNullValues() {
        // Given/When
        TokenValidationRequest request = new TokenValidationRequest(null, null, null);
        
        // Then
        assertThat(request.sessionToken()).isNull();
        assertThat(request.timestamp()).isNull();
        assertThat(request.hash()).isNull();
    }
}