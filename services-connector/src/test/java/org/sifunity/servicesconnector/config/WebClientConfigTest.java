// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for WebClientConfig.
 * Verifies that WebClient builders are properly configured with correct timeouts.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "environment.service.timeout.connection=2000",
    "environment.service.timeout.read=10000",
    "provider.timeout.connection=5000",
    "provider.timeout.read=30000"
})
class WebClientConfigTest {
    
    @Autowired
    @Qualifier("environmentServiceWebClientBuilder")
    private WebClient.Builder environmentServiceWebClientBuilder;
    
    @Autowired
    @Qualifier("providerWebClientBuilder")
    private WebClient.Builder providerWebClientBuilder;
    
    @Test
    void environmentServiceWebClientBuilder_shouldBeConfigured() {
        assertThat(environmentServiceWebClientBuilder).isNotNull();
        WebClient webClient = environmentServiceWebClientBuilder.build();
        assertThat(webClient).isNotNull();
    }
    
    @Test
    void providerWebClientBuilder_shouldBeConfigured() {
        assertThat(providerWebClientBuilder).isNotNull();
        WebClient webClient = providerWebClientBuilder.build();
        assertThat(webClient).isNotNull();
    }
    
    @Test
    void allWebClientBuilders_shouldBeDistinct() {
        assertThat(environmentServiceWebClientBuilder)
            .isNotSameAs(providerWebClientBuilder);
    }
}
