// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.environment.domain.EntityTag;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.domain.TagDefinition;
import org.sifunity.broker.environment.repository.EntityTagRepository;
import org.sifunity.broker.environment.repository.TagDefinitionRepository;
import org.sifunity.broker.environment.service.TagDefinitionService;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property-based test verifying that deleting a TagDefinition does NOT cascade
 * to entity_tags that reference that tag key.
 *
 * <p><strong>Property 10: Delete does not cascade to entity_tags</strong></p>
 *
 * <p>For any TagDefinition whose tag_name has been used to create entity tags,
 * deleting that TagDefinition SHALL NOT remove or modify the associated entity tags.</p>
 *
 * <p><strong>Validates: Requirements 6.3</strong></p>
 */
@Tag("tag-management-admin")
@Tag("property-10-delete-does-not-cascade")
class TagDefinitionDeleteNoCascadePropertyTest {

    /**
     * Property 10: Delete does not cascade to entity_tags
     *
     * <p><strong>Validates: Requirements 6.3</strong></p>
     *
     * <p>Verifies that when a TagDefinition is deleted via TagDefinitionService,
     * the EntityTagRepository is never called to delete entity tags that reference
     * that tag key. The entity_tags remain intact after the tag definition is removed.</p>
     */
    @Property(tries = 20)
    void deletingTagDefinitionDoesNotCascadeToEntityTags(
            @ForAll("tagDefinitionsWithEntityTags") TagDefinitionWithEntityTags scenario
    ) {
        // Set up mocks
        TagDefinitionRepository tagDefRepo = mock(TagDefinitionRepository.class);
        EntityTagRepository entityTagRepo = mock(EntityTagRepository.class);
        TagDefinitionService tagDefService = new TagDefinitionService(tagDefRepo);

        TagDefinition definition = scenario.definition();
        List<EntityTag> associatedTags = scenario.entityTags();
        UUID defId = definition.getId();

        // Mock: TagDefinition exists and can be deleted
        when(tagDefRepo.findById(defId)).thenReturn(Optional.of(definition));
        doNothing().when(tagDefRepo).delete(definition);

        // Mock: entity tags exist for this tag key
        for (EntityTag entityTag : associatedTags) {
            when(entityTagRepo.findById(entityTag.getId()))
                    .thenReturn(Optional.of(entityTag));
        }

        // ACT: Delete the tag definition
        tagDefService.deleteTagDefinition(defId);

        // ASSERT: TagDefinition was deleted
        verify(tagDefRepo).delete(definition);

        // ASSERT: EntityTagRepository was NEVER called for any delete operation
        // This confirms no FK cascade between tag_definitions and entity_tags
        verifyNoInteractions(entityTagRepo);

        // ASSERT: Entity tags remain retrievable (simulating post-delete state)
        for (EntityTag entityTag : associatedTags) {
            assertThat(entityTagRepo.findById(entityTag.getId()))
                    .isPresent()
                    .hasValueSatisfying(tag -> {
                        assertThat(tag.getTagKey()).isEqualTo(entityTag.getTagKey());
                        assertThat(tag.getTagValue()).isEqualTo(entityTag.getTagValue());
                        assertThat(tag.getEntityType()).isEqualTo(entityTag.getEntityType());
                        assertThat(tag.getEntityId()).isEqualTo(entityTag.getEntityId());
                        assertThat(tag.getSolutionId()).isEqualTo(entityTag.getSolutionId());
                    });
        }
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<TagDefinitionWithEntityTags> tagDefinitionsWithEntityTags() {
        Arbitrary<String> tagNames = Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_')
                .ofMinLength(1)
                .ofMaxLength(50);

        Arbitrary<List<String>> entityTypes = Arbitraries.of(EntityType.values())
                .map(Enum::name)
                .list()
                .ofMinSize(1)
                .ofMaxSize(3)
                .uniqueElements();

        Arbitrary<List<String>> allowedValues = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withCharRange('0', '9')
                        .ofMinLength(1)
                        .ofMaxLength(20)
                        .list()
                        .ofMinSize(1)
                        .ofMaxSize(3)
        );

        Arbitrary<Integer> entityTagCounts = Arbitraries.integers().between(1, 5);

        return Combinators.combine(tagNames, entityTypes, allowedValues, entityTagCounts)
                .as((name, types, values, tagCount) -> {
                    // Build TagDefinition
                    TagDefinition def = new TagDefinition();
                    def.setId(UUID.randomUUID());
                    def.setTagName(name.toUpperCase());
                    def.setTagDescription("Test definition for " + name);
                    def.setApplicableEntityTypes(types);
                    def.setAllowedValues(values);
                    def.setCreatedBy("test-admin");
                    def.setCreatedAt(Instant.now());
                    def.setUpdatedAt(Instant.now());

                    // Build associated entity tags using this definition's tag_name as tag_key
                    List<EntityTag> tags = new ArrayList<>();
                    for (int i = 0; i < tagCount; i++) {
                        EntityTag entityTag = new EntityTag();
                        entityTag.setId(UUID.randomUUID());
                        entityTag.setSolutionId(UUID.randomUUID());
                        entityTag.setEntityType(EntityType.valueOf(types.get(i % types.size())));
                        entityTag.setEntityId(UUID.randomUUID());
                        entityTag.setTagKey(def.getTagName());
                        entityTag.setTagValue(values != null && !values.isEmpty()
                                ? values.get(i % values.size())
                                : "value-" + i);
                        entityTag.setCreatedAt(Instant.now());
                        tags.add(entityTag);
                    }

                    return new TagDefinitionWithEntityTags(def, tags);
                });
    }

    /**
     * Test scenario pairing a TagDefinition with entity tags that reference its tag_name.
     */
    record TagDefinitionWithEntityTags(TagDefinition definition, List<EntityTag> entityTags) {}
}
