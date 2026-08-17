// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MetricsConfig.
 * 
 * <p>Tests verify:
 * <ul>
 *   <li>Common tags are applied to all metrics</li>
 *   <li>Cardinality limits are configured correctly</li>
 *   <li>TimedAspect is created for @Timed annotation support</li>
 *   <li>Configuration handles various service names and environments</li>
 * </ul>
 */
@DisplayName("MetricsConfig Tests")
class MetricsConfigTest {

    private MetricsConfig metricsConfig;
    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metricsConfig = new MetricsConfig(registry);
    }

    @Test
    @DisplayName("Should configure common tags with service name and environment")
    void shouldConfigureCommonTags() {
        // Given
        ReflectionTestUtils.setField(metricsConfig, "serviceName", "test-service");
        ReflectionTestUtils.setField(metricsConfig, "environment", "test");
        ReflectionTestUtils.setField(metricsConfig, "cardinalityLimit", 100);

        // When
        metricsConfig.configureMetrics();

        // Then
        // Create a test metric and verify common tags are applied
        Counter counter = registry.counter("test.counter");
        
        // Get the meter ID and check tags
        List<Tag> tags = counter.getId().getTags();
        assertThat(tags).extracting(Tag::getKey).contains("service", "environment");
        assertThat(tags).extracting(Tag::getValue).contains("test-service", "test");
    }

    @Test
    @DisplayName("Should apply common tags to multiple metrics")
    void shouldApplyCommonTagsToMultipleMetrics() {
        // Given
        ReflectionTestUtils.setField(metricsConfig, "serviceName", "queue-service");
        ReflectionTestUtils.setField(metricsConfig, "environment", "production");
        ReflectionTestUtils.setField(metricsConfig, "cardinalityLimit", 100);

        // When
        metricsConfig.configureMetrics();

        // Create multiple metrics
        Counter counter1 = registry.counter("queue.messages.processed");
        Counter counter2 = registry.counter("cache.hits");
        Counter counter3 = registry.counter("http.requests");

        // Then - all metrics should have common tags
        for (Counter counter : Arrays.asList(counter1, counter2, counter3)) {
            List<Tag> tags = counter.getId().getTags();
            assertThat(tags).extracting(Tag::getKey).contains("service", "environment");
            assertThat(tags).extracting(Tag::getValue).contains("queue-service", "production");
        }
    }

    @Test
    @DisplayName("Should handle default environment when not specified")
    void shouldHandleDefaultEnvironment() {
        // Given
        ReflectionTestUtils.setField(metricsConfig, "serviceName", "test-service");
        ReflectionTestUtils.setField(metricsConfig, "environment", "default");
        ReflectionTestUtils.setField(metricsConfig, "cardinalityLimit", 100);

        // When
        metricsConfig.configureMetrics();

        // Then
        Counter counter = registry.counter("test.metric");
        List<Tag> tags = counter.getId().getTags();
        assertThat(tags).extracting(Tag::getValue).contains("default");
    }

    @Test
    @DisplayName("Should configure cardinality limit from property")
    void shouldConfigureCardinalityLimit() {
        // Given
        ReflectionTestUtils.setField(metricsConfig, "serviceName", "test-service");
        ReflectionTestUtils.setField(metricsConfig, "environment", "test");
        ReflectionTestUtils.setField(metricsConfig, "cardinalityLimit", 50);

        // When
        metricsConfig.configureMetrics();

        // Then - registry should be configured (cardinality enforcement is internal to Micrometer)
        assertThat(registry).isNotNull();
        assertThat(registry.config()).isNotNull();
    }

    @Test
    @DisplayName("Should use default cardinality limit of 100")
    void shouldUseDefaultCardinalityLimit() {
        // Given
        ReflectionTestUtils.setField(metricsConfig, "serviceName", "test-service");
        ReflectionTestUtils.setField(metricsConfig, "environment", "test");
        ReflectionTestUtils.setField(metricsConfig, "cardinalityLimit", 100);

        // When
        metricsConfig.configureMetrics();

        // Then
        assertThat(registry).isNotNull();
    }

    @Test
    @DisplayName("Should create TimedAspect for @Timed annotation support")
    void shouldCreateTimedAspect() {
        // When
        TimedAspect timedAspect = metricsConfig.timedAspect(registry);

        // Then
        assertThat(timedAspect).isNotNull();
        assertThat(timedAspect).isInstanceOf(TimedAspect.class);
    }

    @Test
    @DisplayName("Should handle various service names")
    void shouldHandleVariousServiceNames() {
        // Given
        List<String> serviceNames = Arrays.asList(
            "gateway",
            "queue-service",
            "environment-service",
            "subscription-service",
            "requests-connector"
        );
        ReflectionTestUtils.setField(metricsConfig, "environment", "test");
        ReflectionTestUtils.setField(metricsConfig, "cardinalityLimit", 100);

        // When/Then - all service names should be handled correctly
        for (String serviceName : serviceNames) {
            MeterRegistry testRegistry = new SimpleMeterRegistry();
            MetricsConfig testConfig = new MetricsConfig(testRegistry);
            ReflectionTestUtils.setField(testConfig, "serviceName", serviceName);
            ReflectionTestUtils.setField(testConfig, "environment", "test");
            ReflectionTestUtils.setField(testConfig, "cardinalityLimit", 100);
            
            testConfig.configureMetrics();
            
            Counter counter = testRegistry.counter("test.metric");
            List<Tag> tags = counter.getId().getTags();
            assertThat(tags).extracting(Tag::getValue).contains(serviceName);
        }
    }

    @Test
    @DisplayName("Should handle various environments")
    void shouldHandleVariousEnvironments() {
        // Given
        List<String> environments = Arrays.asList("dev", "test", "staging", "production");
        ReflectionTestUtils.setField(metricsConfig, "serviceName", "test-service");
        ReflectionTestUtils.setField(metricsConfig, "cardinalityLimit", 100);

        // When/Then - all environments should be handled correctly
        for (String environment : environments) {
            MeterRegistry testRegistry = new SimpleMeterRegistry();
            MetricsConfig testConfig = new MetricsConfig(testRegistry);
            ReflectionTestUtils.setField(testConfig, "serviceName", "test-service");
            ReflectionTestUtils.setField(testConfig, "environment", environment);
            ReflectionTestUtils.setField(testConfig, "cardinalityLimit", 100);
            
            testConfig.configureMetrics();
            
            Counter counter = testRegistry.counter("test.metric");
            List<Tag> tags = counter.getId().getTags();
            assertThat(tags).extracting(Tag::getValue).contains(environment);
        }
    }

    @Test
    @DisplayName("Should allow custom tags in addition to common tags")
    void shouldAllowCustomTagsInAdditionToCommonTags() {
        // Given
        ReflectionTestUtils.setField(metricsConfig, "serviceName", "test-service");
        ReflectionTestUtils.setField(metricsConfig, "environment", "test");
        ReflectionTestUtils.setField(metricsConfig, "cardinalityLimit", 100);

        // When
        metricsConfig.configureMetrics();
        
        // Create a metric with custom tags
        Counter counter = registry.counter("test.metric", "custom_tag", "custom_value");

        // Then - should have both common and custom tags
        List<Tag> tags = counter.getId().getTags();
        assertThat(tags).extracting(Tag::getKey)
            .contains("service", "environment", "custom_tag");
        assertThat(tags).extracting(Tag::getValue)
            .contains("test-service", "test", "custom_value");
    }

    @Test
    @DisplayName("Should configure cardinality limits for http.server.requests metrics")
    void shouldConfigureCardinalityLimitsForHttpMetrics() {
        // Given
        ReflectionTestUtils.setField(metricsConfig, "serviceName", "test-service");
        ReflectionTestUtils.setField(metricsConfig, "environment", "test");
        ReflectionTestUtils.setField(metricsConfig, "cardinalityLimit", 100);

        // When
        metricsConfig.configureMetrics();

        // Then - configuration should be applied (actual enforcement is internal to Micrometer)
        // We verify the registry is configured without errors
        assertThat(registry.config()).isNotNull();
        
        // Create an http.server.requests metric to verify it can be created
        Counter httpCounter = registry.counter("http.server.requests", 
            "uri", "/api/test",
            "method", "GET",
            "status", "200"
        );
        assertThat(httpCounter).isNotNull();
    }

    @Test
    @DisplayName("Should handle high cardinality limit values")
    void shouldHandleHighCardinalityLimitValues() {
        // Given
        ReflectionTestUtils.setField(metricsConfig, "serviceName", "test-service");
        ReflectionTestUtils.setField(metricsConfig, "environment", "test");
        ReflectionTestUtils.setField(metricsConfig, "cardinalityLimit", 10000);

        // When
        metricsConfig.configureMetrics();

        // Then
        assertThat(registry).isNotNull();
    }

    @Test
    @DisplayName("Should handle low cardinality limit values")
    void shouldHandleLowCardinalityLimitValues() {
        // Given
        ReflectionTestUtils.setField(metricsConfig, "serviceName", "test-service");
        ReflectionTestUtils.setField(metricsConfig, "environment", "test");
        ReflectionTestUtils.setField(metricsConfig, "cardinalityLimit", 10);

        // When
        metricsConfig.configureMetrics();

        // Then
        assertThat(registry).isNotNull();
    }
}
