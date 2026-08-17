// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;

import org.sifunity.broker.environment.config.ProvisionRequestConfig;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.ProvisionRequest;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.exception.ProvisionRequestAccessDeniedException;
import org.sifunity.broker.environment.repository.*;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.service.ProvisionRequestService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 3: Ownership isolation.
 *
 * <p>For any provisionRequest owned by environment A, an authenticated request from
 * environment B (where B ≠ A and B is not an admin) to query or delete that
 * provisionRequest SHALL be denied with HTTP 403.</p>
 *
 * <p><strong>Validates: Requirements 2.4, 2.5, 3.3, 3.4</strong></p>
 */
@Tag("Feature: provision-requests, Property 3: Ownership isolation")
class ProvisionRequestOwnershipPropertyTest {

    private static final ObjectMapper REAL_OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Property 3: Non-owner, non-admin environment is denied query access.
     *
     * <p><strong>Validates: Requirements 2.4, 2.5</strong></p>
     */
    @Property(tries = 20)
    void nonOwnerNonAdmin_isDeniedQuery(
            @ForAll("environmentUUIDs") UUID envA,
            @ForAll("environmentUUIDs") UUID envB) {
        Assume.that(!envA.equals(envB));
        UUID provisionRequestId = UUID.randomUUID();

        // --- Set up mocks ---
        ProvisionRequestRepository provisionRequestRepo = mock(ProvisionRequestRepository.class);
        AclEntryRepository aclEntryRepo = mock(AclEntryRepository.class);
        EnvironmentRepository environmentRepo = mock(EnvironmentRepository.class);
        ZoneRepository zoneRepo = mock(ZoneRepository.class);
        ContextRepository contextRepo = mock(ContextRepository.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        ProvisionRequestConfig config = new ProvisionRequestConfig(false);

        // Create a provision request owned by envA
        ProvisionRequest pr = new ProvisionRequest();
        pr.setId(provisionRequestId);
        pr.setEnvironmentId(envA);
        pr.setProvisionedZonesPayload("{\"provisionedZone\":[]}");
        pr.setCreatedAt(Instant.now());

        when(provisionRequestRepo.findById(provisionRequestId)).thenReturn(Optional.of(pr));

        // envB is NOT admin
        when(authorizationService.isAdmin(any(Environment.class))).thenReturn(false);

        // Mock requester as envB
        Environment requesterB = mock(Environment.class);
        when(requesterB.getId()).thenReturn(envB);

        ProvisionRequestService service = new ProvisionRequestService(
                provisionRequestRepo, aclEntryRepo, environmentRepo,
                zoneRepo, contextRepo, mock(ContextServiceRepository.class), config, authorizationService, REAL_OBJECT_MAPPER);

        // --- Assert: query denied ---
        assertThatThrownBy(() -> service.getProvisionRequest(provisionRequestId, requesterB))
                .isInstanceOf(ProvisionRequestAccessDeniedException.class);
    }

    /**
     * Property 3: Non-owner, non-admin environment is denied delete access.
     *
     * <p><strong>Validates: Requirements 3.3, 3.4</strong></p>
     */
    @Property(tries = 20)
    void nonOwnerNonAdmin_isDeniedDelete(
            @ForAll("environmentUUIDs") UUID envA,
            @ForAll("environmentUUIDs") UUID envB) {
        Assume.that(!envA.equals(envB));
        UUID provisionRequestId = UUID.randomUUID();

        // --- Set up mocks ---
        ProvisionRequestRepository provisionRequestRepo = mock(ProvisionRequestRepository.class);
        AclEntryRepository aclEntryRepo = mock(AclEntryRepository.class);
        EnvironmentRepository environmentRepo = mock(EnvironmentRepository.class);
        ZoneRepository zoneRepo = mock(ZoneRepository.class);
        ContextRepository contextRepo = mock(ContextRepository.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        ProvisionRequestConfig config = new ProvisionRequestConfig(false);

        // Create a provision request owned by envA
        ProvisionRequest pr = new ProvisionRequest();
        pr.setId(provisionRequestId);
        pr.setEnvironmentId(envA);
        pr.setProvisionedZonesPayload("{\"provisionedZone\":[]}");
        pr.setCreatedAt(Instant.now());

        when(provisionRequestRepo.findById(provisionRequestId)).thenReturn(Optional.of(pr));

        // envB is NOT admin
        when(authorizationService.isAdmin(any(Environment.class))).thenReturn(false);

        // Mock requester as envB
        Environment requesterB = mock(Environment.class);
        when(requesterB.getId()).thenReturn(envB);

        ProvisionRequestService service = new ProvisionRequestService(
                provisionRequestRepo, aclEntryRepo, environmentRepo,
                zoneRepo, contextRepo, mock(ContextServiceRepository.class), config, authorizationService, REAL_OBJECT_MAPPER);

        // --- Assert: delete denied ---
        assertThatThrownBy(() -> service.deleteProvisionRequest(provisionRequestId, requesterB))
                .isInstanceOf(ProvisionRequestAccessDeniedException.class);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<UUID> environmentUUIDs() {
        return Arbitraries.create(UUID::randomUUID);
    }
}
