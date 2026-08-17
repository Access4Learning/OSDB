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
import org.sifunity.broker.environment.exception.InvalidTagValueException;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EntityTagRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.EntityTagService;
import org.sifunity.broker.environment.service.TagDefinitionService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for entity tag value validation against allowed_values.
 *
 * <p><strong>Property 12: Entity tag value validation against allowed_values</strong></p>
 *
 * <p>For any TagDefinition with a non-null allowed_values list and any entity tag create
 * request targeting that definition, the request SHALL succeed only if the tagValue is
 * contained in the allowed_values list. If the tagValue is not in the list, the request
 * SHALL be rejected with an InvalidTagValueException.</p>
 *
 * <p>If allowedValues is null/empty, any tagValue is accepted.</p>
 *
 * <p><strong>Validates: Requirements 7.3, 7.4</strong></p>
 *
 * <p>Feature: tag-management-admin, Property 12: Entity tag value validation against allowed_values</p>
 */
class EntityTagValueValidationPropertyTest {

    private static final String DOMAIN_ID = "test-domain";

    /**
     * Property 12a: Creating an entity tag with a tagValue that IS in the definition's
     * allowedValues succeeds.
     *
     * <p><strong>Validates: Requirements 7.3</strong></p>
     */
    @Property(tries = 20)
    @Tag("tag-management-admin")
    @Tag("property-12-entity-tag-value-validation")
    void tagValueInAllowedValuesSucceeds(
            @ForAll("validAllowedValuesDefinitions") TagDefinition definition,
            @ForAll("entityTypes") EntityType entityType,
            @ForAll("uuids") UUID entityId,
            @ForAll("solutionIds") String solutionId
    ) {
        // Pick a value from the definition's allowedValues
        List<String> allowedValues = definition.getAllowedValues();
        String tagValue = allowedValues.get(0); // always at least one element

        // Ensure entityType is in applicableEntityTypes
        definition.setApplicableEntityTypes(List.of(entityType.name()));

        // Set up mocks
        EntityTagRepository entityTagRepository = mock(EntityTagRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        TagDefinitionService tagDefinitionService = mock(TagDefinitionService.class);
        DomainRepository domainRepository = mock(DomainRepository.class);

        EntityTagService service = new EntityTagService(entityTagRepository, solutionRepository, tagDefinitionService, domainRepository);

        Solution solution = new Solution();
        solution.setId(UUID.randomUUID());
        solution.setSolutionId(solutionId);

        UUID domainUuid = UUID.randomUUID();
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(DOMAIN_ID);
        when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)).thenReturn(Optional.of(solution));
        when(tagDefinitionService.findByTagName(definition.getTagName())).thenReturn(Optional.of(definition));
        when(entityTagRepository.existsBySolutionIdAndEntityTypeAndEntityIdAndTagKey(
                any(), any(), any(), any())).thenReturn(false);
        when(entityTagRepository.save(any(EntityTag.class))).thenAnswer(invocation -> {
            EntityTag tag = invocation.getArgument(0);
            tag.setId(UUID.randomUUID());
            tag.setCreatedAt(Instant.now());
            return tag;
        });

        EntityTagCreateRequest request = new EntityTagCreateRequest(
                entityType, entityId, definition.getTagName(), tagValue);

        // Should succeed without exception
        EntityTagDTO result = service.createTag(DOMAIN_ID, solutionId, request);

        assertThat(result).isNotNull();
        assertThat(result.tagValue()).isEqualTo(tagValue);
    }

    /**
     * Property 12b: Creating an entity tag with a tagValue that is NOT in the definition's
     * allowedValues throws InvalidTagValueException.
     *
     * <p><strong>Validates: Requirements 7.4</strong></p>
     */
    @Property(tries = 20)
    @Tag("tag-management-admin")
    @Tag("property-12-entity-tag-value-validation")
    void tagValueNotInAllowedValuesThrows(
            @ForAll("validAllowedValuesDefinitions") TagDefinition definition,
            @ForAll("disallowedValues") String disallowedValue,
            @ForAll("entityTypes") EntityType entityType,
            @ForAll("uuids") UUID entityId,
            @ForAll("solutionIds") String solutionId
    ) {
        // Ensure the disallowed value is actually not in the allowed list
        Assume.that(!definition.getAllowedValues().contains(disallowedValue));

        // Ensure entityType is in applicableEntityTypes
        definition.setApplicableEntityTypes(List.of(entityType.name()));

        // Set up mocks
        EntityTagRepository entityTagRepository = mock(EntityTagRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        TagDefinitionService tagDefinitionService = mock(TagDefinitionService.class);
        DomainRepository domainRepository = mock(DomainRepository.class);

        EntityTagService service = new EntityTagService(entityTagRepository, solutionRepository, tagDefinitionService, domainRepository);

        Solution solution = new Solution();
        solution.setId(UUID.randomUUID());
        solution.setSolutionId(solutionId);

        UUID domainUuid = UUID.randomUUID();
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(DOMAIN_ID);
        when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)).thenReturn(Optional.of(solution));
        when(tagDefinitionService.findByTagName(definition.getTagName())).thenReturn(Optional.of(definition));

        EntityTagCreateRequest request = new EntityTagCreateRequest(
                entityType, entityId, definition.getTagName(), disallowedValue);

        // Should throw InvalidTagValueException
        assertThatThrownBy(() -> service.createTag(DOMAIN_ID, solutionId, request))
                .isInstanceOf(InvalidTagValueException.class);
    }

    /**
     * Property 12c: If allowedValues is null, any tagValue is accepted.
     *
     * <p><strong>Validates: Requirements 7.3</strong></p>
     */
    @Property(tries = 20)
    @Tag("tag-management-admin")
    @Tag("property-12-entity-tag-value-validation")
    void nullAllowedValuesAcceptsAnyTagValue(
            @ForAll("anyTagValues") String tagValue,
            @ForAll("entityTypes") EntityType entityType,
            @ForAll("uuids") UUID entityId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("tagNames") String tagName
    ) {
        // Definition with null allowedValues
        TagDefinition definition = new TagDefinition();
        definition.setTagName(tagName.toUpperCase());
        definition.setAllowedValues(null);
        definition.setApplicableEntityTypes(List.of(entityType.name()));

        // Set up mocks
        EntityTagRepository entityTagRepository = mock(EntityTagRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        TagDefinitionService tagDefinitionService = mock(TagDefinitionService.class);
        DomainRepository domainRepository = mock(DomainRepository.class);

        EntityTagService service = new EntityTagService(entityTagRepository, solutionRepository, tagDefinitionService, domainRepository);

        Solution solution = new Solution();
        solution.setId(UUID.randomUUID());
        solution.setSolutionId(solutionId);

        UUID domainUuid = UUID.randomUUID();
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(DOMAIN_ID);
        when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)).thenReturn(Optional.of(solution));
        when(tagDefinitionService.findByTagName(tagName.toUpperCase())).thenReturn(Optional.of(definition));
        when(entityTagRepository.existsBySolutionIdAndEntityTypeAndEntityIdAndTagKey(
                any(), any(), any(), any())).thenReturn(false);
        when(entityTagRepository.save(any(EntityTag.class))).thenAnswer(invocation -> {
            EntityTag tag = invocation.getArgument(0);
            tag.setId(UUID.randomUUID());
            tag.setCreatedAt(Instant.now());
            return tag;
        });

        EntityTagCreateRequest request = new EntityTagCreateRequest(
                entityType, entityId, tagName.toUpperCase(), tagValue);

        // Should succeed without exception
        EntityTagDTO result = service.createTag(DOMAIN_ID, solutionId, request);

        assertThat(result).isNotNull();
        assertThat(result.tagValue()).isEqualTo(tagValue);
    }

    /**
     * Property 12d: If allowedValues is empty list, any tagValue is accepted.
     *
     * <p><strong>Validates: Requirements 7.3</strong></p>
     */
    @Property(tries = 20)
    @Tag("tag-management-admin")
    @Tag("property-12-entity-tag-value-validation")
    void emptyAllowedValuesAcceptsAnyTagValue(
            @ForAll("anyTagValues") String tagValue,
            @ForAll("entityTypes") EntityType entityType,
            @ForAll("uuids") UUID entityId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("tagNames") String tagName
    ) {
        // Definition with empty allowedValues list
        TagDefinition definition = new TagDefinition();
        definition.setTagName(tagName.toUpperCase());
        definition.setAllowedValues(List.of());
        definition.setApplicableEntityTypes(List.of(entityType.name()));

        // Set up mocks
        EntityTagRepository entityTagRepository = mock(EntityTagRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        TagDefinitionService tagDefinitionService = mock(TagDefinitionService.class);
        DomainRepository domainRepository = mock(DomainRepository.class);

        EntityTagService service = new EntityTagService(entityTagRepository, solutionRepository, tagDefinitionService, domainRepository);

        Solution solution = new Solution();
        solution.setId(UUID.randomUUID());
        solution.setSolutionId(solutionId);

        UUID domainUuid = UUID.randomUUID();
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(DOMAIN_ID);
        when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)).thenReturn(Optional.of(solution));
        when(tagDefinitionService.findByTagName(tagName.toUpperCase())).thenReturn(Optional.of(definition));
        when(entityTagRepository.existsBySolutionIdAndEntityTypeAndEntityIdAndTagKey(
                any(), any(), any(), any())).thenReturn(false);
        when(entityTagRepository.save(any(EntityTag.class))).thenAnswer(invocation -> {
            EntityTag tag = invocation.getArgument(0);
            tag.setId(UUID.randomUUID());
            tag.setCreatedAt(Instant.now());
            return tag;
        });

        EntityTagCreateRequest request = new EntityTagCreateRequest(
                entityType, entityId, tagName.toUpperCase(), tagValue);

        // Should succeed without exception
        EntityTagDTO result = service.createTag(DOMAIN_ID, solutionId, request);

        assertThat(result).isNotNull();
        assertThat(result.tagValue()).isEqualTo(tagValue);
    }

    // ========== Arbitraries ==========

    /**
     * Generates TagDefinition instances with non-null, non-empty allowedValues.
     */
    @Provide
    Arbitrary<TagDefinition> validAllowedValuesDefinitions() {
        Arbitrary<String> tagNames = Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_')
                .ofMinLength(3)
                .ofMaxLength(30);

        Arbitrary<List<String>> allowedValues = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars('_', '-')
                .ofMinLength(1)
                .ofMaxLength(20)
                .list()
                .ofMinSize(1)
                .ofMaxSize(5)
                .uniqueElements();

        return Combinators.combine(tagNames, allowedValues).as((name, values) -> {
            TagDefinition def = new TagDefinition();
            def.setId(UUID.randomUUID());
            def.setTagName(name.toUpperCase());
            def.setAllowedValues(values);
            def.setApplicableEntityTypes(List.of(EntityType.SOLUTION.name()));
            def.setCreatedBy("system");
            def.setCreatedAt(Instant.now());
            def.setUpdatedAt(Instant.now());
            return def;
        });
    }

    @Provide
    Arbitrary<EntityType> entityTypes() {
        return Arbitraries.of(EntityType.values());
    }

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars('-')
                .ofMinLength(3)
                .ofMaxLength(20);
    }

    @Provide
    Arbitrary<String> tagNames() {
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_')
                .ofMinLength(3)
                .ofMaxLength(30);
    }

    /**
     * Generates values that are unlikely to be in the allowed values list.
     * Uses a distinct character set to minimize collisions with allowed values.
     */
    @Provide
    Arbitrary<String> disallowedValues() {
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withChars('!', '@', '#')
                .ofMinLength(5)
                .ofMaxLength(25);
    }

    /**
     * Generates arbitrary tag values for the unconstrained case.
     */
    @Provide
    Arbitrary<String> anyTagValues() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars('_', '-', ' ')
                .ofMinLength(1)
                .ofMaxLength(50);
    }
}
