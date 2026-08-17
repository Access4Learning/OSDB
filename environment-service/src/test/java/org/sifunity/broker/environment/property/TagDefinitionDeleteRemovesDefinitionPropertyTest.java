// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.domain.TagDefinition;
import org.sifunity.broker.environment.exception.TagDefinitionNotFoundException;
import org.sifunity.broker.environment.repository.TagDefinitionRepository;
import org.sifunity.broker.environment.service.TagDefinitionService;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for TagDefinitionService delete removes definition.
 *
 * <p><strong>Property 9: Delete removes definition</strong></p>
 *
 * <p>For any persisted TagDefinition, after deletion, attempting to retrieve it
 * by ID SHALL result in a TagDefinitionNotFoundException.</p>
 *
 * <p><strong>Validates: Requirements 6.1</strong></p>
 *
 * <p>Feature: tag-management-admin, Property 9: Delete removes definition</p>
 */
class TagDefinitionDeleteRemovesDefinitionPropertyTest {

    /**
     * Property 9: Delete removes definition
     *
     * <p><strong>Validates: Requirements 6.1</strong></p>
     *
     * <p>For any persisted TagDefinition, after deletion, attempting to retrieve it
     * by ID SHALL result in a TagDefinitionNotFoundException.</p>
     */
    @Property(tries = 20)
    @Tag("Feature: tag-management-admin, Property 9: Delete removes definition")
    void afterDeletionRetrievalByIdThrowsNotFoundException(
            @ForAll("persistedTagDefinitions") TagDefinition persisted
    ) {
        // Set up mocks fresh for each iteration
        TagDefinitionRepository repository = mock(TagDefinitionRepository.class);
        TagDefinitionService service = new TagDefinitionService(repository);

        UUID id = persisted.getId();

        // Mock: findById returns the entity (it exists before deletion)
        when(repository.findById(id)).thenReturn(Optional.of(persisted));
        doNothing().when(repository).delete(persisted);

        // ACT: Delete the tag definition
        service.deleteTagDefinition(id);

        // Verify delete was called
        verify(repository).delete(persisted);

        // After deletion, simulate that findById returns empty
        when(repository.findById(id)).thenReturn(Optional.empty());

        // ACT + ASSERT: Retrieving by ID after deletion throws TagDefinitionNotFoundException
        assertThatThrownBy(() -> service.getTagDefinition(id))
                .isInstanceOf(TagDefinitionNotFoundException.class);
    }

    // ========== Arbitraries ==========

    /**
     * Generates TagDefinition entities simulating persisted state with:
     * - Non-null UUID id
     * - Uppercased tagName
     * - Valid applicableEntityTypes
     * - Optional allowedValues
     * - Timestamps set
     */
    @Provide
    Arbitrary<TagDefinition> persistedTagDefinitions() {
        Arbitrary<String> tagNames = Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_')
                .ofMinLength(1)
                .ofMaxLength(50);

        Arbitrary<String> descriptions = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withCharRange('A', 'Z')
                        .withChars(' ', '.', ',')
                        .ofMinLength(1)
                        .ofMaxLength(200)
        );

        Arbitrary<List<String>> entityTypes = Arbitraries.of(EntityType.values())
                .map(Enum::name)
                .list()
                .ofMinSize(1)
                .ofMaxSize(5)
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
                        .ofMaxSize(5)
        );

        Arbitrary<String> createdByValues = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars('-', '_')
                .ofMinLength(3)
                .ofMaxLength(30);

        return Combinators.combine(tagNames, descriptions, entityTypes, allowedValues, createdByValues)
                .as((name, desc, types, values, createdBy) -> {
                    TagDefinition entity = new TagDefinition();
                    entity.setId(UUID.randomUUID());
                    entity.setTagName(name.toUpperCase());
                    entity.setTagDescription(desc);
                    entity.setApplicableEntityTypes(types);
                    entity.setAllowedValues(values);
                    entity.setCreatedBy(createdBy);
                    entity.setCreatedAt(Instant.now());
                    entity.setUpdatedAt(Instant.now());
                    return entity;
                });
    }
}
