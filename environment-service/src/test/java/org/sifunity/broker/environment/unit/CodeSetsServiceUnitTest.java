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
import org.sifunity.broker.common.constant.CodeItemAction;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.domain.CodeSet;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.*;
import org.sifunity.broker.environment.service.CodeSetEventPublisher;
import org.sifunity.broker.environment.service.CodeSetsService;

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
 * Unit tests for CodeSetsService edge cases.
 *
 * <p>Covers tasks 12.1 through 12.7:</p>
 * <ul>
 *   <li>12.1 — Event publication failure isolation (Req 6.6)</li>
 *   <li>12.2 — No-provider-registered error (Req 11.1)</li>
 *   <li>12.3 — Response wrapper format (Req 8.9, 8.10)</li>
 *   <li>12.4 — HTTP 204 when no code sets match query (Req 1.5)</li>
 *   <li>12.5 — 404 on non-existent ID (Req 2.2, 4.2, 5.2)</li>
 *   <li>12.6 — Default zone resolution (Req 1.4)</li>
 *   <li>12.7 — codeItem validation error messages (Req 13.5)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CodeSetsService Unit Tests")
class CodeSetsServiceUnitTest {

    @Mock
    private CodeSetRepository codeSetRepository;

    @Mock
    private ProviderRegistrationRepository providerRegistrationRepository;

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private ContextRepository contextRepository;

    @Mock
    private ContextServiceRepository contextServiceRepository;

    @Mock
    private CodeSetEventPublisher codeSetEventPublisher;

    private CodeSetsService codeSetsService;

    private Domain domain;
    private Solution solution;

    @BeforeEach
    void setUp() {
        codeSetsService = new CodeSetsService(
                codeSetRepository, providerRegistrationRepository,
                contextRepository, contextServiceRepository, zoneRepository, domainRepository, solutionRepository,
                codeSetEventPublisher, new SimpleMeterRegistry());

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

        org.sifunity.broker.environment.domain.ContextService codeSetsCs = new org.sifunity.broker.environment.domain.ContextService();
        codeSetsCs.setId(UUID.randomUUID());
        codeSetsCs.setContextUuid(defaultCtx.getId());
        codeSetsCs.setServiceName("codeSets");
        when(contextServiceRepository.findByContextUuidAndServiceName(defaultCtx.getId(), "codeSets"))
                .thenReturn(Optional.of(codeSetsCs));

        when(providerRegistrationRepository.findByContextServiceUuid(codeSetsCs.getId()))
                .thenReturn(Optional.of(org.sifunity.broker.environment.domain.ProviderRegistration.builder().id(UUID.randomUUID()).build()));
    }

    private CodeSet buildCodeSetEntity(String codeSetId, String zone) {
        CodeSet cs = new CodeSet();
        cs.setUuid(UUID.randomUUID());
        cs.setCodeSetId(codeSetId);
        cs.setZone(zone);
        cs.setVersion("v1.0");
        cs.setTimestamp("2024-01-01T00:00:00Z");
        cs.setSource("https://example.org");
        cs.setSolutionUuid(solution.getId());
        cs.setCreatedAt(Instant.now());
        cs.setUpdatedAt(Instant.now());
        return cs;
    }

    private CodeSetCreateBody validCreateBody(String id) {
        return new CodeSetCreateBody(
                id, "environment-global", "v1.0",
                "2024-01-01T00:00:00Z", null, null);
    }

    // ========== 12.1 Event Publication Failure Isolation (Req 6.6) ==========

    @Nested
    @DisplayName("12.1 Event Publication Failure Isolation")
    class EventPublicationFailureIsolation {

        @Test
        @DisplayName("Create returns successfully when event publication throws exception")
        void createCodeSet_succeeds_whenEventPublicationFails() {
            stubDomainAndSolution();
            stubProviderRegistered();

            CodeSetCreateBody body = validCreateBody("CS-EVENT-FAIL");

            when(codeSetRepository.findByCodeSetIdAndZoneAndSolutionUuid(
                    "CS-EVENT-FAIL", "environment-global", solution.getId()))
                    .thenReturn(Optional.empty());

            CodeSet saved = buildCodeSetEntity("CS-EVENT-FAIL", "environment-global");
            when(codeSetRepository.save(any(CodeSet.class))).thenReturn(saved);

            doThrow(new RuntimeException("Events connector unavailable"))
                    .when(codeSetEventPublisher)
                    .publishCodeSetCreatedEvent(any(CodeSetDTO.class), anyString(), anyString());

            CodeSetDTO result = codeSetsService.createCodeSet("test-domain", "test-solution", body);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo("CS-EVENT-FAIL");
            verify(codeSetRepository).save(any(CodeSet.class));
            verify(codeSetEventPublisher).publishCodeSetCreatedEvent(
                    any(CodeSetDTO.class), eq("test-domain"), eq("test-solution"));
        }

        @Test
        @DisplayName("Update returns successfully when event publication throws exception")
        void updateCodeSet_succeeds_whenEventPublicationFails() {
            stubDomainAndSolution();

            CodeSetCreateBody body = new CodeSetCreateBody(
                    "CS-UPD-EVT", "environment-global", "v2.0",
                    "2024-06-01T00:00:00Z", null, null);

            CodeSet existing = buildCodeSetEntity("CS-UPD-EVT", "environment-global");
            when(codeSetRepository.findByCodeSetIdAndZoneAndSolutionUuid(
                    "CS-UPD-EVT", "environment-global", solution.getId()))
                    .thenReturn(Optional.of(existing));
            when(codeSetRepository.save(any(CodeSet.class))).thenReturn(existing);

            doThrow(new RuntimeException("Events connector unavailable"))
                    .when(codeSetEventPublisher)
                    .publishCodeSetUpdatedEvent(any(CodeSetDTO.class), anyString(), anyString());

            CodeSetDTO result = codeSetsService.updateCodeSet(
                    "test-domain", "test-solution", "CS-UPD-EVT", body);

            assertThat(result).isNotNull();
            verify(codeSetRepository).save(any(CodeSet.class));
        }

        @Test
        @DisplayName("Delete returns successfully when event publication throws exception")
        void deleteCodeSet_succeeds_whenEventPublicationFails() {
            stubDomainAndSolution();

            CodeSet existing = buildCodeSetEntity("CS-DEL-EVT", "environment-global");
            when(codeSetRepository.findBySolutionUuid(solution.getId()))
                    .thenReturn(List.of(existing));

            doThrow(new RuntimeException("Events connector unavailable"))
                    .when(codeSetEventPublisher)
                    .publishCodeSetDeletedEvent(any(CodeSetDTO.class), anyString(), anyString());

            // Should not throw
            codeSetsService.deleteCodeSet("test-domain", "test-solution", "CS-DEL-EVT");

            verify(codeSetRepository).delete(existing);
            verify(codeSetEventPublisher).publishCodeSetDeletedEvent(
                    any(CodeSetDTO.class), eq("test-domain"), eq("test-solution"));
        }
    }

    // ========== 12.2 No-Provider-Registered Error (Req 11.1) ==========

    @Nested
    @DisplayName("12.2 No Provider Registered Error")
    class NoProviderRegisteredError {

        @Test
        @DisplayName("Returns 404 with CODESET_007 when no codeSets provider registered")
        void createCodeSet_returns404_whenNoProviderRegistered() {
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

            org.sifunity.broker.environment.domain.ContextService codeSetsCs = new org.sifunity.broker.environment.domain.ContextService();
            codeSetsCs.setId(UUID.randomUUID());
            codeSetsCs.setContextUuid(defaultCtx.getId());
            codeSetsCs.setServiceName("codeSets");
            when(contextServiceRepository.findByContextUuidAndServiceName(defaultCtx.getId(), "codeSets"))
                    .thenReturn(Optional.of(codeSetsCs));

            when(providerRegistrationRepository.findByContextServiceUuid(codeSetsCs.getId()))
                    .thenReturn(Optional.empty());

            CodeSetCreateBody body = validCreateBody("CS-NO-PROV");

            assertThatThrownBy(() -> codeSetsService.createCodeSet(
                    "test-domain", "test-solution", body))
                    .isInstanceOf(SifBrokerException.class)
                    .satisfies(ex -> {
                        SifBrokerException sbe = (SifBrokerException) ex;
                        assertThat(sbe.getErrorCode()).isEqualTo("CODESET_007");
                        assertThat(sbe.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    });

            verify(codeSetRepository, never()).save(any());
        }

        @Test
        @DisplayName("Error message mentions codeSets and environment-global")
        void createCodeSet_errorMessage_mentionsProviderAndZone() {
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

            org.sifunity.broker.environment.domain.ContextService codeSetsCs = new org.sifunity.broker.environment.domain.ContextService();
            codeSetsCs.setId(UUID.randomUUID());
            codeSetsCs.setContextUuid(defaultCtx.getId());
            codeSetsCs.setServiceName("codeSets");
            when(contextServiceRepository.findByContextUuidAndServiceName(defaultCtx.getId(), "codeSets"))
                    .thenReturn(Optional.of(codeSetsCs));

            when(providerRegistrationRepository.findByContextServiceUuid(codeSetsCs.getId()))
                    .thenReturn(Optional.empty());

            CodeSetCreateBody body = validCreateBody("CS-NO-PROV-MSG");

            assertThatThrownBy(() -> codeSetsService.createCodeSet(
                    "test-domain", "test-solution", body))
                    .isInstanceOf(SifBrokerException.class)
                    .hasMessageContaining("No provider registered")
                    .hasMessageContaining("codeSets")
                    .hasMessageContaining("environment-global");
        }
    }

    // ========== 12.3 Response Wrapper Format (Req 8.9, 8.10) ==========

    @Nested
    @DisplayName("12.3 Response Wrapper Format")
    class ResponseWrapperFormat {

        private ObjectMapper objectMapper;

        @BeforeEach
        void setUpMapper() {
            objectMapper = new ObjectMapper();
            objectMapper.findAndRegisterModules();
        }

        @Test
        @DisplayName("Single code set wrapped under 'codeSet' key")
        void singleCodeSet_wrappedUnderCodeSetKey() throws Exception {
            CodeSetDTO dto = new CodeSetDTO(
                    "NCES_Race", "environment-global", "v1.0",
                    "2024-01-01T00:00:00Z", "https://nces.ed.gov", null);

            CodeSetResponse response = new CodeSetResponse(dto);
            String json = objectMapper.writeValueAsString(response);
            JsonNode root = objectMapper.readTree(json);

            assertThat(root.has("codeSet")).isTrue();
            assertThat(root.size()).isEqualTo(1);

            JsonNode codeSetNode = root.get("codeSet");
            assertThat(codeSetNode.get("id").asText()).isEqualTo("NCES_Race");
            assertThat(codeSetNode.get("zone").asText()).isEqualTo("environment-global");
            assertThat(codeSetNode.get("version").asText()).isEqualTo("v1.0");
        }

        @Test
        @DisplayName("Collection wrapped under 'codeSets.codeSet' array")
        void collection_wrappedUnderCodeSetsCodeSetArray() throws Exception {
            CodeSetDTO dto1 = new CodeSetDTO(
                    "NCES_Race", "environment-global", "v1.0",
                    "2024-01-01T00:00:00Z", null, null);
            CodeSetDTO dto2 = new CodeSetDTO(
                    "NCES_Gender", "environment-global", "v2.0",
                    "2024-06-01T00:00:00Z", null, null);

            CodeSetCollectionResponse response = new CodeSetCollectionResponse(
                    new CodeSetCollection(List.of(dto1, dto2)));

            String json = objectMapper.writeValueAsString(response);
            JsonNode root = objectMapper.readTree(json);

            assertThat(root.has("codeSets")).isTrue();
            assertThat(root.size()).isEqualTo(1);

            JsonNode codeSetsNode = root.get("codeSets");
            assertThat(codeSetsNode.has("codeSet")).isTrue();
            assertThat(codeSetsNode.size()).isEqualTo(1);

            JsonNode codeSetArray = codeSetsNode.get("codeSet");
            assertThat(codeSetArray.isArray()).isTrue();
            assertThat(codeSetArray.size()).isEqualTo(2);

            assertThat(codeSetArray.get(0).get("id").asText()).isEqualTo("NCES_Race");
            assertThat(codeSetArray.get(1).get("id").asText()).isEqualTo("NCES_Gender");
        }

        @Test
        @DisplayName("Empty collection has correct 'codeSets.codeSet' structure")
        void emptyCollection_hasCorrectStructure() throws Exception {
            CodeSetCollectionResponse response = new CodeSetCollectionResponse(
                    new CodeSetCollection(List.of()));

            String json = objectMapper.writeValueAsString(response);
            JsonNode root = objectMapper.readTree(json);

            assertThat(root.has("codeSets")).isTrue();
            JsonNode codeSetsNode = root.get("codeSets");
            assertThat(codeSetsNode.has("codeSet")).isTrue();
            assertThat(codeSetsNode.get("codeSet").isArray()).isTrue();
            assertThat(codeSetsNode.get("codeSet").size()).isEqualTo(0);
        }
    }

    // ========== 12.4 HTTP 204 When No Code Sets Match Query (Req 1.5) ==========

    @Nested
    @DisplayName("12.4 HTTP 204 When No Code Sets Match Query")
    class NoCodeSetsMatchQuery {

        @Test
        @DisplayName("listCodeSets returns empty list when no code sets exist")
        void listCodeSets_returnsEmptyList_whenNoCodeSetsExist() {
            stubDomainAndSolution();

            when(codeSetRepository.findBySolutionUuid(solution.getId()))
                    .thenReturn(List.of());

            List<CodeSetDTO> result = codeSetsService.listCodeSets(
                    "test-domain", "test-solution", "environment-global");

            assertThat(result).isEmpty();
        }
    }

    // ========== 12.5 404 on Non-Existent ID (Req 2.2, 4.2, 5.2) ==========

    @Nested
    @DisplayName("12.5 404 on Non-Existent ID")
    class NotFoundOnNonExistentId {

        @Test
        @DisplayName("GET by non-existent ID returns 404 with CODESET_006")
        void getCodeSetById_returns404_whenIdNotFound() {
            stubDomainAndSolution();

            when(codeSetRepository.findByCodeSetIdAndZoneAndSolutionUuid(
                    "NONEXISTENT", "environment-global", solution.getId()))
                    .thenReturn(Optional.empty());
            when(codeSetRepository.findBySolutionUuid(solution.getId()))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> codeSetsService.getCodeSetById(
                    "test-domain", "test-solution", "NONEXISTENT", "environment-global"))
                    .isInstanceOf(SifBrokerException.class)
                    .satisfies(ex -> {
                        SifBrokerException sbe = (SifBrokerException) ex;
                        assertThat(sbe.getErrorCode()).isEqualTo("CODESET_006");
                        assertThat(sbe.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    })
                    .hasMessageContaining("NONEXISTENT");
        }

        @Test
        @DisplayName("PUT on non-existent ID returns 404 with CODESET_006")
        void updateCodeSet_returns404_whenIdNotFound() {
            stubDomainAndSolution();

            CodeSetCreateBody body = new CodeSetCreateBody(
                    "NONEXISTENT", "environment-global", "v1.0",
                    "2024-01-01T00:00:00Z", null, null);

            when(codeSetRepository.findByCodeSetIdAndZoneAndSolutionUuid(
                    "NONEXISTENT", "environment-global", solution.getId()))
                    .thenReturn(Optional.empty());
            when(codeSetRepository.findBySolutionUuid(solution.getId()))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> codeSetsService.updateCodeSet(
                    "test-domain", "test-solution", "NONEXISTENT", body))
                    .isInstanceOf(SifBrokerException.class)
                    .satisfies(ex -> {
                        SifBrokerException sbe = (SifBrokerException) ex;
                        assertThat(sbe.getErrorCode()).isEqualTo("CODESET_006");
                        assertThat(sbe.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    })
                    .hasMessageContaining("NONEXISTENT");
        }

        @Test
        @DisplayName("DELETE on non-existent ID returns 404 with CODESET_006")
        void deleteCodeSet_returns404_whenIdNotFound() {
            stubDomainAndSolution();

            when(codeSetRepository.findBySolutionUuid(solution.getId()))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> codeSetsService.deleteCodeSet(
                    "test-domain", "test-solution", "NONEXISTENT"))
                    .isInstanceOf(SifBrokerException.class)
                    .satisfies(ex -> {
                        SifBrokerException sbe = (SifBrokerException) ex;
                        assertThat(sbe.getErrorCode()).isEqualTo("CODESET_006");
                        assertThat(sbe.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    })
                    .hasMessageContaining("NONEXISTENT");
        }
    }

    // ========== 12.6 Default Zone Resolution (Req 1.4) ==========

    @Nested
    @DisplayName("12.6 Default Zone Resolution")
    class DefaultZoneResolution {

        @Test
        @DisplayName("Zone-scoping uses provided zoneId when present")
        void listCodeSets_usesProvidedZoneId() {
            stubDomainAndSolution();

            CodeSet globalCs = buildCodeSetEntity("CS-ZONE", "environment-global");
            CodeSet zoneCs = buildCodeSetEntity("CS-ZONE", "district-1");

            when(codeSetRepository.findBySolutionUuid(solution.getId()))
                    .thenReturn(List.of(globalCs, zoneCs));

            List<CodeSetDTO> result = codeSetsService.listCodeSets(
                    "test-domain", "test-solution", "district-1");

            // Zone-specific should override global
            assertThat(result).hasSize(1);
            assertThat(result.get(0).zone()).isEqualTo("district-1");
        }

        @Test
        @DisplayName("Environment-global zone returns all code sets without override")
        void listCodeSets_environmentGlobal_returnsAll() {
            stubDomainAndSolution();

            CodeSet globalCs = buildCodeSetEntity("CS-ZONE", "environment-global");
            CodeSet zoneCs = buildCodeSetEntity("CS-ZONE", "district-1");

            when(codeSetRepository.findBySolutionUuid(solution.getId()))
                    .thenReturn(List.of(globalCs, zoneCs));

            List<CodeSetDTO> result = codeSetsService.listCodeSets(
                    "test-domain", "test-solution", "environment-global");

            // environment-global returns ALL without override
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Zone-scoping includes zone-only entries with no global counterpart")
        void listCodeSets_includesZoneOnlyEntries() {
            stubDomainAndSolution();

            CodeSet globalCs = buildCodeSetEntity("CS-GLOBAL-ONLY", "environment-global");
            CodeSet zoneOnlyCs = buildCodeSetEntity("CS-ZONE-ONLY", "district-1");

            when(codeSetRepository.findBySolutionUuid(solution.getId()))
                    .thenReturn(List.of(globalCs, zoneOnlyCs));

            List<CodeSetDTO> result = codeSetsService.listCodeSets(
                    "test-domain", "test-solution", "district-1");

            // Should include global (no override) + zone-only
            assertThat(result).hasSize(2);
            assertThat(result).extracting(CodeSetDTO::id)
                    .containsExactlyInAnyOrder("CS-GLOBAL-ONLY", "CS-ZONE-ONLY");
        }
    }

    // ========== 12.7 CodeItem Validation Error Messages (Req 13.5) ==========

    @Nested
    @DisplayName("12.7 CodeItem Validation Error Messages")
    class CodeItemValidationErrorMessages {

        @Test
        @DisplayName("Error message identifies invalid code item at index 0")
        void validationError_identifiesIndex0() {
            // Validation runs before domain/solution resolution — no stubs needed

            CodeItemDTO invalidItem = new CodeItemDTO(
                    null, null, null, "Valid Value", null, null, null,
                    CodeItemAction.ADD, "2024-01-01T00:00:00Z");

            CodeSetCreateBody body = new CodeSetCreateBody(
                    "CS-VAL-IDX0", "environment-global", "v1.0",
                    "2024-01-01T00:00:00Z", null,
                    new CodeItemsDTO(List.of(invalidItem)));

            assertThatThrownBy(() -> codeSetsService.createCodeSet(
                    "test-domain", "test-solution", body))
                    .isInstanceOf(SifBrokerException.class)
                    .hasMessageContaining("index 0")
                    .hasMessageContaining("code");
        }

        @Test
        @DisplayName("Error message identifies invalid code item at index 2")
        void validationError_identifiesIndex2() {
            // Validation runs before domain/solution resolution — no stubs needed

            CodeItemDTO validItem1 = new CodeItemDTO(
                    "C1", null, null, "Value1", null, null, null,
                    CodeItemAction.ADD, "2024-01-01T00:00:00Z");
            CodeItemDTO validItem2 = new CodeItemDTO(
                    "C2", null, null, "Value2", null, null, null,
                    CodeItemAction.CHANGE, "2024-01-01T00:00:00Z");
            CodeItemDTO invalidItem = new CodeItemDTO(
                    "C3", null, null, null, null, null, null,
                    CodeItemAction.DELETE, "2024-01-01T00:00:00Z");

            CodeSetCreateBody body = new CodeSetCreateBody(
                    "CS-VAL-IDX2", "environment-global", "v1.0",
                    "2024-01-01T00:00:00Z", null,
                    new CodeItemsDTO(List.of(validItem1, validItem2, invalidItem)));

            assertThatThrownBy(() -> codeSetsService.createCodeSet(
                    "test-domain", "test-solution", body))
                    .isInstanceOf(SifBrokerException.class)
                    .hasMessageContaining("index 2")
                    .hasMessageContaining("value");
        }

        @Test
        @DisplayName("Error message identifies missing action field with index")
        void validationError_identifiesMissingAction() {
            // Validation runs before domain/solution resolution — no stubs needed

            CodeItemDTO validItem = new CodeItemDTO(
                    "C1", null, null, "Value1", null, null, null,
                    CodeItemAction.ADD, "2024-01-01T00:00:00Z");
            CodeItemDTO invalidItem = new CodeItemDTO(
                    "C2", null, null, "Value2", null, null, null,
                    null, "2024-01-01T00:00:00Z");

            CodeSetCreateBody body = new CodeSetCreateBody(
                    "CS-VAL-ACTION", "environment-global", "v1.0",
                    "2024-01-01T00:00:00Z", null,
                    new CodeItemsDTO(List.of(validItem, invalidItem)));

            assertThatThrownBy(() -> codeSetsService.createCodeSet(
                    "test-domain", "test-solution", body))
                    .isInstanceOf(SifBrokerException.class)
                    .hasMessageContaining("index 1")
                    .hasMessageContaining("action");
        }

        @Test
        @DisplayName("Error message identifies missing timestamp field with index")
        void validationError_identifiesMissingTimestamp() {
            // Validation runs before domain/solution resolution — no stubs needed

            CodeItemDTO invalidItem = new CodeItemDTO(
                    "C1", null, null, "Value1", null, null, null,
                    CodeItemAction.ADD, null);

            CodeSetCreateBody body = new CodeSetCreateBody(
                    "CS-VAL-TS", "environment-global", "v1.0",
                    "2024-01-01T00:00:00Z", null,
                    new CodeItemsDTO(List.of(invalidItem)));

            assertThatThrownBy(() -> codeSetsService.createCodeSet(
                    "test-domain", "test-solution", body))
                    .isInstanceOf(SifBrokerException.class)
                    .hasMessageContaining("index 0")
                    .hasMessageContaining("timestamp");
        }
    }
}
