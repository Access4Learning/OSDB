// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;

import org.sifunity.broker.environment.config.ProvisionRequestConfig;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.ProvisionRequest;
import org.sifunity.broker.environment.exception.ProvisionRequestNotFoundException;
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
 * Property 9: Delete removes provision request.
 *
 * <p>For any existing provisionRequest, after a successful DELETE by the owning consumer,
 * querying that provisionRequest by ID SHALL return 404.</p>
 *
 * <p><strong>Validates: Requirements 3.1</strong></p>
 */
@Tag("Feature: provision-requests, Property 9: Delete removes provision request")
class ProvisionRequestDeletePropertyTest {

    private static final ObjectMapper REAL_OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Property 9: After delete, querying the same provision request throws ProvisionRequestNotFoundException.
     *
     * <p><strong>Validates: Requirements 3.1</strong></p>
     */
    @Property(tries = 20)
    void afterDelete_queryThrowsNotFoundException() {
        UUID ownerId = UUID.randomUUID();
        UUID provisionRequestId = UUID.randomUUID();

        // --- Set up mocks ---
        ProvisionRequestRepository provisionRequestRepo = mock(ProvisionRequestRepository.class);
        AclEntryRepository aclEntryRepo = mock(AclEntryRepository.class);
        EnvironmentRepository environmentRepo = mock(EnvironmentRepository.class);
        ZoneRepository zoneRepo = mock(ZoneRepository.class);
        ContextRepository contextRepo = mock(ContextRepository.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        ProvisionRequestConfig config = new ProvisionRequestConfig(false);

        // Create a provision request owned by ownerId
        ProvisionRequest pr = new ProvisionRequest();
        pr.setId(provisionRequestId);
        pr.setEnvironmentId(ownerId);
        pr.setProvisionedZonesPayload("{\"provisionedZone\":[]}");
        pr.setCreatedAt(Instant.now());

        // Before delete: findById returns the entity
        when(provisionRequestRepo.findById(provisionRequestId)).thenReturn(Optional.of(pr));

        // Owner is admin check — not admin, but is owner so allowed
        when(authorizationService.isAdmin(any(Environment.class))).thenReturn(false);

        // Mock requester as owner
        Environment owner = mock(Environment.class);
        when(owner.getId()).thenReturn(ownerId);

        ProvisionRequestService service = new ProvisionRequestService(
                provisionRequestRepo, aclEntryRepo, environmentRepo,
                zoneRepo, contextRepo, mock(ContextServiceRepository.class),
                config, authorizationService, REAL_OBJECT_MAPPER);

        // --- Act: delete ---
        service.deleteProvisionRequest(provisionRequestId, owner);

        // Verify delete was called
        verify(provisionRequestRepo).delete(pr);

        // After delete: mock repo.findById to return empty
        when(provisionRequestRepo.findById(provisionRequestId)).thenReturn(Optional.empty());

        // --- Assert: query throws ProvisionRequestNotFoundException ---
        assertThatThrownBy(() -> service.getProvisionRequest(provisionRequestId, owner))
                .isInstanceOf(ProvisionRequestNotFoundException.class);
    }
}
