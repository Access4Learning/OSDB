// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.ContextService;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.AnyGrantCheckRequest;
import org.sifunity.broker.environment.dto.PermissionCheckResponse;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link AuthorizationService#hasAnyGrant}, the entitlement check behind the
 * response-destination gate.
 *
 * <p>The load-bearing assertion here is
 * {@link #usesTheAccessTypeAgnosticQuery()}: it verifies the check consults the
 * <em>access-type-agnostic</em> repository method and never the access-type-specific one. That is
 * the guard against someone narrowing this to {@code QUERY}, which would silently reject responses
 * to create/update/delete requests — a consumer that issued a create holds {@code CREATE} and may
 * hold no {@code QUERY} grant at all.</p>
 *
 * <p>Kept separate from {@code AuthorizationServiceTest} so that suite's 53 existing assertions on
 * the hot {@code checkPermission} path stay untouched.</p>
 *
 * <p><strong>Validates: Requirements 2.3, 2.7</strong></p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthorizationService.hasAnyGrant")
class AuthorizationServiceAnyGrantTest {

    @Mock
    private AclEntryRepository aclEntryRepository;
    @Mock
    private EnvironmentRepository environmentRepository;
    @Mock
    private SolutionService solutionService;
    @Mock
    private DomainService domainService;
    @Mock
    private ContextRepository contextRepository;
    @Mock
    private ContextServiceRepository contextServiceRepository;
    @Mock
    private ZoneRepository zoneRepository;

    private AuthorizationService authorizationService;

    private UUID environmentId;
    private UUID solutionUuid;
    private UUID contextServiceUuid;
    private AnyGrantCheckRequest request;

    @BeforeEach
    void setUp() {
        authorizationService = new AuthorizationService(aclEntryRepository, environmentRepository,
                solutionService, domainService, contextRepository, contextServiceRepository,
                zoneRepository);

        environmentId = UUID.randomUUID();
        solutionUuid = UUID.randomUUID();
        contextServiceUuid = UUID.randomUUID();
        request = new AnyGrantCheckRequest(environmentId, "StudentPersonal", "test-zone", "DEFAULT");
    }

    /** A non-admin environment, so the admin short-circuit does not apply. */
    private Environment nonAdminEnvironment() {
        Environment environment = new Environment();
        environment.setId(environmentId);
        environment.setSolutionUuid(solutionUuid);
        return environment;
    }

    private void resolveZoneContextService() {
        Zone zone = new Zone();
        zone.setId(UUID.randomUUID());
        Context context = new Context();
        context.setId(UUID.randomUUID());
        ContextService contextService = new ContextService();
        contextService.setId(contextServiceUuid);

        when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, "test-zone"))
                .thenReturn(Optional.of(zone));
        when(contextRepository.findByZoneUuidAndContextId(zone.getId(), "DEFAULT"))
                .thenReturn(Optional.of(context));
        when(contextServiceRepository.findByContextUuidAndServiceName(context.getId(), "StudentPersonal"))
                .thenReturn(Optional.of(contextService));
    }

    @Test
    @DisplayName("Uses the access-type-agnostic query, never the access-type-specific one")
    void usesTheAccessTypeAgnosticQuery() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(nonAdminEnvironment()));
        resolveZoneContextService();
        when(aclEntryRepository.existsByEnvironmentIdAndContextServiceUuidAndAccessLevel(
                environmentId, contextServiceUuid, AccessLevel.APPROVED)).thenReturn(true);

        assertThat(authorizationService.hasAnyGrant(request).granted()).isTrue();

        verify(aclEntryRepository).existsByEnvironmentIdAndContextServiceUuidAndAccessLevel(
                environmentId, contextServiceUuid, AccessLevel.APPROVED);

        // The guard: requiring a specific access type would reject responses to create/update/delete
        // requests, whose consumer may hold no QUERY grant.
        verify(aclEntryRepository, never()).findByEnvironmentIdAndContextServiceUuidAndAccessType(
                any(UUID.class), any(UUID.class), any(AccessType.class));
    }

    @Test
    @DisplayName("Only APPROVED grants count as entitlement")
    void onlyApprovedGrantsCount() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(nonAdminEnvironment()));
        resolveZoneContextService();

        // Nothing approved for this environment/context-service.
        when(aclEntryRepository.existsByEnvironmentIdAndContextServiceUuidAndAccessLevel(
                environmentId, contextServiceUuid, AccessLevel.APPROVED)).thenReturn(false);

        assertThat(authorizationService.hasAnyGrant(request).granted()).isFalse();
        verify(aclEntryRepository).existsByEnvironmentIdAndContextServiceUuidAndAccessLevel(
                eq(environmentId), eq(contextServiceUuid), eq(AccessLevel.APPROVED));
    }

    @Test
    @DisplayName("Fails closed when the environment does not exist")
    void failsClosedOnMissingEnvironment() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.empty());

        assertThat(authorizationService.hasAnyGrant(request).granted()).isFalse();
        verifyNoInteractions(aclEntryRepository);
    }

    @Test
    @DisplayName("Fails closed when the zone cannot be resolved")
    void failsClosedOnUnresolvableZone() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(nonAdminEnvironment()));
        when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, "test-zone"))
                .thenReturn(Optional.empty());

        assertThat(authorizationService.hasAnyGrant(request).granted()).isFalse();
        verifyNoInteractions(aclEntryRepository);
    }

    @Test
    @DisplayName("Fails closed when no context_service exists for the object")
    void failsClosedOnMissingContextService() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(nonAdminEnvironment()));

        Zone zone = new Zone();
        zone.setId(UUID.randomUUID());
        Context context = new Context();
        context.setId(UUID.randomUUID());
        when(zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, "test-zone"))
                .thenReturn(Optional.of(zone));
        when(contextRepository.findByZoneUuidAndContextId(zone.getId(), "DEFAULT"))
                .thenReturn(Optional.of(context));
        when(contextServiceRepository.findByContextUuidAndServiceName(context.getId(), "StudentPersonal"))
                .thenReturn(Optional.empty());

        assertThat(authorizationService.hasAnyGrant(request).granted()).isFalse();
        verifyNoInteractions(aclEntryRepository);
    }

    @Test
    @DisplayName("Fails closed when the lookup throws")
    void failsClosedOnException() {
        when(environmentRepository.findById(environmentId))
                .thenThrow(new RuntimeException("database unavailable"));

        assertThat(authorizationService.hasAnyGrant(request).granted())
                .as("an error must deny, never admit")
                .isFalse();
    }
}
