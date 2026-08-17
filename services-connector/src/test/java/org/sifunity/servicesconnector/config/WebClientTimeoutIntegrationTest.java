// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for WebClient timeout configuration.
 * Verifies that timeouts are properly applied and enforced for provider endpoints.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "provider.timeout.connection=1000",
    "provider.timeout.read=3000"
})
class WebClientTimeoutIntegrationTest {
    
    @Autowired
    @Qualifier("providerWebClientBuilder")
    private WebClient.Builder providerWebClientBuilder;
    
    private WireMockServer wireMockServer;
    
    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
    }
    
    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }
    
    @Test
    void providerWebClient_shouldTimeoutOnSlowResponse() {
        wireMockServer.stubFor(post(urlEqualTo("/services"))
            .willReturn(aResponse()
                .withStatus(201)
                .withBody("{\"id\":\"123\"}")
                .withFixedDelay(4000)));
        
        WebClient webClient = providerWebClientBuilder
            .baseUrl(wireMockServer.baseUrl())
            .build();
        
        assertThatThrownBy(() -> 
            webClient.post()
                .uri("/services")
                .bodyValue("{\"test\":\"data\"}")
                .retrieve()
                .bodyToMono(String.class)
                .block()
        ).isInstanceOf(WebClientRequestException.class)
         .hasCauseInstanceOf(io.netty.handler.timeout.ReadTimeoutException.class);
    }
    
    @Test
    void providerWebClient_shouldSucceedOnFastResponse() {
        wireMockServer.stubFor(post(urlEqualTo("/services"))
            .willReturn(aResponse()
                .withStatus(201)
                .withBody("{\"id\":\"123\"}")
                .withFixedDelay(100)));
        
        WebClient webClient = providerWebClientBuilder
            .baseUrl(wireMockServer.baseUrl())
            .build();
        
        String response = webClient.post()
            .uri("/services")
            .bodyValue("{\"test\":\"data\"}")
            .retrieve()
            .bodyToMono(String.class)
            .block();
        
        assertThat(response).isEqualTo("{\"id\":\"123\"}");
    }
}
