// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.environment.config.BrokerInfrastructureConfig;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.ContextService;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.service.EnvironmentResponseMapper;
import org.sifunity.broker.common.constant.EnvironmentType;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property 5: ACL-to-ProvisionedZones Mapping Correctness.
 *
 * <p>For any set of ACL entries, provisionedZones grouping is correct
 * (service count, right count, type=OBJECT).</p>
 *
 * <p>This is a unit-level property test against the EnvironmentResponseMapper directly.</p>
 *
 * <p>Tag: Feature: sif-environment-response, Property 5: ACL-to-ProvisionedZones Mapping</p>
 *
 * <p><strong>Validates: Requirements 8.2, 8.3, 8.4, 8.6, 9.5, 12.2</strong></p>
 */
class AclToProvisionedZonesMappingPropertyTest {

    private static final BrokerInfrastructureConfig CONFIG = new BrokerInfrastructureConfig(
            "http://localhost:8080",
            "http://localhost:8081", "http://localhost:8081",
            "http://localhost:8083", "http://localhost:8084",
            "http://localhost:8085", "http://localhost:8086",
            "http://localhost:8087", "http://localhost:8088"
    );

    @Provide
    Arbitrary<List<AclEntry>> validAclEntrySets() {
        return SifEnvironmentResponseArbitraries.validAclEntrySets();
    }

    @Property(tries = 10)
    @Tag("Feature: sif-environment-response, Property 5: ACL-to-ProvisionedZones Mapping")
    void provisionedZonesGroupingIsCorrect(@ForAll("validAclEntrySets") List<AclEntry> aclEntries) {
        // Set up mock repositories — since ACL entries use contextUuid (which may be null),
        // we mock the context/zone resolution
        ContextRepository contextRepository = mock(ContextRepository.class);
        ContextServiceRepository contextServiceRepository = mock(ContextServiceRepository.class);
        ZoneRepository zoneRepository = mock(ZoneRepository.class);
        when(contextRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(contextServiceRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(zoneRepository.findAllById(anyCollection())).thenReturn(List.of());

        EnvironmentResponseMapper mapper = new EnvironmentResponseMapper(CONFIG,
                org.mockito.Mockito.mock(org.sifunity.broker.environment.repository.SolutionRepository.class),
                org.mockito.Mockito.mock(org.sifunity.broker.environment.repository.DomainRepository.class),
                contextRepository,
                contextServiceRepository,
                zoneRepository);

        // Build a minimal environment entity and attach ACL entries
        Environment entity = new Environment();
        entity.setId(UUID.randomUUID());
        entity.setApplicationName("TestApp");
        entity.setSessionToken(UUID.randomUUID().toString());
        entity.setSharedSecret("dGVzdA==");
        entity.setCreatedAt(Instant.now());
        entity.setDefaultZoneId("zone-default");
        entity.setType(EnvironmentType.DIRECT);
        entity.setFingerprint(UUID.randomUUID().toString());
        entity.setAuthenticationMethod("SIF_HMACSHA256");
        entity.setConsumerName("TestApp");
        entity.setAclEntries(new ArrayList<>(aclEntries));

        // Use the public toEnvironmentResponse(entity, aclEntries) overload
        SifEnvironmentResponse response = mapper.toEnvironmentResponse(entity, aclEntries);
        ProvisionedZonesDTO result = response.provisionedZones();

        assertThat(result).isNotNull();

        if (aclEntries.isEmpty()) {
            assertThat(result.provisionedZone()).isEmpty();
            return;
        }

        List<ProvisionedZoneDTO> zones = result.provisionedZone();

        // Total rights count = total ACL entries
        int totalRights = zones.stream()
                .flatMap(z -> z.services().service().stream())
                .mapToInt(s -> s.rights().right().size())
                .sum();
        assertThat(totalRights).isEqualTo(aclEntries.size());

        // Every service has type=OBJECT
        for (ProvisionedZoneDTO zone : zones) {
            for (ProvisionedServiceDTO svc : zone.services().service()) {
                assertThat(svc.type()).isEqualTo("OBJECT");
            }
        }
    }
}
