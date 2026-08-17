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
import org.sifunity.broker.environment.exception.TagDefinitionNotFoundForKeyException;
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
 * Property-based test for Property 11: Entity tag key validation against definitions.
 *
 * <p><strong>Property 11: Entity tag key validation against definitions</strong></p>
 *
 * <p>For any entity tag create request, the request SHALL succeed only if a TagDefinition
 * exists whose tag_name matches the request's tagKey (case-insensitive comparison).
 * If no matching TagDefinition exists, the request SHALL be rejected with a
 * TagDefinitionNotFoundForKeyException.</p>
 *
 * <p><strong>Validates: Requirements 7.1, 7.2</strong></p>
 */
class EntityTagKeyValidationPropertyTest {

    private static final String DOMAIN_ID = "test-domain";

    /**
     * Property 11a: Entity tag creation succeeds when a matching TagDefinition exists.
     *
     * <p>For any valid entity tag create request where a TagDefinition exists with a
     * tag_name matching the request's tagKey (case-insensitive), the creation SHALL succeed
     * and return a valid EntityTagDTO.</p>
     *
     * <p><strong>Validates: Requirements 7.1</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: tag-management-admin, Property 11: Entity tag key validation against definitions")
    void entityTagCreationSucceedsWhenMatchingDefinitionExists(
            @ForAll("validEntityTagRequests") EntityTagCreateRequest request,
            @ForAll("validSolutionIds") String solutionId
    ) {
        // Set up mocks
        EntityTagRepository entityTagRepository = mock(EntityTagRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
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

        Solution solution = mock(Solution.class);
        UUID solutionUuid = UUID.randomUUID();
        when(solution.getId()).thenReturn(solutionUuid);
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)).thenReturn(Optional.of(solution));

        // Mock: TagDefinition exists for the tagKey (case-insensitive match)
        TagDefinition definition = new TagDefinition();
        definition.setId(UUID.randomUUID());
        definition.setTagName(request.tagKey().toUpperCase());
        definition.setApplicableEntityTypes(List.of(request.entityType().name()));
        definition.setAllowedValues(null); // No value constraint for this property
        definition.setCreatedBy("system");
        definition.setCreatedAt(Instant.now());
        definition.setUpdatedAt(Instant.now());
        when(tagDefinitionService.findByTagName(request.tagKey())).thenReturn(Optional.of(definition));

        // Mock: no duplicate tag exists
        when(entityTagRepository.existsBySolutionIdAndEntityTypeAndEntityIdAndTagKey(
                any(), any(), any(), any())).thenReturn(false);

        // Mock: save returns the entity with generated fields
        when(entityTagRepository.save(any(EntityTag.class))).thenAnswer(invocation -> {
            EntityTag entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(Instant.now());
            return entity;
        });

        // ACT: Create entity tag — should succeed
        EntityTagDTO result = entityTagService.createTag(DOMAIN_ID, solutionId, request);

        // ASSERT: Creation succeeded and returned valid DTO
        assertThat(result).isNotNull();
        assertThat(result.id()).isNotNull();
        assertThat(result.entityType()).isEqualTo(request.entityType());
        assertThat(result.entityId()).isEqualTo(request.entityId());
        assertThat(result.tagKey()).isEqualTo(request.tagKey());
        assertThat(result.tagValue()).isEqualTo(request.tagValue());

        // Verify TagDefinitionService was consulted
        verify(tagDefinitionService).findByTagName(request.tagKey());
    }

    /**
     * Property 11b: Entity tag creation fails when no matching TagDefinition exists.
     *
     * <p>For any entity tag create request where NO TagDefinition exists with a tag_name
     * matching the request's tagKey, the creation SHALL be rejected with a
     * TagDefinitionNotFoundForKeyException.</p>
     *
     * <p><strong>Validates: Requirements 7.2</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: tag-management-admin, Property 11: Entity tag key validation against definitions")
    void entityTagCreationFailsWhenNoMatchingDefinitionExists(
            @ForAll("validEntityTagRequests") EntityTagCreateRequest request,
            @ForAll("validSolutionIds") String solutionId
    ) {
        // Set up mocks
        EntityTagRepository entityTagRepository = mock(EntityTagRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
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

        Solution solution = mock(Solution.class);
        UUID solutionUuid = UUID.randomUUID();
        when(solution.getId()).thenReturn(solutionUuid);
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)).thenReturn(Optional.of(solution));

        // Mock: NO TagDefinition exists for the tagKey
        when(tagDefinitionService.findByTagName(request.tagKey())).thenReturn(Optional.empty());

        // ACT & ASSERT: Creation should throw TagDefinitionNotFoundForKeyException
        assertThatThrownBy(() -> entityTagService.createTag(DOMAIN_ID, solutionId, request))
                .isInstanceOf(TagDefinitionNotFoundForKeyException.class)
                .hasMessageContaining(request.tagKey());

        // Verify TagDefinitionService was consulted
        verify(tagDefinitionService).findByTagName(request.tagKey());

        // Verify no entity tag was persisted
        verify(entityTagRepository, never()).save(any());
    }

    // ========== Arbitraries ==========

    /**
     * Generates valid EntityTagCreateRequest instances with:
     * - Random EntityType
     * - Random UUID entityId
     * - Non-blank tagKey (alphanumeric + underscore, uppercase-friendly)
     * - Optional tagValue
     */
    @Provide
    Arbitrary<EntityTagCreateRequest> validEntityTagRequests() {
        Arbitrary<EntityType> entityTypes = Arbitraries.of(EntityType.values());

        Arbitrary<UUID> entityIds = Arbitraries.create(UUID::randomUUID);

        Arbitrary<String> tagKeys = Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('a', 'z')
                .withChars('_')
                .ofMinLength(3)
                .ofMaxLength(30);

        Arbitrary<String> tagValues = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.of("true", "false", "enabled", "disabled", "high", "medium", "low")
        );

        return Combinators.combine(entityTypes, entityIds, tagKeys, tagValues)
                .as(EntityTagCreateRequest::new);
    }

    /**
     * Generates valid solution IDs following the project's naming pattern.
     */
    @Provide
    Arbitrary<String> validSolutionIds() {
        Arbitrary<String> prefixes = Arbitraries.of(
                "production", "staging", "testing", "development", "qa", "uat");
        Arbitrary<Integer> numbers = Arbitraries.integers().between(1, 999);

        return Combinators.combine(prefixes, numbers)
                .as((prefix, num) -> prefix + "-" + num);
    }
}
