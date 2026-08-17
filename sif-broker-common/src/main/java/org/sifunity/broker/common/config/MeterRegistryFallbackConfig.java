// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a fallback {@link MeterRegistry} when Spring Boot Actuator's
 * auto-configured registry is not available (e.g., in {@code @DataJpaTest}
 * slices or when observability is disabled by test customizers).
 *
 * <p>This ensures metrics service beans ({@code ConnectorMetricsService},
 * {@code QueueMetricsService}) can always be created without failing on
 * missing {@code MeterRegistry} dependency.</p>
 */
@Configuration
public class MeterRegistryFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry fallbackMeterRegistry() {
        return new SimpleMeterRegistry();
    }
}
