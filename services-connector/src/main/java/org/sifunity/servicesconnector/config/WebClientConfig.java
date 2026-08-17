// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.sifunity.broker.common.config.HttpClientProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient configuration for HTTP communication with downstream services.
 * Configures connection and read timeouts for Environment Service and Provider endpoints.
 *
 * <p>These builders are created explicitly (not from the auto-configured
 * {@code WebClient.Builder}), so the shared codec customizer in
 * {@code sif-broker-common} does not reach them automatically. The maximum
 * in-memory buffer size from {@link HttpClientProperties} is therefore applied
 * explicitly here so large provider/service responses behave consistently with
 * the rest of the broker (default 5MB instead of Spring's 256KB).</p>
 */
@Configuration
public class WebClientConfig {
    
    @Value("${environment.service.timeout.connection:2000}")
    private int environmentServiceConnectionTimeout;
    
    @Value("${environment.service.timeout.read:10000}")
    private int environmentServiceReadTimeout;
    
    @Value("${provider.timeout.connection:5000}")
    private int providerConnectionTimeout;
    
    @Value("${provider.timeout.read:30000}")
    private int providerReadTimeout;

    private final int maxInMemoryBytes;

    public WebClientConfig(HttpClientProperties httpClientProperties) {
        this.maxInMemoryBytes = (int) httpClientProperties.getMaxResponseSize().toBytes();
    }
    
    /**
     * WebClient builder for Environment Service communication.
     * Uses shorter timeouts (2s connection, 10s read) similar to Job Service.
     * 
     * @return WebClient.Builder configured for Environment Service
     */
    @Bean(name = "environmentServiceWebClientBuilder")
    public WebClient.Builder environmentServiceWebClientBuilder() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, environmentServiceConnectionTimeout)
            .responseTimeout(Duration.ofMillis(environmentServiceReadTimeout))
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(environmentServiceReadTimeout, TimeUnit.MILLISECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(environmentServiceReadTimeout, TimeUnit.MILLISECONDS)));
        
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemoryBytes));
    }
    
    /**
     * WebClient builder for Provider endpoint communication.
     * Uses longer timeouts (5s connection, 30s read) as specified in Requirements 14.1, 14.2, 14.4.
     * 
     * @return WebClient.Builder configured for Provider endpoints
     */
    @Bean(name = "providerWebClientBuilder")
    public WebClient.Builder providerWebClientBuilder() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, providerConnectionTimeout)
            .responseTimeout(Duration.ofMillis(providerReadTimeout))
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(providerReadTimeout, TimeUnit.MILLISECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(providerReadTimeout, TimeUnit.MILLISECONDS)));
        
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemoryBytes));
    }
}
