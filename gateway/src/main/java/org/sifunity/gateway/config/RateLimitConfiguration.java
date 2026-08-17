// SPDX-License-Identifier: MIT
package org.sifunity.gateway.config;

import org.sifunity.broker.common.constant.EnvironmentType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for rate limiting per environment type.
 * 
 * <p>This configuration class loads rate limit settings from application.yml
 * and provides methods to retrieve the configured request rate limits for
 * different environment types (DIRECT, BROKERED).</p>
 * 
 * <p>Default rate limits:</p>
 * <ul>
 *   <li>DIRECT: 100 requests per second</li>
 *   <li>BROKERED: 100 requests per second</li>
 * </ul>
 * 
 * <p>Configuration example in application.yml:</p>
 * <pre>
 * rate-limit:
 *   requests-per-second:
 *     DIRECT: 100
 *     BROKERED: 100
 * </pre>
 * 
 * @see EnvironmentType
 */
@Configuration
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitConfiguration {
    
    /**
     * Map of environment types to their configured request rate limits (requests per second).
     * 
     * <p>Defaults are provided for all environment types if not configured.</p>
     */
    private Map<EnvironmentType, Integer> requestsPerSecond = new HashMap<>(Map.of(
        EnvironmentType.DIRECT, 100,
        EnvironmentType.BROKERED, 100
    ));
    
    /**
     * Gets the configured request rate limit for the specified environment type.
     * 
     * <p>If no rate limit is configured for the given environment type,
     * a default of 50 requests per second is returned.</p>
     * 
     * @param type the environment type
     * @return the configured requests per second for the environment type
     */
    public int getRequestsPerSecond(EnvironmentType type) {
        return requestsPerSecond.getOrDefault(type, 50);
    }
    
    /**
     * Sets the request rate limits per environment type.
     * 
     * <p>This method is used by Spring Boot's configuration properties binding
     * to load values from application.yml.</p>
     * 
     * @param requestsPerSecond map of environment types to rate limits
     */
    public void setRequestsPerSecond(Map<EnvironmentType, Integer> requestsPerSecond) {
        this.requestsPerSecond = requestsPerSecond;
    }
    
    /**
     * Gets the map of all configured rate limits.
     * 
     * @return map of environment types to rate limits
     */
    public Map<EnvironmentType, Integer> getRequestsPerSecond() {
        return requestsPerSecond;
    }
}
