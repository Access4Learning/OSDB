// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * RestClient configuration for HTTP communication with Queue Service.
 * Replaces WebClientConfig — uses JDK HttpClient (daemon threads only, no Netty).
 *
 * <p>Provides named RestClient beans with configurable connection and read timeouts.
 * A separate health-check RestClient uses shorter timeouts suitable for liveness probes.</p>
 *
 * Requirements:
 * - 9.6: HTTP client for calls to Queue Service
 * - 11.1: Configurable connection timeout for Queue Service calls
 * - 11.2: Configurable read timeout for Queue Service calls
 */
@Configuration
public class RestClientConfig {

    @Bean
    JdkClientHttpRequestFactory jdkRequestFactory(
            @Value("${queue.service.connection-timeout:5000}") int connectTimeout,
            @Value("${queue.service.read-timeout:10000}") int readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeout));
        return factory;
    }

    @Bean("queueServiceRestClient")
    RestClient queueServiceRestClient(
            JdkClientHttpRequestFactory requestFactory,
            @Value("${queue.service.url}") String baseUrl) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }

    @Bean("environmentServiceRestClient")
    RestClient environmentServiceRestClient(
            JdkClientHttpRequestFactory requestFactory,
            @Value("${environment.service.url}") String baseUrl) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }

    @Bean("healthCheckRestClient")
    RestClient healthCheckRestClient(
            @Value("${queue.service.health-check-timeout:3000}") int timeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(timeout));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
