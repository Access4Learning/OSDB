// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import org.sifunity.broker.environment.config.ProvisionRequestConfig;
import org.sifunity.broker.environment.domain.*;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.exception.AdminRequiredException;
import org.sifunity.broker.environment.repository.*;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.service.ProvisionRequestService;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property 7: Admin-only resolution.
 *
 * <p>For any non-admin environment attempting to PUT (resolve) a provisionRequest,
 * the service SHALL deny the request. Only environments belonging to
 * GLOBAL_DOMAIN/GLOBAL_SOLUTION SHALL be permitted to resolve provisionRequests.</p>
 *
 * <p><strong>Validates: Requirements 5.5</strong></p>
 */
@Tag("Feature: provision-requests, Property 7: Admin-only resolution")
class AdminOnlyResolutionPropertyTest {

    private static final ObjectMapper REAL_OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Non-admin environments are denied resolution.
     *
     * <p><strong>Validates: Requirements 5.5</strong></p>
     */
    @Property(tries = 20)
    void nonAdminEnvironment_throwsAdminRequiredException(
            @ForAll("nonAdminEnvironments") Environment nonAdminRequester) {

        ProvisionRequestRepository provisionRequestRepo = mock(ProvisionRequestRepository.class);
        AclEntryRepository aclEntryRepo = mock(AclEntryRepository.class);
        EnvironmentRepository environmentRepo = mock(EnvironmentRepository.class);
        ZoneRepository zoneRepo = mock(ZoneRepository.class);
        ContextRepository contextRepo = mock(ContextRepository.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        ProvisionRequestConfig config = new ProvisionRequestConfig(false);

        // Non-admin
        when(authorizationService.isAdmin(nonAdminRequester)).thenReturn(false);

        ProvisionRequestService service = new ProvisionRequestService(
                provisionRequestRepo, aclEntryRepo, environmentRepo,
                zoneRepo, contextRepo, mock(ContextServiceRepository.class), config, authorizationService, REAL_OBJECT_MAPPER);

        UUID provisionRequestId = UUID.randomUUID();
        ProvisionRequestResolveDTO resolveDTO = buildMinimalResolveDTO();

        assertThatThrownBy(() ->
                service.resolveProvisionRequest(provisionRequestId, resolveDTO, nonAdminRequester))
                .isInstanceOf(AdminRequiredException.class);

        // Verify no ACL entries were created
        verify(aclEntryRepo, never()).save(any());
    }

    /**
     * Admin environments are permitted to resolve.
     *
     * <p><strong>Validates: Requirements 5.5</strong></p>
     */
    @Property(tries = 20)
    void adminEnvironment_doesNotThrowAdminRequiredException(
            @ForAll("adminEnvironments") Environment adminRequester) {

        ProvisionRequestRepository provisionRequestRepo = mock(ProvisionRequestRepository.class);
        AclEntryRepository aclEntryRepo = mock(AclEntryRepository.class);
        EnvironmentRepository environmentRepo = mock(EnvironmentRepository.class);
        ZoneRepository zoneRepo = mock(ZoneRepository.class);
        ContextRepository contextRepo = mock(ContextRepository.class);
        ContextServiceRepository contextServiceRepo = mock(ContextServiceRepository.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        ProvisionRequestConfig config = new ProvisionRequestConfig(false);

        // Admin
        when(authorizationService.isAdmin(adminRequester)).thenReturn(true);

        UUID environmentId = UUID.randomUUID();
        UUID provisionRequestId = UUID.randomUUID();

        // Mock provision request
        ProvisionRequest pr = new ProvisionRequest();
        pr.setId(provisionRequestId);
        pr.setEnvironmentId(environmentId);
        pr.setProvisionedZonesPayload("{}");
        pr.setCreatedAt(Instant.now());
        when(provisionRequestRepo.findById(provisionRequestId)).thenReturn(Optional.of(pr));
        when(provisionRequestRepo.save(any(ProvisionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mock environment
        Environment env = mock(Environment.class);
        when(env.getId()).thenReturn(environmentId);
        when(env.getSolutionUuid()).thenReturn(UUID.randomUUID());
        when(environmentRepo.findById(environmentId)).thenReturn(Optional.of(env));

        // Mock zone and context
        UUID zoneUuid = UUID.randomUUID();
        Zone zone = new Zone();
        zone.setId(zoneUuid);
        zone.setZoneId("testZone");
        when(zoneRepo.findBySolutionUuidAndZoneId(any(), eq("testZone"))).thenReturn(Optional.of(zone));

        UUID contextUuid = UUID.randomUUID();
        Context ctx = new Context();
        ctx.setId(contextUuid);
        ctx.setContextId("DEFAULT");
        ctx.setZoneUuid(zoneUuid);
        when(contextRepo.findByZoneUuidAndContextId(zoneUuid, "DEFAULT"))
                .thenReturn(Optional.of(ctx));

        // Mock contextServiceRepository — findByContextUuidAndServiceName returns empty so it auto-creates
        when(contextServiceRepo.findByContextUuidAndServiceName(any(), any())).thenReturn(Optional.empty());
        when(contextServiceRepo.save(any(org.sifunity.broker.environment.domain.ContextService.class))).thenAnswer(inv -> {
            org.sifunity.broker.environment.domain.ContextService cs = inv.getArgument(0);
            if (cs.getId() == null) cs.setId(UUID.randomUUID());
            return cs;
        });

        when(aclEntryRepo.findByEnvironmentIdAndContextServiceUuidAndAccessType(any(), any(), any())).thenReturn(Optional.empty());
        when(aclEntryRepo.save(any(AclEntry.class))).thenAnswer(inv -> {
            AclEntry e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });

        ProvisionRequestService service = new ProvisionRequestService(
                provisionRequestRepo, aclEntryRepo, environmentRepo,
                zoneRepo, contextRepo, contextServiceRepo, config, authorizationService, REAL_OBJECT_MAPPER);

        ProvisionRequestResolveDTO resolveDTO = buildMinimalResolveDTO();

        // Should NOT throw AdminRequiredException
        ProvisionRequestResponseDTO result = service.resolveProvisionRequest(
                provisionRequestId, resolveDTO, adminRequester);

        assertThat(result).isNotNull();
        assertThat(result.completionStatus()).isNotNull();
    }

    // ========== Helpers ==========

    private static ProvisionRequestResolveDTO buildMinimalResolveDTO() {
        RightDTO right = new RightDTO("QUERY", "APPROVED");
        ProvisionedServiceDTO svc = new ProvisionedServiceDTO(
                "OBJECT", "students", "DEFAULT",
                new ProvisionedServiceRightsDTO(List.of(right)));
        ProvisionedZoneDTO zone = new ProvisionedZoneDTO("testZone",
                new ProvisionedZoneServicesDTO(List.of(svc)));
        return new ProvisionRequestResolveDTO(
                new ProvisionedZonesDTO(List.of(zone)));
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<Environment> nonAdminEnvironments() {
        return Arbitraries.create(() -> {
            Environment env = mock(Environment.class);
            when(env.getId()).thenReturn(UUID.randomUUID());
            return env;
        });
    }

    @Provide
    Arbitrary<Environment> adminEnvironments() {
        return Arbitraries.create(() -> {
            Environment env = mock(Environment.class);
            when(env.getId()).thenReturn(UUID.randomUUID());
            return env;
        });
    }
}
