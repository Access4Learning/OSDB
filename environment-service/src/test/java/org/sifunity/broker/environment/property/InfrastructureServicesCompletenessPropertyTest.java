// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.environment.config.BrokerInfrastructureConfig;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.InfrastructureServiceDTO;
import org.sifunity.broker.environment.dto.SifEnvironmentResponse;
import org.sifunity.broker.environment.service.EnvironmentResponseMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 4: Infrastructure Services Completeness.
 *
 * <p>For any environment response, infrastructureServices contains exactly 8 entries
 * with correct names and config-derived URLs.</p>
 *
 * <p>Tag: Feature: sif-environment-response, Property 4: Infrastructure Services Completeness</p>
 *
 * <p><strong>Validates: Requirements 7.1, 7.2, 9.4, 12.3</strong></p>
 */
class InfrastructureServicesCompletenessPropertyTest {

    private static final Set<String> EXPECTED_SERVICE_NAMES = Set.of(
            "environment", "provisionRequests", "requestsConnector", "eventsConnector",
            "queues", "subscriptions", "servicesConnector", "responsesConnector"
    );

    @Provide
    Arbitrary<BrokerInfrastructureConfig> validConfigs() {
        Arbitrary<String> urls = Arbitraries.of(
                "http://localhost:8081",
                "http://broker.example.com:9090",
                "https://infra.school.edu/api"
        );
        return Combinators.combine(urls, urls, urls, urls, urls, urls, urls, urls)
                .as((u1, u2, u3, u4, u5, u6, u7, u8) ->
                        new BrokerInfrastructureConfig("http://localhost:8080", u1, u2, u3, u4, u5, u6, u7, u8));
    }

    @Provide
    Arbitrary<Environment> validEnvironmentEntities() {
        return SifEnvironmentResponseArbitraries.validEnvironmentEntities();
    }

    @Property(tries = 10)
    @Tag("Feature: sif-environment-response, Property 4: Infrastructure Services Completeness")
    void infrastructureServicesContainsExactlyEightEntriesWithCorrectNames(
            @ForAll("validConfigs") BrokerInfrastructureConfig config,
            @ForAll("validEnvironmentEntities") Environment entity) {

        EnvironmentResponseMapper mapper = new EnvironmentResponseMapper(config,
                org.mockito.Mockito.mock(org.sifunity.broker.environment.repository.SolutionRepository.class),
                org.mockito.Mockito.mock(org.sifunity.broker.environment.repository.DomainRepository.class),
                org.mockito.Mockito.mock(org.sifunity.broker.environment.repository.ContextRepository.class),
                org.mockito.Mockito.mock(org.sifunity.broker.environment.repository.ContextServiceRepository.class),
                org.mockito.Mockito.mock(org.sifunity.broker.environment.repository.ZoneRepository.class));
        SifEnvironmentResponse response = mapper.toEnvironmentResponse(entity);

        assertThat(response.infrastructureServices()).isNotNull();
        List<InfrastructureServiceDTO> services = response.infrastructureServices().infrastructureService();
        assertThat(services).hasSize(8);

        // All 8 expected names present
        Set<String> actualNames = services.stream()
                .map(InfrastructureServiceDTO::name)
                .collect(Collectors.toSet());
        assertThat(actualNames).isEqualTo(EXPECTED_SERVICE_NAMES);

        // Values match config
        Map<String, String> byName = services.stream()
                .collect(Collectors.toMap(InfrastructureServiceDTO::name, InfrastructureServiceDTO::value));
        assertThat(byName.get("environment")).isEqualTo(config.environment());
        assertThat(byName.get("provisionRequests")).isEqualTo(config.provisionRequests());
        assertThat(byName.get("requestsConnector")).isEqualTo(config.requestsConnector());
        assertThat(byName.get("eventsConnector")).isEqualTo(config.eventsConnector());
        assertThat(byName.get("queues")).isEqualTo(config.queues());
        assertThat(byName.get("subscriptions")).isEqualTo(config.subscriptions());
        assertThat(byName.get("servicesConnector")).isEqualTo(config.servicesConnector());
        assertThat(byName.get("responsesConnector")).isEqualTo(config.responsesConnector());
    }
}
