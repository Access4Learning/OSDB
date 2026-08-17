// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.domain.TagDefinition;
import org.sifunity.broker.environment.dto.TagDefinitionDTO;
import org.sifunity.broker.environment.dto.TagDefinitionUpdateRequest;
import org.sifunity.broker.environment.repository.TagDefinitionRepository;
import org.sifunity.broker.environment.service.TagDefinitionService;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for TagDefinitionService update preserving tag_name.
 *
 * <p><strong>Property 8: Update preserves tag_name and reflects changes</strong></p>
 *
 * <p>For any existing TagDefinition and any valid TagDefinitionUpdateRequest,
 * after update the tag_name SHALL remain unchanged (equal to the original),
 * while tagDescription, applicableEntityTypes, and allowedValues SHALL reflect
 * the update request values.</p>
 *
 * <p><strong>Validates: Requirements 5.1, 5.3</strong></p>
 *
 * <p>Feature: tag-management-admin, Property 8: Update preserves tag_name and reflects changes</p>
 */
class TagDefinitionUpdatePreservesTagNamePropertyTest {

    /**
     * Property 8: Update preserves tag_name and reflects changes
     *
     * <p><strong>Validates: Requirements 5.1, 5.3</strong></p>
     *
     * <p>For any existing TagDefinition and any valid TagDefinitionUpdateRequest,
     * after update the tag_name SHALL remain unchanged (equal to the original),
     * while tagDescription, applicableEntityTypes, and allowedValues SHALL reflect
     * the update request values. The updatedAt timestamp SHALL be refreshed.</p>
     */
    @Property(tries = 20)
    @Tag("tag-management-admin")
    @Tag("property-8-update-preserves-tag-name")
    void updatePreservesTagNameAndReflectsChanges(
            @ForAll("existingTagDefinitions") TagDefinition existing,
            @ForAll("validUpdateRequests") TagDefinitionUpdateRequest updateRequest
    ) {
        // Set up mocks fresh for each iteration
        TagDefinitionRepository repository = mock(TagDefinitionRepository.class);
        TagDefinitionService service = new TagDefinitionService(repository);

        UUID existingId = existing.getId();
        String originalTagName = existing.getTagName();
        Instant originalUpdatedAt = existing.getUpdatedAt();

        // Mock: findById returns the existing entity
        when(repository.findById(existingId)).thenReturn(Optional.of(existing));

        // Capture the entity passed to save and simulate @PreUpdate lifecycle
        ArgumentCaptor<TagDefinition> captor = ArgumentCaptor.forClass(TagDefinition.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> {
            TagDefinition entity = invocation.getArgument(0);
            // Simulate JPA @PreUpdate lifecycle callback
            entity.setTagName(entity.getTagName() != null ? entity.getTagName().toUpperCase() : null);
            entity.setUpdatedAt(Instant.now());
            return entity;
        });

        // ACT: Update
        TagDefinitionDTO updated = service.updateTagDefinition(existingId, updateRequest);

        // ASSERT: tag_name remains unchanged (immutable)
        assertThat(updated.tagName())
                .as("tag_name must remain unchanged after update")
                .isEqualTo(originalTagName);

        // ASSERT: updated fields reflect the request values
        assertThat(updated.tagDescription())
                .as("tagDescription must reflect update request")
                .isEqualTo(updateRequest.tagDescription());

        assertThat(updated.applicableEntityTypes())
                .as("applicableEntityTypes must reflect update request")
                .isEqualTo(updateRequest.applicableEntityTypes());

        assertThat(updated.allowedValues())
                .as("allowedValues must reflect update request")
                .isEqualTo(updateRequest.allowedValues());

        // ASSERT: updatedAt timestamp is refreshed
        assertThat(updated.updatedAt())
                .as("updatedAt must be refreshed after update")
                .isAfter(originalUpdatedAt);
    }

    // ========== Arbitraries ==========

    /**
     * Generates existing TagDefinition entities with pre-set fields simulating
     * a previously persisted entity (ID assigned, tagName already uppercased,
     * timestamps set).
     */
    @Provide
    Arbitrary<TagDefinition> existingTagDefinitions() {
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
                    entity.setTagName(name.toUpperCase()); // Already uppercased (persisted)
                    entity.setTagDescription(desc);
                    entity.setApplicableEntityTypes(types);
                    entity.setAllowedValues(values);
                    entity.setCreatedBy(createdBy);
                    entity.setCreatedAt(Instant.now().minusSeconds(3600)); // Created 1 hour ago
                    entity.setUpdatedAt(Instant.now().minusSeconds(1800)); // Updated 30 min ago
                    return entity;
                });
    }

    /**
     * Generates valid TagDefinitionUpdateRequest instances with:
     * - Optional tagDescription (nullable)
     * - Non-empty applicableEntityTypes (valid EntityType values)
     * - Optional allowedValues (nullable or non-empty list)
     */
    @Provide
    Arbitrary<TagDefinitionUpdateRequest> validUpdateRequests() {
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

        return Combinators.combine(descriptions, entityTypes, allowedValues)
                .as(TagDefinitionUpdateRequest::new);
    }
}
