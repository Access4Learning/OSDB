// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BrokerInfrastructureConfig}.
 *
 * Covers: default values load from application-test.yml, all 8 URLs present.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("BrokerInfrastructureConfig Tests")
class BrokerInfrastructureConfigTest {

    @Autowired
    private BrokerInfrastructureConfig config;

    @Test
    @DisplayName("Config loads all 8 infrastructure service URLs from test profile")
    void configLoadsAllUrls() {
        assertThat(config.gatewayBaseUrl()).isNotBlank();
        assertThat(config.environment()).isNotBlank();
        assertThat(config.provisionRequests()).isNotBlank();
        assertThat(config.requestsConnector()).isNotBlank();
        assertThat(config.eventsConnector()).isNotBlank();
        assertThat(config.queues()).isNotBlank();
        assertThat(config.subscriptions()).isNotBlank();
        assertThat(config.servicesConnector()).isNotBlank();
        assertThat(config.responsesConnector()).isNotBlank();
    }

    @Test
    @DisplayName("Config values match application-test.yml defaults")
    void configValuesMatchDefaults() {
        assertThat(config.environment()).isEqualTo("http://localhost:8081");
        assertThat(config.provisionRequests()).isEqualTo("http://localhost:8081");
        assertThat(config.requestsConnector()).isEqualTo("http://localhost:8083");
        assertThat(config.eventsConnector()).isEqualTo("http://localhost:8084");
        assertThat(config.queues()).isEqualTo("http://localhost:8085");
        assertThat(config.subscriptions()).isEqualTo("http://localhost:8086");
        assertThat(config.servicesConnector()).isEqualTo("http://localhost:8087");
        assertThat(config.responsesConnector()).isEqualTo("http://localhost:8088");
    }

    @Test
    @DisplayName("Config is a valid record with all fields accessible")
    void configIsValidRecord() {
        BrokerInfrastructureConfig fresh = new BrokerInfrastructureConfig(
                "http://gw", "http://a", "http://b", "http://c", "http://d",
                "http://e", "http://f", "http://g", "http://h"
        );
        assertThat(fresh.gatewayBaseUrl()).isEqualTo("http://gw");
        assertThat(fresh.environment()).isEqualTo("http://a");
        assertThat(fresh.responsesConnector()).isEqualTo("http://h");
    }
}
