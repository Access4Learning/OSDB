// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration that provides beans required by @WebMvcTest and @DataJpaTest contexts.
 * 
 * <p>This configuration provides:
 * <ul>
 *   <li>MeterRegistry - Required by MetricsConfig from sif-broker-common</li>
 *   <li>RestTemplateBuilder - Required by RestTemplateConfig</li>
 * </ul>
 */
@TestConfiguration
public class TestConfig {

    /**
     * Provides a simple MeterRegistry for test contexts.
     * 
     * @return SimpleMeterRegistry instance
     */
    @Bean
    @Primary
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    /**
     * Provides a RestTemplateBuilder for test contexts.
     * 
     * @return RestTemplateBuilder instance
     */
    @Bean
    @Primary
    public RestTemplateBuilder restTemplateBuilder() {
        return new RestTemplateBuilder();
    }
}
