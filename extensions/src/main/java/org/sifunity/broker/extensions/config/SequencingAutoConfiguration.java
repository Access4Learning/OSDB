package org.sifunity.broker.extensions.config;

import java.time.Duration;

import org.sifunity.broker.extensions.sequencing.MessageSequenceFilter;
import org.sifunity.broker.extensions.sequencing.SequenceService;
import org.sifunity.broker.extensions.sequencing.ToggleService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration for the message sequencing extension.
 *
 * <p>Registers the servlet filter, sequence service, toggle service, and a
 * fallback {@link RestTemplate} when the JAR is on the classpath and the
 * property {@code sif.extensions.sequencing.enabled} is {@code true}
 * (or absent — enabled by default).
 */
@Configuration
@ConditionalOnProperty(
    name = "sif.extensions.sequencing.enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(SequencingProperties.class)
public class SequencingAutoConfiguration {

    @Bean
    public SequenceService sequenceService(StringRedisTemplate redisTemplate) {
        return new SequenceService(redisTemplate);
    }

    @Bean
    public ToggleService toggleService(SequencingProperties properties, RestTemplate restTemplate) {
        return new ToggleService(restTemplate, properties);
    }

    @Bean
    public FilterRegistrationBean<MessageSequenceFilter> messageSequenceFilter(
            SequenceService sequenceService, ToggleService toggleService) {
        FilterRegistrationBean<MessageSequenceFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new MessageSequenceFilter(sequenceService, toggleService));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("messageSequenceFilter");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate extensionsRestTemplate() {
        return new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(5))
            .build();
    }
}
