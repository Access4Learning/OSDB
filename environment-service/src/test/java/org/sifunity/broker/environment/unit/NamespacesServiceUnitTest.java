// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.unit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Namespace;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.NamespaceRepository;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.NamespaceEventPublisher;
import org.sifunity.broker.environment.service.NamespacesService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NamespacesService edge cases.
 *
 * <p>Covers tasks 11.1 through 11.8:</p>
 * <ul>
 *   <li>11.1 — Event publication failure isolation (Req 6.6)</li>
 *   <li>11.2 — No-provider-registered error (Req 11.1)</li>
 *   <li>11.3 — Response wrapper format (Req 8.4, 8.5)</li>
 *   <li>11.4 — HTTP 204 when no namespaces match query (Req 1.5)</li>
 *   <li>11.5 — 404 on non-existent ID (Req 2.2, 4.2, 5.2)</li>
 *   <li>11.6 — Default zone resolution (Req 1.4)</li>
 *   <li>11.7 — Update duplicate detection (Req 4.8)</li>
 *   <li>11.8 — Provided id ignored on create (Req 3.6)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NamespacesService Unit Tests")
class NamespacesServiceUnitTest {

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private ProviderRegistrationRepository providerRegistrationRepository;

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private org.sifunity.broker.environment.repository.ZoneRepository zoneRepository;

    @Mock
    private org.sifunity.broker.environment.repository.ContextRepository contextRepository;

    @Mock
    private org.sifunity.broker.environment.repository.ContextServiceRepository contextServiceRepository;

    @Mock
    private NamespaceEventPublisher namespaceEventPublisher;

    private NamespacesService namespacesService;

    private Domain domain;
    private Solution solution;

    @BeforeEach
    void setUp() {
        namespacesService = new NamespacesService(
                namespaceRepository, providerRegistrationRepository,
                contextRepository, contextServiceRepository, zoneRepository, domainRepository, solutionRepository,
                namespaceEventPublisher, new SimpleMeterRegistry());

        domain = new Domain();
        domain.setId(UUID.randomUUID());
        domain.setDomainId("test-domain");

        solution = new Solution();
        solution.setId(UUID.randomUUID());
        solution.setSolutionId("test-solution");
        solution.setDomainUuid(domain.getId());
    }

    // ========== Helper Methods ==========

    private void stubDomainAndSolution() {
        when(domainRepository.findByDomainId("test-domain")).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domain.getId(), "test-solution"))
                .thenReturn(Optional.of(solution));
    }

    private void stubProviderRegistered() {
        // checkProviderRegistered resolves: zone → context → contextService → providerRegistration
        org.sifunity.broker.environment.domain.Zone envGlobalZone = new org.sifunity.broker.environment.domain.Zone();
        envGlobalZone.setId(UUID.randomUUID());
        envGlobalZone.setZoneId("environment-global");
        when(zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), "environment-global"))
                .thenReturn(Optional.of(envGlobalZone));

        org.sifunity.broker.environment.domain.Context defaultCtx = new org.sifunity.broker.environment.domain.Context();
        defaultCtx.setId(UUID.randomUUID());
        defaultCtx.setContextId("DEFAULT");
        when(contextRepository.findByZoneUuidAndContextId(envGlobalZone.getId(), "DEFAULT"))
                .thenReturn(Optional.of(defaultCtx));

        org.sifunity.broker.environment.domain.ContextService namespacesCs = new org.sifunity.broker.environment.domain.ContextService();
        namespacesCs.setId(UUID.randomUUID());
        namespacesCs.setContextUuid(defaultCtx.getId());
        namespacesCs.setServiceName("namespaces");
        when(contextServiceRepository.findByContextUuidAndServiceName(defaultCtx.getId(), "namespaces"))
                .thenReturn(Optional.of(namespacesCs));

        when(providerRegistrationRepository.findByContextServiceUuid(namespacesCs.getId()))
                .thenReturn(Optional.of(org.sifunity.broker.environment.domain.ProviderRegistration.builder().id(UUID.randomUUID()).build()));
    }

    private Namespace buildNamespaceEntity(String uri, String zone) {
        Namespace ns = new Namespace();
        ns.setId(UUID.randomUUID());
        ns.setZone(zone);
        ns.setUri(uri);
        ns.setUrl("https://schemas.example.org/schema.xsd");
        ns.setSolutionUuid(solution.getId());
        ns.setCreatedAt(Instant.now());
        ns.setUpdatedAt(Instant.now());
        return ns;
    }

    private NamespaceCreateBody validCreateBody() {
        return new NamespaceCreateBody(
                null, "environment-global",
                "http://www.sifassociation.org/ns/test",
                "https://schemas.example.org/schema.xsd");
    }

    // ========== 11.1 Event Publication Failure Isolation (Req 6.6) ==========

    @Nested
    @DisplayName("11.1 Event Publication Failure Isolation")
    class EventPublicationFailureIsolation {

        @Test
        @DisplayName("Create returns successfully when event publication throws exception")
        void createNamespace_succeeds_whenEventPublicationFails() {
            stubDomainAndSolution();
            stubProviderRegistered();

            NamespaceCreateBody body = validCreateBody();

            when(namespaceRepository.findByUriAndZoneAndSolutionUuid(
                    body.uri(), body.zone(), solution.getId()))
                    .thenReturn(Optional.empty());

            Namespace saved = buildNamespaceEntity(body.uri(), body.zone());
            when(namespaceRepository.save(any(Namespace.class))).thenReturn(saved);

            doThrow(new RuntimeException("Events connector unavailable"))
                    .when(namespaceEventPublisher)
                    .publishNamespaceCreatedEvent(any(NamespaceDTO.class), anyString(), anyString());

            NamespaceDTO result = namespacesService.createNamespace("test-domain", "test-solution", body);

            assertThat(result).isNotNull();
            assertThat(result.uri()).isEqualTo(body.uri());
            verify(namespaceRepository).save(any(Namespace.class));
            verify(namespaceEventPublisher).publishNamespaceCreatedEvent(
                    any(NamespaceDTO.class), eq("test-domain"), eq("test-solution"));
        }

        @Test
        @DisplayName("Update returns successfully when event publication throws exception")
        void updateNamespace_succeeds_whenEventPublicationFails() {
            stubDomainAndSolution();

            Namespace existing = buildNamespaceEntity("http://ns/update", "environment-global");
            UUID id = existing.getId();

            NamespaceCreateBody body = new NamespaceCreateBody(
                    null, "environment-global",
                    "http://ns/updated", "https://schemas.example.org/updated.xsd");

            when(namespaceRepository.findById(id)).thenReturn(Optional.of(existing));
            when(namespaceRepository.findByUriAndZoneAndSolutionUuid(
                    body.uri(), body.zone(), solution.getId()))
                    .thenReturn(Optional.empty());
            when(namespaceRepository.save(any(Namespace.class))).thenReturn(existing);

            doThrow(new RuntimeException("Events connector unavailable"))
                    .when(namespaceEventPublisher)
                    .publishNamespaceUpdatedEvent(any(NamespaceDTO.class), anyString(), anyString());

            NamespaceDTO result = namespacesService.updateNamespace(
                    "test-domain", "test-solution", id, body);

            assertThat(result).isNotNull();
            verify(namespaceRepository).save(any(Namespace.class));
        }

        @Test
        @DisplayName("Delete returns successfully when event publication throws exception")
        void deleteNamespace_succeeds_whenEventPublicationFails() {
            stubDomainAndSolution();

            Namespace existing = buildNamespaceEntity("http://ns/delete", "environment-global");
            UUID id = existing.getId();

            when(namespaceRepository.findById(id)).thenReturn(Optional.of(existing));

            doThrow(new RuntimeException("Events connector unavailable"))
                    .when(namespaceEventPublisher)
                    .publishNamespaceDeletedEvent(any(NamespaceDTO.class), anyString(), anyString());

            // Should not throw
            namespacesService.deleteNamespace("test-domain", "test-solution", id);

            verify(namespaceRepository).delete(existing);
            verify(namespaceEventPublisher).publishNamespaceDeletedEvent(
                    any(NamespaceDTO.class), eq("test-domain"), eq("test-solution"));
        }
    }

    // ========== 11.2 No-Provider-Registered Error (Req 11.1) ==========

    @Nested
    @DisplayName("11.2 No Provider Registered Error")
    class NoProviderRegisteredError {

        @Test
        @DisplayName("Returns 404 with NAMESPACE_006 when no namespaces provider registered")
        void createNamespace_returns404_whenNoProviderRegistered() {
            stubDomainAndSolution();

            // Stub zone → context → contextService chain but no provider at the end
            org.sifunity.broker.environment.domain.Zone envGlobalZone = new org.sifunity.broker.environment.domain.Zone();
            envGlobalZone.setId(UUID.randomUUID());
            envGlobalZone.setZoneId("environment-global");
            when(zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), "environment-global"))
                    .thenReturn(Optional.of(envGlobalZone));

            org.sifunity.broker.environment.domain.Context defaultCtx = new org.sifunity.broker.environment.domain.Context();
            defaultCtx.setId(UUID.randomUUID());
            defaultCtx.setContextId("DEFAULT");
            when(contextRepository.findByZoneUuidAndContextId(envGlobalZone.getId(), "DEFAULT"))
                    .thenReturn(Optional.of(defaultCtx));

            org.sifunity.broker.environment.domain.ContextService namespacesCs = new org.sifunity.broker.environment.domain.ContextService();
            namespacesCs.setId(UUID.randomUUID());
            namespacesCs.setContextUuid(defaultCtx.getId());
            namespacesCs.setServiceName("namespaces");
            when(contextServiceRepository.findByContextUuidAndServiceName(defaultCtx.getId(), "namespaces"))
                    .thenReturn(Optional.of(namespacesCs));

            when(providerRegistrationRepository.findByContextServiceUuid(namespacesCs.getId()))
                    .thenReturn(Optional.empty());

            NamespaceCreateBody body = validCreateBody();

            assertThatThrownBy(() -> namespacesService.createNamespace(
                    "test-domain", "test-solution", body))
                    .isInstanceOf(SifBrokerException.class)
                    .satisfies(ex -> {
                        SifBrokerException sbe = (SifBrokerException) ex;
                        assertThat(sbe.getErrorCode()).isEqualTo("NAMESPACE_006");
                        assertThat(sbe.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    });

            verify(namespaceRepository, never()).save(any());
        }

        @Test
        @DisplayName("Error message mentions namespaces and environment-global")
        void createNamespace_errorMessage_mentionsProviderAndZone() {
            stubDomainAndSolution();

            // Stub zone → context → contextService chain but no provider at the end
            org.sifunity.broker.environment.domain.Zone envGlobalZone = new org.sifunity.broker.environment.domain.Zone();
            envGlobalZone.setId(UUID.randomUUID());
            envGlobalZone.setZoneId("environment-global");
            when(zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), "environment-global"))
                    .thenReturn(Optional.of(envGlobalZone));

            org.sifunity.broker.environment.domain.Context defaultCtx = new org.sifunity.broker.environment.domain.Context();
            defaultCtx.setId(UUID.randomUUID());
            defaultCtx.setContextId("DEFAULT");
            when(contextRepository.findByZoneUuidAndContextId(envGlobalZone.getId(), "DEFAULT"))
                    .thenReturn(Optional.of(defaultCtx));

            org.sifunity.broker.environment.domain.ContextService namespacesCs = new org.sifunity.broker.environment.domain.ContextService();
            namespacesCs.setId(UUID.randomUUID());
            namespacesCs.setContextUuid(defaultCtx.getId());
            namespacesCs.setServiceName("namespaces");
            when(contextServiceRepository.findByContextUuidAndServiceName(defaultCtx.getId(), "namespaces"))
                    .thenReturn(Optional.of(namespacesCs));

            when(providerRegistrationRepository.findByContextServiceUuid(namespacesCs.getId()))
                    .thenReturn(Optional.empty());

            NamespaceCreateBody body = validCreateBody();

            assertThatThrownBy(() -> namespacesService.createNamespace(
                    "test-domain", "test-solution", body))
                    .isInstanceOf(SifBrokerException.class)
                    .hasMessageContaining("No provider registered")
                    .hasMessageContaining("namespaces")
                    .hasMessageContaining("environment-global");
        }
    }


    // ========== 11.3 Response Wrapper Format (Req 8.4, 8.5) ==========

    @Nested
    @DisplayName("11.3 Response Wrapper Format")
    class ResponseWrapperFormat {

        private ObjectMapper objectMapper;

        @BeforeEach
        void setUpMapper() {
            objectMapper = new ObjectMapper();
            objectMapper.findAndRegisterModules();
        }

        @Test
        @DisplayName("Single namespace wrapped under 'namespace' key")
        void singleNamespace_wrappedUnderNamespaceKey() throws Exception {
            NamespaceDTO dto = new NamespaceDTO(
                    "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    "environment-global",
                    "http://www.sifassociation.org/datamodel/na/4.x",
                    "https://specification.sifassociation.org/Implementation/NA/4.8/XSD/SIF_Message.xsd");

            NamespaceResponse response = new NamespaceResponse(dto);
            String json = objectMapper.writeValueAsString(response);
            JsonNode root = objectMapper.readTree(json);

            assertThat(root.has("namespace")).isTrue();
            assertThat(root.size()).isEqualTo(1);

            JsonNode nsNode = root.get("namespace");
            assertThat(nsNode.get("id").asText()).isEqualTo("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
            assertThat(nsNode.get("zone").asText()).isEqualTo("environment-global");
            assertThat(nsNode.get("uri").asText()).isEqualTo("http://www.sifassociation.org/datamodel/na/4.x");
            assertThat(nsNode.get("url").asText()).contains("SIF_Message.xsd");
        }

        @Test
        @DisplayName("Collection wrapped under 'namespaces.namespace' array")
        void collection_wrappedUnderNamespacesNamespaceArray() throws Exception {
            NamespaceDTO dto1 = new NamespaceDTO(
                    "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    "environment-global",
                    "http://www.sifassociation.org/datamodel/na/4.x",
                    "https://specification.sifassociation.org/NA/4.8/XSD/SIF_Message.xsd");
            NamespaceDTO dto2 = new NamespaceDTO(
                    "b2c3d4e5-f6a7-8901-bcde-f12345678901",
                    "environment-global",
                    "http://www.sifassociation.org/infrastructure/3.x",
                    "https://specification.sifassociation.org/Infrastructure/3.7/XSD/SIF_Infrastructure.xsd");

            NamespaceCollectionResponse response = new NamespaceCollectionResponse(
                    new NamespaceCollection(List.of(dto1, dto2)));

            String json = objectMapper.writeValueAsString(response);
            JsonNode root = objectMapper.readTree(json);

            assertThat(root.has("namespaces")).isTrue();
            assertThat(root.size()).isEqualTo(1);

            JsonNode namespacesNode = root.get("namespaces");
            assertThat(namespacesNode.has("namespace")).isTrue();
            assertThat(namespacesNode.size()).isEqualTo(1);

            JsonNode nsArray = namespacesNode.get("namespace");
            assertThat(nsArray.isArray()).isTrue();
            assertThat(nsArray.size()).isEqualTo(2);

            assertThat(nsArray.get(0).get("uri").asText()).contains("datamodel");
            assertThat(nsArray.get(1).get("uri").asText()).contains("infrastructure");
        }

        @Test
        @DisplayName("Empty collection has correct 'namespaces.namespace' structure")
        void emptyCollection_hasCorrectStructure() throws Exception {
            NamespaceCollectionResponse response = new NamespaceCollectionResponse(
                    new NamespaceCollection(List.of()));

            String json = objectMapper.writeValueAsString(response);
            JsonNode root = objectMapper.readTree(json);

            assertThat(root.has("namespaces")).isTrue();
            JsonNode namespacesNode = root.get("namespaces");
            assertThat(namespacesNode.has("namespace")).isTrue();
            assertThat(namespacesNode.get("namespace").isArray()).isTrue();
            assertThat(namespacesNode.get("namespace").size()).isEqualTo(0);
        }
    }

    // ========== 11.4 HTTP 204 When No Namespaces Match Query (Req 1.5) ==========

    @Nested
    @DisplayName("11.4 HTTP 204 When No Namespaces Match Query")
    class NoNamespacesMatchQuery {

        @Test
        @DisplayName("listNamespaces returns empty list when no namespaces exist")
        void listNamespaces_returnsEmptyList_whenNoNamespacesExist() {
            stubDomainAndSolution();

            when(namespaceRepository.findBySolutionUuid(solution.getId()))
                    .thenReturn(List.of());

            List<NamespaceDTO> result = namespacesService.listNamespaces(
                    "test-domain", "test-solution", "environment-global");

            assertThat(result).isEmpty();
        }
    }

    // ========== 11.5 404 on Non-Existent ID (Req 2.2, 4.2, 5.2) ==========

    @Nested
    @DisplayName("11.5 404 on Non-Existent ID")
    class NotFoundOnNonExistentId {

        @Test
        @DisplayName("GET by non-existent ID returns 404 with NAMESPACE_005")
        void getNamespaceById_returns404_whenIdNotFound() {
            stubDomainAndSolution();

            UUID nonExistentId = UUID.randomUUID();
            when(namespaceRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> namespacesService.getNamespaceById(
                    "test-domain", "test-solution", nonExistentId))
                    .isInstanceOf(SifBrokerException.class)
                    .satisfies(ex -> {
                        SifBrokerException sbe = (SifBrokerException) ex;
                        assertThat(sbe.getErrorCode()).isEqualTo("NAMESPACE_005");
                        assertThat(sbe.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    })
                    .hasMessageContaining(nonExistentId.toString());
        }

        @Test
        @DisplayName("PUT on non-existent ID returns 404 with NAMESPACE_005")
        void updateNamespace_returns404_whenIdNotFound() {
            stubDomainAndSolution();

            UUID nonExistentId = UUID.randomUUID();
            NamespaceCreateBody body = new NamespaceCreateBody(
                    null, "environment-global",
                    "http://ns/nonexistent", "https://schemas.example.org/schema.xsd");

            when(namespaceRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> namespacesService.updateNamespace(
                    "test-domain", "test-solution", nonExistentId, body))
                    .isInstanceOf(SifBrokerException.class)
                    .satisfies(ex -> {
                        SifBrokerException sbe = (SifBrokerException) ex;
                        assertThat(sbe.getErrorCode()).isEqualTo("NAMESPACE_005");
                        assertThat(sbe.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    })
                    .hasMessageContaining(nonExistentId.toString());
        }

        @Test
        @DisplayName("DELETE on non-existent ID returns 404 with NAMESPACE_005")
        void deleteNamespace_returns404_whenIdNotFound() {
            stubDomainAndSolution();

            UUID nonExistentId = UUID.randomUUID();
            when(namespaceRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> namespacesService.deleteNamespace(
                    "test-domain", "test-solution", nonExistentId))
                    .isInstanceOf(SifBrokerException.class)
                    .satisfies(ex -> {
                        SifBrokerException sbe = (SifBrokerException) ex;
                        assertThat(sbe.getErrorCode()).isEqualTo("NAMESPACE_005");
                        assertThat(sbe.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    })
                    .hasMessageContaining(nonExistentId.toString());
        }
    }

    // ========== 11.6 Default Zone Resolution (Req 1.4) ==========

    @Nested
    @DisplayName("11.6 Default Zone Resolution")
    class DefaultZoneResolution {

        @Test
        @DisplayName("Zone-scoping uses provided zoneId when present")
        void listNamespaces_usesProvidedZoneId() {
            stubDomainAndSolution();

            Namespace globalNs = buildNamespaceEntity("http://ns/zone-test", "environment-global");
            Namespace zoneNs = buildNamespaceEntity("http://ns/zone-test", "district-1");

            when(namespaceRepository.findBySolutionUuid(solution.getId()))
                    .thenReturn(List.of(globalNs, zoneNs));

            List<NamespaceDTO> result = namespacesService.listNamespaces(
                    "test-domain", "test-solution", "district-1");

            // Zone-specific should override global (matched by URI)
            assertThat(result).hasSize(1);
            assertThat(result.get(0).zone()).isEqualTo("district-1");
        }

        @Test
        @DisplayName("Environment-global zone returns all namespaces without override")
        void listNamespaces_environmentGlobal_returnsAll() {
            stubDomainAndSolution();

            Namespace globalNs = buildNamespaceEntity("http://ns/zone-test", "environment-global");
            Namespace zoneNs = buildNamespaceEntity("http://ns/zone-test", "district-1");

            when(namespaceRepository.findBySolutionUuid(solution.getId()))
                    .thenReturn(List.of(globalNs, zoneNs));

            List<NamespaceDTO> result = namespacesService.listNamespaces(
                    "test-domain", "test-solution", "environment-global");

            // environment-global returns ALL without override
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Zone-scoping includes zone-only entries with no global counterpart")
        void listNamespaces_includesZoneOnlyEntries() {
            stubDomainAndSolution();

            Namespace globalNs = buildNamespaceEntity("http://ns/global-only", "environment-global");
            Namespace zoneOnlyNs = buildNamespaceEntity("http://ns/zone-only", "district-1");

            when(namespaceRepository.findBySolutionUuid(solution.getId()))
                    .thenReturn(List.of(globalNs, zoneOnlyNs));

            List<NamespaceDTO> result = namespacesService.listNamespaces(
                    "test-domain", "test-solution", "district-1");

            // Should include global (no override) + zone-only
            assertThat(result).hasSize(2);
            assertThat(result).extracting(NamespaceDTO::uri)
                    .containsExactlyInAnyOrder("http://ns/global-only", "http://ns/zone-only");
        }
    }

    // ========== 11.7 Update Duplicate Detection (Req 4.8) ==========

    @Nested
    @DisplayName("11.7 Update Duplicate Detection")
    class UpdateDuplicateDetection {

        @Test
        @DisplayName("Update that would create duplicate (uri, zone) returns 409")
        void updateNamespace_returns409_whenDuplicateWouldBeCreated() {
            stubDomainAndSolution();

            // Existing namespace being updated
            Namespace existing = buildNamespaceEntity("http://ns/original", "environment-global");
            UUID existingId = existing.getId();

            // Another namespace that already has the target uri+zone
            Namespace other = buildNamespaceEntity("http://ns/target", "environment-global");

            NamespaceCreateBody body = new NamespaceCreateBody(
                    null, "environment-global",
                    "http://ns/target", "https://schemas.example.org/schema.xsd");

            when(namespaceRepository.findById(existingId)).thenReturn(Optional.of(existing));
            when(namespaceRepository.findByUriAndZoneAndSolutionUuid(
                    "http://ns/target", "environment-global", solution.getId()))
                    .thenReturn(Optional.of(other));

            assertThatThrownBy(() -> namespacesService.updateNamespace(
                    "test-domain", "test-solution", existingId, body))
                    .isInstanceOf(SifBrokerException.class)
                    .satisfies(ex -> {
                        SifBrokerException sbe = (SifBrokerException) ex;
                        assertThat(sbe.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    });

            verify(namespaceRepository, never()).save(any());
        }

        @Test
        @DisplayName("Update to same uri+zone on self does not trigger 409")
        void updateNamespace_allowsSameUriZoneOnSelf() {
            stubDomainAndSolution();

            Namespace existing = buildNamespaceEntity("http://ns/self", "environment-global");
            UUID existingId = existing.getId();

            NamespaceCreateBody body = new NamespaceCreateBody(
                    null, "environment-global",
                    "http://ns/self", "https://schemas.example.org/updated.xsd");

            when(namespaceRepository.findById(existingId)).thenReturn(Optional.of(existing));
            when(namespaceRepository.findByUriAndZoneAndSolutionUuid(
                    "http://ns/self", "environment-global", solution.getId()))
                    .thenReturn(Optional.of(existing)); // same entity
            when(namespaceRepository.save(any(Namespace.class))).thenReturn(existing);

            doNothing().when(namespaceEventPublisher)
                    .publishNamespaceUpdatedEvent(any(), anyString(), anyString());

            NamespaceDTO result = namespacesService.updateNamespace(
                    "test-domain", "test-solution", existingId, body);

            assertThat(result).isNotNull();
            verify(namespaceRepository).save(any(Namespace.class));
        }
    }

    // ========== 11.8 Provided ID Ignored on Create (Req 3.6) ==========

    @Nested
    @DisplayName("11.8 Provided ID Ignored on Create")
    class ProvidedIdIgnoredOnCreate {

        @Test
        @DisplayName("Broker assigns UUID regardless of provided id")
        void createNamespace_ignoresProvidedId() {
            stubDomainAndSolution();
            stubProviderRegistered();

            String providedId = UUID.randomUUID().toString();
            NamespaceCreateBody body = new NamespaceCreateBody(
                    providedId, "environment-global",
                    "http://ns/id-test", "https://schemas.example.org/schema.xsd");

            when(namespaceRepository.findByUriAndZoneAndSolutionUuid(
                    body.uri(), body.zone(), solution.getId()))
                    .thenReturn(Optional.empty());

            UUID brokerAssignedId = UUID.randomUUID();
            Namespace saved = buildNamespaceEntity(body.uri(), body.zone());
            saved.setId(brokerAssignedId);
            when(namespaceRepository.save(any(Namespace.class))).thenReturn(saved);

            doNothing().when(namespaceEventPublisher)
                    .publishNamespaceCreatedEvent(any(), anyString(), anyString());

            NamespaceDTO result = namespacesService.createNamespace(
                    "test-domain", "test-solution", body);

            // Broker-assigned id should NOT equal provided id
            assertThat(result.id())
                    .as("Broker should assign its own UUID, ignoring provided id")
                    .isEqualTo(brokerAssignedId.toString())
                    .isNotEqualTo(providedId);
        }

        @Test
        @DisplayName("Create with null id gets broker-assigned UUID")
        void createNamespace_assignsUuid_whenIdIsNull() {
            stubDomainAndSolution();
            stubProviderRegistered();

            NamespaceCreateBody body = new NamespaceCreateBody(
                    null, "environment-global",
                    "http://ns/null-id-test", "https://schemas.example.org/schema.xsd");

            when(namespaceRepository.findByUriAndZoneAndSolutionUuid(
                    body.uri(), body.zone(), solution.getId()))
                    .thenReturn(Optional.empty());

            Namespace saved = buildNamespaceEntity(body.uri(), body.zone());
            when(namespaceRepository.save(any(Namespace.class))).thenReturn(saved);

            doNothing().when(namespaceEventPublisher)
                    .publishNamespaceCreatedEvent(any(), anyString(), anyString());

            NamespaceDTO result = namespacesService.createNamespace(
                    "test-domain", "test-solution", body);

            assertThat(result.id())
                    .as("Broker should assign a non-null UUID id")
                    .isNotNull()
                    .isNotBlank();

            // Verify it's a valid UUID
            UUID.fromString(result.id());
        }
    }
}
