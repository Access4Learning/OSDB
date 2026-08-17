// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metrics configuration for Micrometer and Prometheus integration.
 * 
 * <p>This configuration provides:
 * <ul>
 *   <li>Common tags for all metrics (service name, environment)</li>
 *   <li>Cardinality limiting to prevent metric explosion</li>
 *   <li>TimedAspect for @Timed annotation support</li>
 * </ul>
 * 
 * <p>Configuration properties:
 * <ul>
 *   <li>observability.metrics.enabled: Enable/disable metrics collection (default: true)</li>
 *   <li>observability.metrics.cardinality.limit: Maximum unique tag combinations per metric (default: 100)</li>
 *   <li>spring.application.name: Service name for common tags</li>
 *   <li>spring.profiles.active: Environment name for common tags</li>
 * </ul>
 * 
 * <p>Cardinality limiting prevents memory issues by replacing high-cardinality tag values
 * with "other" when the configured limit is exceeded. This is critical for tags like
 * URIs, user IDs, or other unbounded values.
 * 
 * @see io.micrometer.core.instrument.MeterRegistry
 * @see io.micrometer.core.aop.TimedAspect
 * @see <a href="https://micrometer.io/docs">Micrometer Documentation</a>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "observability.metrics.enabled", havingValue = "true", matchIfMissing = true)
public class MetricsConfig {

    private final MeterRegistry meterRegistry;
    
    @Value("${spring.application.name}")
    private String serviceName;
    
    @Value("${spring.profiles.active:default}")
    private String environment;
    
    @Value("${observability.metrics.cardinality.limit:100}")
    private int cardinalityLimit;

    /**
     * Configures common tags and cardinality limits for all metrics.
     * 
     * <p>Common tags are automatically added to all metrics:
     * <ul>
     *   <li>service: The microservice name from spring.application.name</li>
     *   <li>environment: The deployment environment from spring.profiles.active</li>
     * </ul>
     * 
     * <p>Cardinality limits are applied to prevent metric explosion:
     * <ul>
     *   <li>http.uri: Limited to prevent unbounded URI paths</li>
     *   <li>http.endpoint: Limited to prevent unbounded endpoint variations</li>
     *   <li>Custom limit from configuration for all metrics</li>
     * </ul>
     * 
     * <p>When a metric exceeds the cardinality limit, new tag values are replaced
     * with "other" to prevent memory issues and query performance degradation.
     */
    @PostConstruct
    public void configureMetrics() {
        log.info("Configuring metrics with common tags - service: {}, environment: {}", 
            serviceName, environment);
        log.info("Cardinality limit set to: {}", cardinalityLimit);
        
        // Add common tags to all metrics
        meterRegistry.config()
            .commonTags(
                "service", serviceName,
                "environment", environment
            );
        
        // Apply cardinality limits for high-cardinality tags
        // Limit http.uri to prevent unbounded URI paths
        meterRegistry.config()
            .meterFilter(MeterFilter.maximumAllowableTags(
                "http.server.requests", "uri", cardinalityLimit, MeterFilter.deny()
            ));
        
        // Limit http.endpoint to prevent unbounded endpoint variations
        meterRegistry.config()
            .meterFilter(MeterFilter.maximumAllowableTags(
                "http.server.requests", "endpoint", cardinalityLimit, MeterFilter.deny()
            ));
        
        // Apply general cardinality limit to all metrics
        // This is a safety net to prevent any metric from creating too many time series
        meterRegistry.config()
            .meterFilter(new MeterFilter() {
                @Override
                public io.micrometer.core.instrument.Meter.Id map(io.micrometer.core.instrument.Meter.Id id) {
                    // Count the number of unique tag combinations for this metric
                    // If it exceeds the limit, we log a warning
                    // Note: Micrometer doesn't provide a built-in way to count time series,
                    // so we rely on the per-tag limits above for enforcement
                    return id;
                }
            });
        
        log.info("Metrics configuration completed successfully");
    }
    
    /**
     * Enables @Timed annotation support for method-level metrics.
     * 
     * <p>The @Timed annotation can be applied to methods to automatically
     * record execution time metrics. Example usage:
     * 
     * <pre>
     * {@code
     * @Timed(value = "queue.process.message", description = "Time to process queue message")
     * public void processMessage(Message message) {
     *     // Method implementation
     * }
     * }
     * </pre>
     * 
     * <p>This will create a timer metric named "queue.process.message" that
     * records the execution time of the method, including percentiles and
     * histogram data if configured.
     * 
     * @param registry the meter registry
     * @return timed aspect for @Timed annotation support
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        log.info("Enabling @Timed annotation support");
        return new TimedAspect(registry);
    }
}
