// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for {@link RestTemplate} used by the environment-service
 * to call other broker infrastructure services (subscription-service, queue-service).
 *
 * <p>Used by {@link org.sifunity.broker.environment.service.AlertEventPublisher}
 * for targeted alert event delivery.</p>
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}
