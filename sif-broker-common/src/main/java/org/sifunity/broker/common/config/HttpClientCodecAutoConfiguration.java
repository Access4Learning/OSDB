// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.codec.CodecCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared auto-configuration that raises the in-memory buffer limit used by
 * WebClient codecs across all SIF Unity Broker services.
 *
 * <p>Spring's default {@code maxInMemorySize} for decoding response bodies is
 * 256KB. Several broker flows legitimately exceed that — proxying large provider
 * result sets, reading large environments/ACLs, draining large queue messages —
 * and would otherwise fail to decode. This customizer applies a single,
 * configurable limit (default 5MB, see {@link HttpClientProperties}) to every
 * service's auto-configured {@code WebClient.Builder}.</p>
 *
 * <p>The {@link CodecCustomizer} bean is consumed by Spring Boot's
 * {@code WebClientAutoConfiguration} in any service that uses WebClient; services
 * without WebClient simply ignore it. This keeps the limit uniform and avoids
 * per-service configuration drift.</p>
 *
 * <p>This class deliberately depends only on spring-boot and spring-web types
 * ({@link CodecCustomizer}, {@code CodecConfigurer}) so that {@code sif-broker-common}
 * does not need a compile dependency on spring-webflux.</p>
 *
 * @see HttpClientProperties
 */
@Configuration
@EnableConfigurationProperties(HttpClientProperties.class)
public class HttpClientCodecAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HttpClientCodecAutoConfiguration.class);

    /**
     * Registers a {@link CodecCustomizer} that sets the maximum in-memory buffer
     * size for all default codecs from {@link HttpClientProperties#getMaxResponseSize()}.
     *
     * @param properties the shared HTTP client properties
     * @return a codec customizer applying the configured max in-memory size
     */
    @Bean
    public CodecCustomizer sifMaxInMemorySizeCodecCustomizer(HttpClientProperties properties) {
        int maxBytes = (int) properties.getMaxResponseSize().toBytes();
        log.info("Configuring WebClient codec maxInMemorySize = {} bytes ({})",
                maxBytes, properties.getMaxResponseSize());
        return configurer -> configurer.defaultCodecs().maxInMemorySize(maxBytes);
    }
}
