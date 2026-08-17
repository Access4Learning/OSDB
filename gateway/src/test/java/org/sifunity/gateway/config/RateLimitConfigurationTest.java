// SPDX-License-Identifier: MIT
package org.sifunity.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.EnvironmentType;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RateLimitConfiguration}.
 */
@DisplayName("RateLimitConfiguration")
class RateLimitConfigurationTest {
    
    private RateLimitConfiguration configuration;
    
    @BeforeEach
    void setUp() {
        configuration = new RateLimitConfiguration();
    }
    
    @Test
    @DisplayName("should have default rate limits for DIRECT and BROKERED environment types")
    void shouldHaveDefaultRateLimits() {
        int directLimit = configuration.getRequestsPerSecond(EnvironmentType.DIRECT);
        int brokeredLimit = configuration.getRequestsPerSecond(EnvironmentType.BROKERED);
        
        assertThat(directLimit).isEqualTo(100);
        assertThat(brokeredLimit).isEqualTo(100);
    }
    
    @Test
    @DisplayName("should return configured rate limit for DIRECT environment")
    void shouldReturnConfiguredRateLimitForDirect() {
        Map<EnvironmentType, Integer> customLimits = new HashMap<>();
        customLimits.put(EnvironmentType.DIRECT, 150);
        configuration.setRequestsPerSecond(customLimits);
        
        int limit = configuration.getRequestsPerSecond(EnvironmentType.DIRECT);
        
        assertThat(limit).isEqualTo(150);
    }
    
    @Test
    @DisplayName("should return configured rate limit for BROKERED environment")
    void shouldReturnConfiguredRateLimitForBrokered() {
        Map<EnvironmentType, Integer> customLimits = new HashMap<>();
        customLimits.put(EnvironmentType.BROKERED, 250);
        configuration.setRequestsPerSecond(customLimits);
        
        int limit = configuration.getRequestsPerSecond(EnvironmentType.BROKERED);
        
        assertThat(limit).isEqualTo(250);
    }
    
    @Test
    @DisplayName("should return default fallback value for unconfigured environment type")
    void shouldReturnDefaultFallbackForUnconfiguredType() {
        configuration.setRequestsPerSecond(new HashMap<>());
        
        int limit = configuration.getRequestsPerSecond(EnvironmentType.BROKERED);
        
        assertThat(limit).isEqualTo(50);
    }
    
    @Test
    @DisplayName("should allow updating rate limits via setter")
    void shouldAllowUpdatingRateLimits() {
        Map<EnvironmentType, Integer> newLimits = new HashMap<>();
        newLimits.put(EnvironmentType.DIRECT, 200);
        newLimits.put(EnvironmentType.BROKERED, 300);
        
        configuration.setRequestsPerSecond(newLimits);
        
        assertThat(configuration.getRequestsPerSecond(EnvironmentType.DIRECT)).isEqualTo(200);
        assertThat(configuration.getRequestsPerSecond(EnvironmentType.BROKERED)).isEqualTo(300);
    }
    
    @Test
    @DisplayName("should preserve default values when partially configured")
    void shouldPreserveDefaultsWhenPartiallyConfigured() {
        Map<EnvironmentType, Integer> partialLimits = new HashMap<>(configuration.getRequestsPerSecond());
        partialLimits.put(EnvironmentType.DIRECT, 150);
        configuration.setRequestsPerSecond(partialLimits);
        
        int directLimit = configuration.getRequestsPerSecond(EnvironmentType.DIRECT);
        int brokeredLimit = configuration.getRequestsPerSecond(EnvironmentType.BROKERED);
        
        assertThat(directLimit).isEqualTo(150);
        assertThat(brokeredLimit).isEqualTo(100);
    }
}
