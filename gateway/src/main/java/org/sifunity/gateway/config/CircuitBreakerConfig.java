// SPDX-License-Identifier: MIT
package org.sifunity.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.broker.common.exception.ServiceUnavailableException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Circuit breaker configuration for the gateway service.
 *
 * <p>Configures a Resilience4j circuit breaker instance for the environment-service
 * token validation call. The circuit breaker protects the gateway from cascading
 * failures when environment-service is unavailable.</p>
 *
 * <p>Configuration:</p>
 * <ul>
 *   <li>Sliding window: COUNT_BASED, size 10</li>
 *   <li>Failure rate threshold: 50% (trips after 5/10 failures)</li>
 *   <li>Wait duration in open state: 30 seconds</li>
 *   <li>Permitted calls in half-open state: 3</li>
 *   <li>Recorded exceptions: ServiceUnavailableException, TimeoutException, ConnectException</li>
 *   <li>Ignored exceptions: InvalidTokenException (client errors should not trip circuit)</li>
 * </ul>
 *
 * @see CircuitBreaker
 */
@Configuration
public class CircuitBreakerConfig {

    /**
     * Creates and configures the circuit breaker instance for environment-service calls.
     *
     * @return configured CircuitBreaker bean named "environmentServiceCB"
     */
    @Bean
    public CircuitBreaker environmentServiceCircuitBreaker() {
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config =
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .slidingWindowType(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(10)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .recordExceptions(
                                ServiceUnavailableException.class,
                                TimeoutException.class,
                                ConnectException.class
                        )
                        .ignoreExceptions(
                                InvalidTokenException.class
                        )
                        .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        return registry.circuitBreaker("environmentServiceCB");
    }
}
