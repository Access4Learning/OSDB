// SPDX-License-Identifier: MIT
package org.sifunity.gateway.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RateLimitResponse.
 */
class RateLimitResponseTest {
    
    @Test
    void allowed_shouldCreateAllowedResponse() {
        // Act
        RateLimitResponse response = RateLimitResponse.allowed();
        
        // Assert
        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getRetryAfterSeconds()).isEqualTo(0);
    }
    
    @Test
    void denied_shouldCreateDeniedResponse() {
        // Act
        RateLimitResponse response = RateLimitResponse.denied(5);
        
        // Assert
        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getRetryAfterSeconds()).isEqualTo(5);
    }
    
    @Test
    void denied_shouldAcceptZeroRetryAfter() {
        // Act
        RateLimitResponse response = RateLimitResponse.denied(0);
        
        // Assert
        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getRetryAfterSeconds()).isEqualTo(0);
    }
    
    @Test
    void denied_shouldAcceptLargeRetryAfter() {
        // Act
        RateLimitResponse response = RateLimitResponse.denied(3600);
        
        // Assert
        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getRetryAfterSeconds()).isEqualTo(3600);
    }
    
    @Test
    void constructor_shouldCreateResponseWithValues() {
        // Act
        RateLimitResponse response = new RateLimitResponse(true, 10);
        
        // Assert
        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getRetryAfterSeconds()).isEqualTo(10);
    }
}
