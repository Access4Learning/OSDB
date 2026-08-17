// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.EntityTag;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.TagDefinition;
import org.sifunity.broker.environment.dto.EntityTagCreateRequest;
import org.sifunity.broker.environment.dto.EntityTagDTO;
import org.sifunity.broker.environment.exception.EntityTypeNotApplicableException;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EntityTagRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.EntityTagService;
import org.sifunity.broker.environment.service.TagDefinitionService;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for Property 13: Entity tag entityType validation against applicable_entity_types.
 *
 * <p><strong>Property 13: Entity tag entityType validation against applicable_entity_types</strong></p>
 *
 * <p>For any TagDefinition and any entity tag create request targeting that definition,
 * the request SHALL succeed only if the request's entityType is contained in the
 * TagDefinition's applicableEntityTypes array. If not, the request SHALL be rejected
 * with an EntityTypeNotApplicableException.</p>
 *
 * <p><strong>Validates: Requirements 7.5, 7.6</strong></p>
 */
class EntityTagEntityTypeValidationPropertyTest {

    private static final String DOMAIN_ID = "test-domain";

    /**
     * Property 13a: Entity tag creation succeeds when entityType IS in applicableEntityTypes.
     *
     * <p><strong>Validates: Requirements 7.5</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: tag-management-admin, Property 13: Entity tag entityType validation")
    void entityTagCreationSucceedsWhenEntityTypeIsApplicable(
            @ForAll("applicableEntityTypeScenarios") ApplicableScenario scenario) {

        // Set up mocks
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        EntityTagRepository entityTagRepository = mock(EntityTagRepository.class);
        TagDefinitionService tagDefinitionService = mock(TagDefinitionService.class);
        DomainRepository domainRepository = mock(DomainRepository.class);

        EntityTagService entityTagService = new EntityTagService(
                entityTagRepository, solutionRepository, tagDefinitionService, domainRepository);

        // Mock domain + solution resolution
        UUID domainUuid = UUID.randomUUID();
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(DOMAIN_ID);
        when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(domain));

        Solution solution = new Solution();
        solution.setId(scenario.solutionUuid);
        solution.setSolutionId(scenario.solutionId);
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, scenario.solutionId))
                .thenReturn(Optional.of(solution));

        // Mock tag definition lookup — entityType IS in applicableEntityTypes
        TagDefinition definition = new TagDefinition();
        definition.setId(UUID.randomUUID());
        definition.setTagName(scenario.tagKey.toUpperCase());
        definition.setApplicableEntityTypes(scenario.applicableEntityTypes);
        definition.setAllowedValues(null); // no value constraint
        when(tagDefinitionService.findByTagName(scenario.tagKey))
                .thenReturn(Optional.of(definition));

        // Mock no duplicate exists
        when(entityTagRepository.existsBySolutionIdAndEntityTypeAndEntityIdAndTagKey(
                any(), any(), any(), any())).thenReturn(false);

        // Mock save
        when(entityTagRepository.save(any(EntityTag.class))).thenAnswer(invocation -> {
            EntityTag tag = invocation.getArgument(0);
            tag.setId(UUID.randomUUID());
            tag.setCreatedAt(Instant.now());
            return tag;
        });

        // ACT: Create entity tag with an entityType that IS in applicableEntityTypes
        EntityTagCreateRequest request = new EntityTagCreateRequest(
                scenario.entityType, scenario.entityId, scenario.tagKey, scenario.tagValue);

        EntityTagDTO result = entityTagService.createTag(DOMAIN_ID, scenario.solutionId, request);

        // ASSERT: Creation succeeds
        assertThat(result).isNotNull();
        assertThat(result.id()).isNotNull();
        assertThat(result.entityType()).isEqualTo(scenario.entityType);
        assertThat(result.tagKey()).isEqualTo(scenario.tagKey);

        // Verify save was called (tag was persisted)
        verify(entityTagRepository).save(any(EntityTag.class));
    }

    /**
     * Property 13b: Entity tag creation fails when entityType is NOT in applicableEntityTypes.
     *
     * <p><strong>Validates: Requirements 7.6</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: tag-management-admin, Property 13: Entity tag entityType validation")
    void entityTagCreationFailsWhenEntityTypeIsNotApplicable(
            @ForAll("nonApplicableEntityTypeScenarios") NonApplicableScenario scenario) {

        // Set up mocks
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        EntityTagRepository entityTagRepository = mock(EntityTagRepository.class);
        TagDefinitionService tagDefinitionService = mock(TagDefinitionService.class);
        DomainRepository domainRepository = mock(DomainRepository.class);

        EntityTagService entityTagService = new EntityTagService(
                entityTagRepository, solutionRepository, tagDefinitionService, domainRepository);

        // Mock domain + solution resolution
        UUID domainUuid = UUID.randomUUID();
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(DOMAIN_ID);
        when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(domain));

        Solution solution = new Solution();
        solution.setId(scenario.solutionUuid);
        solution.setSolutionId(scenario.solutionId);
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, scenario.solutionId))
                .thenReturn(Optional.of(solution));

        // Mock tag definition lookup — entityType is NOT in applicableEntityTypes
        TagDefinition definition = new TagDefinition();
        definition.setId(UUID.randomUUID());
        definition.setTagName(scenario.tagKey.toUpperCase());
        definition.setApplicableEntityTypes(scenario.applicableEntityTypes);
        definition.setAllowedValues(null); // no value constraint
        when(tagDefinitionService.findByTagName(scenario.tagKey))
                .thenReturn(Optional.of(definition));

        // ACT & ASSERT: Creation should throw EntityTypeNotApplicableException
        EntityTagCreateRequest request = new EntityTagCreateRequest(
                scenario.entityType, scenario.entityId, scenario.tagKey, scenario.tagValue);

        assertThatThrownBy(() -> entityTagService.createTag(DOMAIN_ID, scenario.solutionId, request))
                .isInstanceOf(EntityTypeNotApplicableException.class)
                .hasMessageContaining(scenario.tagKey.toUpperCase())
                .hasMessageContaining(scenario.entityType.name());

        // Verify save was NOT called (tag was not persisted)
        verify(entityTagRepository, never()).save(any(EntityTag.class));
    }

    // ========== Scenario Records ==========

    record ApplicableScenario(
            String solutionId,
            UUID solutionUuid,
            EntityType entityType,
            UUID entityId,
            String tagKey,
            String tagValue,
            List<String> applicableEntityTypes
    ) {}

    record NonApplicableScenario(
            String solutionId,
            UUID solutionUuid,
            EntityType entityType,
            UUID entityId,
            String tagKey,
            String tagValue,
            List<String> applicableEntityTypes
    ) {}

    // ========== Arbitraries ==========

    /**
     * Generates scenarios where the request's entityType IS in the definition's applicableEntityTypes.
     */
    @Provide
    Arbitrary<ApplicableScenario> applicableEntityTypeScenarios() {
        Arbitrary<String> solutionIds = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars('-')
                .ofMinLength(3)
                .ofMaxLength(20);

        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);

        Arbitrary<EntityType> entityTypes = Arbitraries.of(EntityType.values());

        Arbitrary<String> tagKeys = Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_')
                .ofMinLength(3)
                .ofMaxLength(30);

        Arbitrary<String> tagValues = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.of("true", "false", "enabled", "disabled", "high", "low")
        );

        return Combinators.combine(solutionIds, uuids, entityTypes, uuids, tagKeys, tagValues)
                .as((solId, solUuid, entityType, entityId, tagKey, tagValue) -> {
                    // Build applicableEntityTypes that INCLUDES the request's entityType
                    List<String> applicable = new ArrayList<>();
                    applicable.add(entityType.name());
                    // Optionally add other entity types
                    for (EntityType et : EntityType.values()) {
                        if (et != entityType && applicable.size() < 3) {
                            applicable.add(et.name());
                        }
                    }
                    return new ApplicableScenario(
                            solId, solUuid, entityType, entityId, tagKey, tagValue, applicable);
                });
    }

    /**
     * Generates scenarios where the request's entityType is NOT in the definition's applicableEntityTypes.
     */
    @Provide
    Arbitrary<NonApplicableScenario> nonApplicableEntityTypeScenarios() {
        Arbitrary<String> solutionIds = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars('-')
                .ofMinLength(3)
                .ofMaxLength(20);

        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);

        Arbitrary<EntityType> entityTypes = Arbitraries.of(EntityType.values());

        Arbitrary<String> tagKeys = Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_')
                .ofMinLength(3)
                .ofMaxLength(30);

        Arbitrary<String> tagValues = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.of("true", "false", "enabled", "disabled", "high", "low")
        );

        return Combinators.combine(solutionIds, uuids, entityTypes, uuids, tagKeys, tagValues)
                .as((solId, solUuid, entityType, entityId, tagKey, tagValue) -> {
                    // Build applicableEntityTypes that EXCLUDES the request's entityType
                    List<String> applicable = Arrays.stream(EntityType.values())
                            .filter(et -> et != entityType)
                            .map(Enum::name)
                            .limit(2)
                            .collect(Collectors.toList());
                    // Ensure at least one type in the list (required by schema)
                    if (applicable.isEmpty()) {
                        // This shouldn't happen since we have 5 entity types,
                        // but as a safety net pick a different one
                        applicable.add("SOLUTION");
                    }
                    return new NonApplicableScenario(
                            solId, solUuid, entityType, entityId, tagKey, tagValue, applicable);
                });
    }
}
