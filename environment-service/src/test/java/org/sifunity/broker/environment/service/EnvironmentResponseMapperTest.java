// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.config.BrokerInfrastructureConfig;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.ContextService;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EnvironmentResponseMapper}.
 *
 * Covers: entity with full applicationInfo, entity with null applicationInfo,
 * empty ACL list, multi-zone ACL grouping, infrastructure services from config.
 */
@DisplayName("EnvironmentResponseMapper Tests")
class EnvironmentResponseMapperTest {

    private EnvironmentResponseMapper mapper;
    private BrokerInfrastructureConfig config;
    private ContextRepository contextRepository;
    private org.sifunity.broker.environment.repository.ContextServiceRepository contextServiceRepository;
    private ZoneRepository zoneRepository;

    @BeforeEach
    void setUp() {
        config = new BrokerInfrastructureConfig(
                "http://localhost:8080",
                "http://localhost:8081",
                "http://localhost:8081",
                "http://localhost:8083",
                "http://localhost:8084",
                "http://localhost:8085",
                "http://localhost:8086",
                "http://localhost:8087",
                "http://localhost:8088"
        );
        contextRepository = mock(ContextRepository.class);
        contextServiceRepository = mock(org.sifunity.broker.environment.repository.ContextServiceRepository.class);
        zoneRepository = mock(ZoneRepository.class);
        mapper = new EnvironmentResponseMapper(config,
                mock(SolutionRepository.class),
                mock(org.sifunity.broker.environment.repository.DomainRepository.class),
                contextRepository,
                contextServiceRepository, zoneRepository);
    }

    @Test
    @DisplayName("toEnvironmentResponse maps entity with full applicationInfo")
    void toEnvironmentResponse_fullApplicationInfo() {
        Environment entity = buildBaseEntity();
        entity.setAppinfoApplicationKey("app-key-1");
        entity.setAppinfoSupportedInfraVersion("3.7");
        entity.setAppinfoDataModelNamespace("http://sifassociation.org/au/datamodel/3.4");
        entity.setAppinfoTransport("REST");
        entity.setAppinfoAppVendorName("Acme");
        entity.setAppinfoAppProductName("StudentSIS");
        entity.setAppinfoAppProductVersion("2.1");
        entity.setAppinfoAppIconUri("http://acme.com/icon.png");
        entity.setAppinfoAdapterVendorName("AdapterCo");
        entity.setAppinfoAdapterProductName("SIFAdapter");
        entity.setAppinfoAdapterProductVersion("1.0");
        entity.setAppinfoAdapterIconUri("http://adapterco.com/icon.png");
        entity.setAclEntries(new ArrayList<>());

        SifEnvironmentResponse response = mapper.toEnvironmentResponse(entity);

        assertThat(response.id()).isEqualTo(entity.getId());
        assertThat(response.type()).isEqualTo(EnvironmentType.BROKERED);
        assertThat(response.consumerName()).isEqualTo("TestConsumer");
        assertThat(response.fingerprint()).isEqualTo("fp-123");
        assertThat(DefaultZoneDTO.idOf(response.defaultZone())).isEqualTo("zone-1");

        ApplicationInfoDTO appInfo = response.applicationInfo();
        assertThat(appInfo).isNotNull();
        assertThat(appInfo.applicationKey()).isEqualTo("app-key-1");
        assertThat(appInfo.supportedInfrastructureVersion()).isEqualTo("3.7");
        assertThat(appInfo.transport()).isEqualTo("REST");
        assertThat(appInfo.applicationProduct()).isNotNull();
        assertThat(appInfo.applicationProduct().vendorName()).isEqualTo("Acme");
        assertThat(appInfo.adapterProduct()).isNotNull();
        assertThat(appInfo.adapterProduct().vendorName()).isEqualTo("AdapterCo");
    }

    @Test
    @DisplayName("toEnvironmentResponse maps entity with null applicationInfo")
    void toEnvironmentResponse_nullApplicationInfo() {
        Environment entity = buildBaseEntity();
        entity.setAclEntries(new ArrayList<>());

        SifEnvironmentResponse response = mapper.toEnvironmentResponse(entity);

        assertThat(response.applicationInfo()).isNull();
        assertThat(response.id()).isEqualTo(entity.getId());
    }

    @Test
    @DisplayName("toEnvironmentResponse with empty ACL list produces empty provisionedZones")
    void toEnvironmentResponse_emptyAclList() {
        Environment entity = buildBaseEntity();
        entity.setAclEntries(new ArrayList<>());

        SifEnvironmentResponse response = mapper.toEnvironmentResponse(entity);

        assertThat(response.provisionedZones()).isNotNull();
        assertThat(response.provisionedZones().provisionedZone()).isEmpty();
    }

    @Test
    @DisplayName("toEnvironmentResponse groups multi-zone ACL entries correctly")
    void toEnvironmentResponse_multiZoneAclGrouping() {
        // Set up zone and context entities for resolution
        UUID zone1Uuid = UUID.randomUUID();
        UUID zone2Uuid = UUID.randomUUID();
        UUID ctx1Uuid = UUID.randomUUID();

        Zone zone1 = new Zone();
        zone1.setId(zone1Uuid);
        zone1.setZoneId("Z1");
        zone1.setCreatedAt(Instant.now());

        Zone zone2 = new Zone();
        zone2.setId(zone2Uuid);
        zone2.setZoneId("Z2");
        zone2.setCreatedAt(Instant.now());

        Context ctx1 = new Context();
        ctx1.setId(ctx1Uuid);
        ctx1.setZoneUuid(zone1Uuid);
        ctx1.setContextId("ctx1");
        ctx1.setCreatedAt(Instant.now());

        // Create ContextService entities that link to contexts
        UUID csSvcACtx1Uuid = UUID.randomUUID();
        ContextService csSvcACtx1 = new ContextService();
        csSvcACtx1.setId(csSvcACtx1Uuid);
        csSvcACtx1.setContextUuid(ctx1Uuid);
        csSvcACtx1.setServiceName("SvcA");

        UUID csSvcBNullUuid = UUID.randomUUID();
        ContextService csSvcBNull = new ContextService();
        csSvcBNull.setId(csSvcBNullUuid);
        csSvcBNull.setContextUuid(null); // wildcard — no context
        csSvcBNull.setServiceName("SvcB");

        UUID csSvcANullUuid = UUID.randomUUID();
        ContextService csSvcANull = new ContextService();
        csSvcANull.setId(csSvcANullUuid);
        csSvcANull.setContextUuid(null); // wildcard — no context
        csSvcANull.setServiceName("SvcA");

        when(contextServiceRepository.findAllById(anyCollection()))
                .thenReturn(List.of(csSvcACtx1, csSvcBNull, csSvcANull));
        when(contextRepository.findAllById(anyCollection())).thenReturn(List.of(ctx1));
        when(zoneRepository.findAllById(anyCollection())).thenReturn(List.of(zone1, zone2));

        Environment entity = buildBaseEntity();
        List<AclEntry> acls = new ArrayList<>();
        // Z1/ctx1 entries (contextServiceUuid points to csSvcACtx1 which resolves to ctx1 → zone1)
        acls.add(buildAclWithContextService(entity, csSvcACtx1Uuid, AccessType.QUERY, AccessLevel.APPROVED));
        acls.add(buildAclWithContextService(entity, csSvcACtx1Uuid, AccessType.CREATE, AccessLevel.REJECTED));
        // Wildcard entries (contextServiceUuid points to csSvcBNull which has null contextUuid)
        acls.add(buildAclWithContextService(entity, csSvcBNullUuid, AccessType.QUERY, AccessLevel.APPROVED));
        // Another wildcard entry
        acls.add(buildAclWithContextService(entity, csSvcANullUuid, AccessType.QUERY, AccessLevel.SUPPORTED));
        entity.setAclEntries(acls);

        SifEnvironmentResponse response = mapper.toEnvironmentResponse(entity);

        ProvisionedZonesDTO zones = response.provisionedZones();
        // ctx1 resolves to Z1; null contextUuid entries group together under null zone
        assertThat(zones.provisionedZone()).hasSize(2);

        ProvisionedZoneDTO z1 = zones.provisionedZone().stream()
                .filter(z -> "Z1".equals(z.id())).findFirst().orElseThrow();
        // Z1 has SvcA with ctx1 (2 rights)
        assertThat(z1.services().service()).hasSize(1);

        ProvisionedServiceDTO svcA = z1.services().service().stream()
                .filter(s -> "SvcA".equals(s.name())).findFirst().orElseThrow();
        assertThat(svcA.type()).isEqualTo("OBJECT");
        assertThat(svcA.rights().right()).hasSize(2);

        // Wildcard entries (null contextUuid) group under null zone
        ProvisionedZoneDTO wildcardZone = zones.provisionedZone().stream()
                .filter(z -> z.id() == null).findFirst().orElseThrow();
        assertThat(wildcardZone.services().service()).hasSize(2);
    }

    @Test
    @DisplayName("buildInfrastructureServices returns 8 entries with gateway-routed URLs")
    void buildInfrastructureServices_returns8Entries() {
        InfrastructureServicesDTO services = mapper.buildInfrastructureServices(
                "test-domain", "test-solution", java.util.UUID.randomUUID());

        assertThat(services.infrastructureService()).hasSize(8);
        assertThat(services.infrastructureService())
                .extracting(InfrastructureServiceDTO::name)
                .containsExactlyInAnyOrder(
                        "environment", "provisionRequests", "requestsConnector",
                        "eventsConnector", "queues", "subscriptions",
                        "servicesConnector", "responsesConnector");
        assertThat(services.infrastructureService())
                .filteredOn(s -> "environment".equals(s.name()))
                .first()
                .extracting(InfrastructureServiceDTO::value)
                .asString()
                .startsWith("http://localhost:8080/test-domain/test-solution/environments/");
    }

    @Test
    @DisplayName("toRegistrationResponse wraps environment with sharedSecret")
    void toRegistrationResponse_wrapsCorrectly() {
        Environment entity = buildBaseEntity();
        entity.setAclEntries(new ArrayList<>());

        SifEnvironmentRegistrationResponse regResponse = mapper.toRegistrationResponse(entity, "secret-123");

        assertThat(regResponse.sharedSecret()).isEqualTo("secret-123");
        assertThat(regResponse.environment().id()).isEqualTo(entity.getId());
        assertThat(regResponse.environment().consumerName()).isEqualTo("TestConsumer");
    }

    // ========== Helpers ==========

    private Environment buildBaseEntity() {
        Environment e = new Environment();
        e.setId(UUID.randomUUID());
        e.setType(EnvironmentType.BROKERED);
        e.setApplicationName("TestApp");
        e.setConsumerName("TestConsumer");
        e.setSessionToken(UUID.randomUUID().toString());
        e.setFingerprint("fp-123");
        e.setDefaultZoneId("zone-1");
        e.setAuthenticationMethod("SIF_HMACSHA256");
        return e;
    }

    private AclEntry buildAcl(Environment env, UUID contextUuid, String serviceName,
                               AccessType accessType, AccessLevel accessLevel) {
        AclEntry acl = new AclEntry();
        acl.setId(UUID.randomUUID());
        acl.setEnvironment(env);
        acl.setContextServiceUuid(UUID.randomUUID());
        acl.setAccessType(accessType);
        acl.setAccessLevel(accessLevel);
        return acl;
    }

    private AclEntry buildAclWithContextService(Environment env, UUID contextServiceUuid,
                                                 AccessType accessType, AccessLevel accessLevel) {
        AclEntry acl = new AclEntry();
        acl.setId(UUID.randomUUID());
        acl.setEnvironment(env);
        acl.setContextServiceUuid(contextServiceUuid);
        acl.setAccessType(accessType);
        acl.setAccessLevel(accessLevel);
        return acl;
    }
}
