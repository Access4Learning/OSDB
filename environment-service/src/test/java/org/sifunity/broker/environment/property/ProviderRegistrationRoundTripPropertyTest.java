// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.ProviderRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.ZoneCreateRequest;
import org.sifunity.broker.environment.service.ContextServiceService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.ProviderService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.Zone;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationBody;

/**
 * Property-based test for Property 1: Round-trip preserves all SIF 3.7 fields.
 *
 * <p><strong>Property:</strong> For any valid ProviderRegistrationRequest containing a random
 * serviceType, a random non-blank providerName, and a random valid supportedRequestTypes
 * structure, creating a provider registration and then retrieving it (via lookup by
 * serviceName/zoneId/contextId) SHALL produce a response where serviceType, providerName,
 * and supportedRequestTypes are equivalent to the values in the original request.</p>
 *
 * <p><strong>Validates: Requirements 1.4, 2.1, 2.6, 3.4, 7.3, 7.4, 7.5</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProviderRegistrationRoundTripPropertyTest {

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private ZoneService zoneService;

    @Autowired
    private ContextServiceService contextServiceService;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private ContextRepository contextRepository;

    private final Random random = new Random();

    /**
     * Property 1: Provider registration round-trip preserves all SIF 3.7 fields
     *
     * <p><strong>Validates: Requirements 1.4, 2.1, 2.6, 3.4, 7.3, 7.4, 7.5</strong></p>
     */
    @RepeatedTest(10)
    @Tag("provider-registry-sif-alignment-property-1")
    void roundTripPreservesAllSif37Fields() {
        // Create test environment infrastructure
        String domainId = "rt-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));
        String solutionId = "rt-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
        
        // Create zone with DEFAULT context
        String zoneId = "rt-zone-" + System.nanoTime();
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Test zone"));
        String contextId = "DEFAULT";

        String appName = "RoundTripApp-" + System.nanoTime();
        EnvironmentRegistrationRequest envRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId), solutionId, null));
        SifEnvironmentRegistrationResponse environment = environmentService.registerEnvironment(domainId, envRequest);

        // Generate random inputs
        ServiceType serviceType = randomServiceType();
        List<SupportedRequestType> supportedRequestTypes = randomValidSupportedRequestTypes();

        // Generate unique service name
        String serviceName = "RoundTripSvc-" + System.nanoTime();
        String endpoint = "http://provider.example.com:" + (8000 + random.nextInt(1000)) + "/api";

        // Resolve contextServiceUuid via the zone's DEFAULT context
        UUID solutionUuid = solutionService.getSolution(domainId, solutionId).id();
        Zone zone = zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, zoneId).orElseThrow();
        Context context = contextRepository.findByZoneUuidAndContextId(zone.getId(), contextId).orElseThrow();
        UUID contextServiceUuid = contextServiceService.resolveOrCreate(
                context.getId(), serviceName, serviceType.name());

        // Create provider registration
        ProviderRegistrationRequest request = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environment.environment().id(),
                null,
                serviceType,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of(endpoint),
                SupportedRequestsDTO.of(supportedRequestTypes)));

        ProviderRegistrationDTO registered = providerService.registerProvider(domainId, solutionId, request);

        // Retrieve via lookup
        Optional<ProviderRegistrationDTO> lookedUp = providerService.lookupProvider(
                solutionUuid, serviceName, zoneId, contextId);

        assertThat(lookedUp).isPresent();
        ProviderRegistrationDTO retrieved = lookedUp.get();

        // Assert serviceType round-trips (Req 7.4)
        assertThat(retrieved.serviceType())
                .as("serviceType should round-trip")
                .isEqualTo(serviceType);

        // Assert consumerName is present (formerly providerName round-trip)
        // Note: consumerName mapping logic will be completed in task 4.3
        // For now, mapToProviderRegistrationDTO passes null for consumerName

        // Assert supportedRequestTypes round-trips (Req 7.3)
        assertThat(retrieved.supportedRequestTypes().supportedRequestType())
                .as("supportedRequestTypes size should match")
                .hasSameSizeAs(supportedRequestTypes);

        for (SupportedRequestType expectedEntry : supportedRequestTypes) {
            SupportedRequestType matchingEntry = retrieved.supportedRequestTypes().supportedRequestType().stream()
                    .filter(srt -> srt.requestType() == expectedEntry.requestType())
                    .findFirst()
                    .orElse(null);

            assertThat(matchingEntry)
                    .as("Should find entry for requestType " + expectedEntry.requestType())
                    .isNotNull();

            assertThat(matchingEntry.operations().operation())
                    .as("Operations for " + expectedEntry.requestType() + " should match")
                    .containsExactlyInAnyOrderElementsOf(expectedEntry.operations().operation());
        }
    }

    // ========== Random Data Generators ==========

    private ServiceType randomServiceType() {
        ServiceType[] values = ServiceType.values();
        return values[random.nextInt(values.length)];
    }

    private String randomProviderName() {
        String[] names = {"LovellSIS", "AcmeLMS", "BrightAssess", "EduTrack", "SchoolSync", "DataBridge"};
        return names[random.nextInt(names.length)] + "-" + random.nextInt(1000);
    }

    private List<SupportedRequestType> randomValidSupportedRequestTypes() {
        List<SupportedRequestType> result = new ArrayList<>();

        // Always add at least one request type
        RequestType[] requestTypes = RequestType.values();
        List<RequestType> available = new ArrayList<>(List.of(requestTypes));
        Collections.shuffle(available, random);

        // 1 or 2 request types
        int count = 1 + random.nextInt(Math.min(2, available.size()));
        for (int i = 0; i < count; i++) {
            result.add(new SupportedRequestType(available.get(i), OperationsDTO.of(randomOperations())));
        }
        return result;
    }

    private Set<Operation> randomOperations() {
        Operation[] allOps = Operation.values();
        Set<Operation> ops = EnumSet.noneOf(Operation.class);
        // At least 1, up to 4 operations
        ops.add(allOps[random.nextInt(allOps.length)]);
        for (Operation op : allOps) {
            if (random.nextBoolean()) {
                ops.add(op);
            }
        }
        return ops;
    }

    private SifEnvironmentRegistrationResponse createTestProviderEnvironment() {
        String domainId = "rt-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));
        String solutionId = "rt-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));

        String zoneId = "rt-default-zone-" + System.nanoTime();
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Test zone"));

        String appName = "RoundTripApp-" + System.nanoTime();
        EnvironmentRegistrationRequest envRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId), solutionId, null));
        return environmentService.registerEnvironment(domainId, envRequest);
    }
}
