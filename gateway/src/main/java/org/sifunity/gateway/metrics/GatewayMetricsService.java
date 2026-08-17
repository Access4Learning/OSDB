// SPDX-License-Identifier: MIT
package org.sifunity.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.sifunity.broker.common.constant.AccessType;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for recording Gateway metrics.
 * 
 * <p>This service provides methods to record various metrics for the Gateway,
 * including authentication, authorization, rate limiting, and routing metrics.</p>
 * 
 * <p>Metrics Recorded:</p>
 * <ul>
 *   <li>gateway.authentication.success - Counter for successful authentications</li>
 *   <li>gateway.authentication.failure - Counter for failed authentications</li>
 *   <li>gateway.authorization.success - Counter for successful authorizations</li>
 *   <li>gateway.authorization.failure - Counter for failed authorizations</li>
 *   <li>gateway.ratelimit.exceeded - Counter for rate limit violations</li>
 *   <li>gateway.requests - Counter for total requests per route</li>
 *   <li>gateway.request.duration - Timer for request duration per route</li>
 * </ul>
 * 
 * <p>Requirements: 10.3, 10.4, 10.5, 10.6</p>
 */
@Service
public class GatewayMetricsService {
    
    private final MeterRegistry meterRegistry;
    
    /**
     * Constructs a GatewayMetricsService with the specified meter registry.
     * 
     * @param meterRegistry the Micrometer meter registry
     */
    public GatewayMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Records a successful authentication.
     * 
     * @param environmentId the environment ID that was authenticated
     */
    public void recordAuthenticationSuccess(UUID environmentId) {
        Counter.builder("gateway.authentication.success")
                .description("Number of successful authentications")
                .tag("environment_id", environmentId.toString())
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * Records a failed authentication.
     * 
     * @param errorCode the error code for the authentication failure
     */
    public void recordAuthenticationFailure(String errorCode) {
        Counter.builder("gateway.authentication.failure")
                .description("Number of failed authentications")
                .tag("error_code", errorCode)
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * Records a successful authorization.
     * 
     * @param environmentId the environment ID that was authorized
     * @param serviceName the service name that was accessed
     * @param accessType the access type that was granted
     */
    public void recordAuthorizationSuccess(UUID environmentId, String serviceName, AccessType accessType) {
        Counter.builder("gateway.authorization.success")
                .description("Number of successful authorizations")
                .tag("environment_id", environmentId.toString())
                .tag("service_name", serviceName)
                .tag("access_type", accessType.name())
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * Records a failed authorization.
     * 
     * @param environmentId the environment ID that was denied
     * @param serviceName the service name that was accessed
     * @param accessType the access type that was denied
     */
    public void recordAuthorizationFailure(UUID environmentId, String serviceName, AccessType accessType) {
        Counter.builder("gateway.authorization.failure")
                .description("Number of failed authorizations")
                .tag("environment_id", environmentId.toString())
                .tag("service_name", serviceName != null ? serviceName : "unknown")
                .tag("access_type", accessType != null ? accessType.name() : "unknown")
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * Records a rate limit exceeded event.
     * 
     * @param environmentId the environment ID that exceeded the rate limit
     */
    public void recordRateLimitExceeded(UUID environmentId) {
        Counter.builder("gateway.ratelimit.exceeded")
                .description("Number of rate limit violations")
                .tag("environment_id", environmentId.toString())
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * Records a request to a specific route.
     * 
     * @param route the route path (e.g., "/requests", "/responses")
     * @param method the HTTP method
     * @param statusCode the HTTP status code
     */
    public void recordRequest(String route, String method, int statusCode) {
        Counter.builder("gateway.requests")
                .description("Number of requests per route")
                .tag("route", route)
                .tag("method", method)
                .tag("status", String.valueOf(statusCode))
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * Records the duration of a request.
     * 
     * @param route the route path (e.g., "/requests", "/responses")
     * @param method the HTTP method
     * @param durationMs the duration in milliseconds
     */
    public void recordRequestDuration(String route, String method, long durationMs) {
        Timer.builder("gateway.request.duration")
                .description("Request duration per route")
                .tag("route", route)
                .tag("method", method)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }
}
