// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * RestClient configuration for HTTP communication with external services.
 * Replaces WebClientConfig — uses JDK HttpClient (daemon threads only, no Netty).
 *
 * <p>Provides named RestClient beans for each downstream service with configurable
 * connection and read timeouts. A separate health-check RestClient uses shorter
 * timeouts suitable for liveness probes.</p>
 *
 * Requirements:
 * - 2.1: Named RestClient bean for Subscription Service
 * - 2.2: Named RestClient bean for Queue Service
 * - 2.3: Health-check RestClient with shorter timeout
 * - 2.4: JdkClientHttpRequestFactory as HTTP transport (no Netty)
 */
@Configuration
public class RestClientConfig {

    @Bean
    JdkClientHttpRequestFactory jdkRequestFactory(
            @Value("${http.client.connect-timeout:5000}") int connectTimeout,
            @Value("${http.client.read-timeout:10000}") int readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeout));
        return factory;
    }

    @Bean("subscriptionServiceRestClient")
    RestClient subscriptionServiceRestClient(
            JdkClientHttpRequestFactory requestFactory,
            @Value("${subscription-service.url}") String baseUrl) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }

    @Bean("queueServiceRestClient")
    RestClient queueServiceRestClient(
            JdkClientHttpRequestFactory requestFactory,
            @Value("${queue-service.url}") String baseUrl) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }

    @Bean("environmentServiceRestClient")
    RestClient environmentServiceRestClient(
            JdkClientHttpRequestFactory requestFactory,
            @Value("${environment-service.url}") String baseUrl) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }

    @Bean("healthCheckRestClient")
    RestClient healthCheckRestClient(
            @Value("${http.client.health-check-timeout:3000}") int timeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(timeout));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
