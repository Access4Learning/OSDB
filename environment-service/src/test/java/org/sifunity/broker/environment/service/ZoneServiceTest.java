// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.ContextCreateRequest;
import org.sifunity.broker.environment.dto.ContextDTO;
import org.sifunity.broker.environment.dto.ZoneCreateRequest;
import org.sifunity.broker.environment.dto.ZoneDTO;
import org.sifunity.broker.environment.exception.DuplicateContextException;
import org.sifunity.broker.environment.exception.DuplicateZoneException;
import org.sifunity.broker.environment.exception.SolutionNotFoundException;
import org.sifunity.broker.environment.exception.ZoneNotFoundException;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ZoneService.
 * 
 * <p>Tests the business logic for zone and context management operations
 * including creation, retrieval, validation, and error handling.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ZoneService")
class ZoneServiceTest {
    
    @Mock
    private ZoneRepository zoneRepository;
    
    @Mock
    private ContextRepository contextRepository;
    
    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private DomainRepository domainRepository;

    private ZoneService zoneService;

    private static final UUID SOLUTION_UUID = UUID.randomUUID();
    private static final UUID ZONE_UUID = UUID.randomUUID();
    private static final String TEST_SOLUTION_ID = "test-solution";
    private static final String DOMAIN_ID = "test-domain";
    private static final UUID DOMAIN_UUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        zoneService = new ZoneService(zoneRepository, contextRepository, solutionRepository, domainRepository);
    }

    private Solution createTestSolution(String solutionId) {
        Solution solution = new Solution();
        solution.setId(SOLUTION_UUID);
        solution.setSolutionId(solutionId);
        solution.setDomainUuid(DOMAIN_UUID);
        return solution;
    }

    private Domain createTestDomain() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_UUID);
        domain.setDomainId(DOMAIN_ID);
        return domain;
    }
    
    @Nested
    @DisplayName("Zone Creation")
    class ZoneCreationTests {
        
        @Test
        @DisplayName("Should create zone successfully with valid request")
        void shouldCreateZoneSuccessfully() {
            ZoneCreateRequest request = new ZoneCreateRequest("test-solution", "test-zone", "Test Zone Description");
            Zone savedZone = createTestZone("test-zone", "Test Zone Description");
            Solution solution = createTestSolution("test-solution");

            when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(createTestDomain()));
            when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, "test-solution")).thenReturn(Optional.of(solution));
            when(zoneRepository.existsBySolutionUuidAndZoneId(SOLUTION_UUID, "test-zone")).thenReturn(false);
            when(zoneRepository.save(any(Zone.class))).thenReturn(savedZone);
            when(contextRepository.existsByZoneUuidAndContextId(savedZone.getId(), "DEFAULT")).thenReturn(false);
            when(contextRepository.save(any(Context.class))).thenAnswer(inv -> inv.getArgument(0));

            ZoneDTO result = zoneService.createZone(DOMAIN_ID, request);
            
            assertThat(result).isNotNull();
            assertThat(result.zoneId()).isEqualTo("test-zone");
            assertThat(result.description()).isEqualTo("Test Zone Description");
            assertThat(result.id()).isEqualTo(savedZone.getId());
            // createdAt is populated internally; suppressed from JSON by @JsonIgnore on accessor
            assertThat(result.createdAt()).isNotNull();
            
            verify(zoneRepository).existsBySolutionUuidAndZoneId(SOLUTION_UUID, "test-zone");
            verify(zoneRepository).save(any(Zone.class));
        }
        
        @Test
        @DisplayName("Should create zone with null description")
        void shouldCreateZoneWithNullDescription() {
            ZoneCreateRequest request = new ZoneCreateRequest("test-solution", "test-zone", null);
            Zone savedZone = createTestZone("test-zone", null);
            Solution solution = createTestSolution("test-solution");

            when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(createTestDomain()));
            when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, "test-solution")).thenReturn(Optional.of(solution));
            when(zoneRepository.existsBySolutionUuidAndZoneId(SOLUTION_UUID, "test-zone")).thenReturn(false);
            when(zoneRepository.save(any(Zone.class))).thenReturn(savedZone);
            when(contextRepository.existsByZoneUuidAndContextId(savedZone.getId(), "DEFAULT")).thenReturn(false);
            when(contextRepository.save(any(Context.class))).thenAnswer(inv -> inv.getArgument(0));

            ZoneDTO result = zoneService.createZone(DOMAIN_ID, request);
            
            assertThat(result).isNotNull();
            assertThat(result.zoneId()).isEqualTo("test-zone");
            assertThat(result.description()).isNull();
        }
        
        @Test
        @DisplayName("Should throw DuplicateZoneException when zone ID already exists")
        void shouldThrowDuplicateZoneExceptionWhenZoneExists() {
            ZoneCreateRequest request = new ZoneCreateRequest("test-solution", "existing-zone", "Description");
            Solution solution = createTestSolution("test-solution");

            when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(createTestDomain()));
            when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, "test-solution")).thenReturn(Optional.of(solution));
            when(zoneRepository.existsBySolutionUuidAndZoneId(SOLUTION_UUID, "existing-zone")).thenReturn(true);

            assertThatThrownBy(() -> zoneService.createZone(DOMAIN_ID, request))
                .isInstanceOf(DuplicateZoneException.class)
                .hasMessageContaining("existing-zone");
            
            verify(zoneRepository).existsBySolutionUuidAndZoneId(SOLUTION_UUID, "existing-zone");
            verify(zoneRepository, never()).save(any(Zone.class));
        }
        
        @Test
        @DisplayName("Should throw SolutionNotFoundException when solution does not exist")
        void shouldThrowSolutionNotFoundExceptionWhenSolutionNotExists() {
            ZoneCreateRequest request = new ZoneCreateRequest("non-existent", "test-zone", "Description");

            when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(createTestDomain()));
            when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, "non-existent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> zoneService.createZone(DOMAIN_ID, request))
                .isInstanceOf(SolutionNotFoundException.class);
            
            verify(zoneRepository, never()).save(any(Zone.class));
        }
    }
    
    @Nested
    @DisplayName("Zone Listing")
    class ZoneListingTests {
        
        @Test
        @DisplayName("Should return all zones")
        void shouldReturnAllZones() {
            List<Zone> zones = List.of(
                createTestZone("zone1", "Zone 1"),
                createTestZone("zone2", "Zone 2")
            );
            
            when(zoneRepository.findAll()).thenReturn(zones);
            
            List<ZoneDTO> result = zoneService.listZones();
            
            assertThat(result).hasSize(2);
            assertThat(result.get(0).zoneId()).isEqualTo("zone1");
            assertThat(result.get(1).zoneId()).isEqualTo("zone2");
            
            verify(zoneRepository).findAll();
        }
        
        @Test
        @DisplayName("Should return empty list when no zones exist")
        void shouldReturnEmptyListWhenNoZones() {
            when(zoneRepository.findAll()).thenReturn(List.of());
            
            List<ZoneDTO> result = zoneService.listZones();
            
            assertThat(result).isEmpty();
            verify(zoneRepository).findAll();
        }
    }
    
    @Nested
    @DisplayName("Context Creation")
    class ContextCreationTests {
        
        @Test
        @DisplayName("Should create context successfully with valid request")
        void shouldCreateContextSuccessfully() {
            String zoneId = "test-zone";
            ContextCreateRequest request = new ContextCreateRequest("test-context", "Test Context Description");
            Zone zone = createTestZone(zoneId, "Test Zone");
            Context savedContext = createTestContext(zone.getId(), "test-context", "Test Context Description");
            Solution solution = createTestSolution(TEST_SOLUTION_ID);

            when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(createTestDomain()));
            when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, TEST_SOLUTION_ID)).thenReturn(Optional.of(solution));
            when(zoneRepository.findBySolutionUuidAndZoneId(SOLUTION_UUID, zoneId)).thenReturn(Optional.of(zone));
            when(contextRepository.existsByZoneUuidAndContextId(zone.getId(), "test-context")).thenReturn(false);
            when(contextRepository.save(any(Context.class))).thenReturn(savedContext);

            ContextDTO result = zoneService.createContext(DOMAIN_ID, TEST_SOLUTION_ID, zoneId, request);

            assertThat(result).isNotNull();
            assertThat(result.zoneId()).isEqualTo(zoneId);
            assertThat(result.contextId()).isEqualTo("test-context");
            assertThat(result.description()).isEqualTo("Test Context Description");
            assertThat(result.id()).isEqualTo(savedContext.getId());
            // createdAt is populated internally; suppressed from JSON by @JsonIgnore on accessor
            assertThat(result.createdAt()).isNotNull();

            verify(solutionRepository).findByDomainUuidAndSolutionId(DOMAIN_UUID, TEST_SOLUTION_ID);
            verify(zoneRepository).findBySolutionUuidAndZoneId(SOLUTION_UUID, zoneId);
            verify(contextRepository).existsByZoneUuidAndContextId(zone.getId(), "test-context");
            verify(contextRepository).save(any(Context.class));
        }
        
        @Test
        @DisplayName("Should throw ZoneNotFoundException when parent zone does not exist")
        void shouldThrowZoneNotFoundExceptionWhenParentZoneNotExists() {
            String zoneId = "non-existent-zone";
            ContextCreateRequest request = new ContextCreateRequest("test-context", "Description");
            Solution solution = createTestSolution(TEST_SOLUTION_ID);

            when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(createTestDomain()));
            when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, TEST_SOLUTION_ID)).thenReturn(Optional.of(solution));
            when(zoneRepository.findBySolutionUuidAndZoneId(SOLUTION_UUID, zoneId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> zoneService.createContext(DOMAIN_ID, TEST_SOLUTION_ID, zoneId, request))
                .isInstanceOf(ZoneNotFoundException.class)
                .hasMessageContaining(zoneId);

            verify(solutionRepository).findByDomainUuidAndSolutionId(DOMAIN_UUID, TEST_SOLUTION_ID);
            verify(zoneRepository).findBySolutionUuidAndZoneId(SOLUTION_UUID, zoneId);
            verify(contextRepository, never()).existsByZoneUuidAndContextId(any(), anyString());
            verify(contextRepository, never()).save(any(Context.class));
        }
        
        @Test
        @DisplayName("Should throw DuplicateContextException when context already exists in zone")
        void shouldThrowDuplicateContextExceptionWhenContextExists() {
            String zoneId = "test-zone";
            ContextCreateRequest request = new ContextCreateRequest("existing-context", "Description");
            Zone zone = createTestZone(zoneId, "Test Zone");
            Solution solution = createTestSolution(TEST_SOLUTION_ID);
            
            when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(createTestDomain()));
            when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, TEST_SOLUTION_ID)).thenReturn(Optional.of(solution));
            when(zoneRepository.findBySolutionUuidAndZoneId(SOLUTION_UUID, zoneId)).thenReturn(Optional.of(zone));
            when(contextRepository.existsByZoneUuidAndContextId(zone.getId(), "existing-context")).thenReturn(true);

            assertThatThrownBy(() -> zoneService.createContext(DOMAIN_ID, TEST_SOLUTION_ID, zoneId, request))
                .isInstanceOf(DuplicateContextException.class)
                .hasMessageContaining("existing-context")
                .hasMessageContaining(zoneId);

            verify(solutionRepository).findByDomainUuidAndSolutionId(DOMAIN_UUID, TEST_SOLUTION_ID);
            verify(zoneRepository).findBySolutionUuidAndZoneId(SOLUTION_UUID, zoneId);
            verify(contextRepository).existsByZoneUuidAndContextId(zone.getId(), "existing-context");
            verify(contextRepository, never()).save(any(Context.class));
        }
    }
    
    @Nested
    @DisplayName("Context Listing")
    class ContextListingTests {
        
        @Test
        @DisplayName("Should return all contexts for zone")
        void shouldReturnAllContextsForZone() {
            String zoneId = "test-zone";
            Zone zone = createTestZone(zoneId, "Test Zone");
            List<Context> contexts = List.of(
                createTestContext(zone.getId(), "context1", "Context 1"),
                createTestContext(zone.getId(), "context2", "Context 2")
            );
            Solution solution = createTestSolution(TEST_SOLUTION_ID);

            when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(createTestDomain()));
            when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, TEST_SOLUTION_ID)).thenReturn(Optional.of(solution));
            when(zoneRepository.findBySolutionUuidAndZoneId(SOLUTION_UUID, zoneId)).thenReturn(Optional.of(zone));
            when(contextRepository.findByZoneUuid(zone.getId())).thenReturn(contexts);

            List<ContextDTO> result = zoneService.listContexts(DOMAIN_ID, TEST_SOLUTION_ID, zoneId);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).contextId()).isEqualTo("context1");
            assertThat(result.get(1).contextId()).isEqualTo("context2");
            assertThat(result.get(0).zoneId()).isEqualTo(zoneId);
            assertThat(result.get(1).zoneId()).isEqualTo(zoneId);
            
            verify(contextRepository).findByZoneUuid(zone.getId());
        }
        
        @Test
        @DisplayName("Should return empty list when no contexts exist in zone")
        void shouldReturnEmptyListWhenNoContextsInZone() {
            String zoneId = "test-zone";
            Zone zone = createTestZone(zoneId, "Test Zone");
            Solution solution = createTestSolution(TEST_SOLUTION_ID);
            when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(createTestDomain()));
            when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, TEST_SOLUTION_ID)).thenReturn(Optional.of(solution));
            when(zoneRepository.findBySolutionUuidAndZoneId(SOLUTION_UUID, zoneId)).thenReturn(Optional.of(zone));
            when(contextRepository.findByZoneUuid(zone.getId())).thenReturn(List.of());

            List<ContextDTO> result = zoneService.listContexts(DOMAIN_ID, TEST_SOLUTION_ID, zoneId);

            assertThat(result).isEmpty();
            verify(contextRepository).findByZoneUuid(zone.getId());
        }
    }
    
    @Nested
    @DisplayName("Validation Methods")
    class ValidationMethodsTests {
        
        @Test
        @DisplayName("Should return true when zone exists")
        void shouldReturnTrueWhenZoneExists() {
            String zoneId = "existing-zone";
            when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(createTestDomain()));
            when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, "test-solution")).thenReturn(Optional.of(createTestSolution("test-solution")));
            when(zoneRepository.existsBySolutionUuidAndZoneId(any(), eq(zoneId))).thenReturn(true);

            boolean result = zoneService.zoneExists(DOMAIN_ID, "test-solution", zoneId);

            assertThat(result).isTrue();
            verify(zoneRepository).existsBySolutionUuidAndZoneId(any(), eq(zoneId));
        }
        
        @Test
        @DisplayName("Should return false when zone does not exist")
        void shouldReturnFalseWhenZoneNotExists() {
            String zoneId = "non-existent-zone";
            when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(createTestDomain()));
            when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, "test-solution")).thenReturn(Optional.of(createTestSolution("test-solution")));
            when(zoneRepository.existsBySolutionUuidAndZoneId(any(), eq(zoneId))).thenReturn(false);

            boolean result = zoneService.zoneExists(DOMAIN_ID, "test-solution", zoneId);

            assertThat(result).isFalse();
            verify(zoneRepository).existsBySolutionUuidAndZoneId(any(), eq(zoneId));
        }
        
        @Test
        @DisplayName("Should return true when context exists")
        void shouldReturnTrueWhenContextExists() {
            String zoneId = "test-zone";
            String contextId = "existing-context";
            Zone zone = createTestZone(zoneId, "Test Zone");
            Solution solution = createTestSolution(TEST_SOLUTION_ID);
            when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(createTestDomain()));
            when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, TEST_SOLUTION_ID)).thenReturn(Optional.of(solution));
            when(zoneRepository.findBySolutionUuidAndZoneId(SOLUTION_UUID, zoneId)).thenReturn(Optional.of(zone));
            when(contextRepository.existsByZoneUuidAndContextId(zone.getId(), contextId)).thenReturn(true);

            boolean result = zoneService.contextExists(DOMAIN_ID, TEST_SOLUTION_ID, zoneId, contextId);

            assertThat(result).isTrue();
        }
        
        @Test
        @DisplayName("Should return false when context does not exist")
        void shouldReturnFalseWhenContextNotExists() {
            String zoneId = "test-zone";
            String contextId = "non-existent-context";
            Zone zone = createTestZone(zoneId, "Test Zone");
            Solution solution = createTestSolution(TEST_SOLUTION_ID);
            when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(createTestDomain()));
            when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, TEST_SOLUTION_ID)).thenReturn(Optional.of(solution));
            when(zoneRepository.findBySolutionUuidAndZoneId(SOLUTION_UUID, zoneId)).thenReturn(Optional.of(zone));
            when(contextRepository.existsByZoneUuidAndContextId(zone.getId(), contextId)).thenReturn(false);

            boolean result = zoneService.contextExists(DOMAIN_ID, TEST_SOLUTION_ID, zoneId, contextId);

            assertThat(result).isFalse();
        }
    }
    
    // Helper methods
    
    private Zone createTestZone(String zoneId, String description) {
        Zone zone = new Zone();
        zone.setId(ZONE_UUID);
        zone.setSolutionUuid(SOLUTION_UUID);
        zone.setZoneId(zoneId);
        zone.setDescription(description);
        zone.setCreatedAt(Instant.now());
        return zone;
    }
    
    private Context createTestContext(UUID zoneUuid, String contextId, String description) {
        Context context = new Context();
        context.setId(UUID.randomUUID());
        context.setZoneUuid(zoneUuid);
        context.setContextId(contextId);
        context.setDescription(description);
        context.setCreatedAt(Instant.now());
        return context;
    }
}
