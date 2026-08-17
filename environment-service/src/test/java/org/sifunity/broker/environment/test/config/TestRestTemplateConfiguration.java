// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.test.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Test configuration that provides a {@link RestTemplateBuilder} bean.
 *
 * <p>Required because {@code QueueServiceClient} (a {@code @Component}) depends on
 * {@code RestTemplateBuilder}, which is not auto-configured in {@code @WebMvcTest} slices.
 * This configuration has no profile restriction so it activates in all test contexts.</p>
 */
@Configuration
public class TestRestTemplateConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RestTemplateBuilder restTemplateBuilder() {
        return new RestTemplateBuilder();
    }
}
