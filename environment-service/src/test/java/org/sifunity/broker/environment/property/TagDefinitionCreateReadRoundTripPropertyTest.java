// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.domain.TagDefinition;
import org.sifunity.broker.environment.dto.TagDefinitionCreateRequest;
import org.sifunity.broker.environment.dto.TagDefinitionDTO;
import org.sifunity.broker.environment.repository.TagDefinitionRepository;
import org.sifunity.broker.environment.service.TagDefinitionService;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for TagDefinitionService create-then-read round trip.
 *
 * <p><strong>Property 2: Create-then-read round trip</strong></p>
 *
 * <p>For any valid TagDefinitionCreateRequest, creating a TagDefinition and then
 * retrieving it by the returned ID SHALL produce a DTO with matching tagName
 * (uppercased), tagDescription, applicableEntityTypes, allowedValues, and
 * createdBy fields.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 4.2</strong></p>
 *
 * <p>Feature: tag-management-admin, Property 2: Create-then-read round trip</p>
 */
class TagDefinitionCreateReadRoundTripPropertyTest {

    /**
     * Property 2: Create-then-read round trip
     *
     * <p><strong>Validates: Requirements 3.1, 4.2</strong></p>
     *
     * <p>For any valid TagDefinitionCreateRequest, creating a TagDefinition and then
     * retrieving it by the returned ID SHALL produce a DTO with matching tagName
     * (uppercased), tagDescription, applicableEntityTypes, allowedValues, and
     * createdBy fields.</p>
     */
    @Property(tries = 20)
    @Tag("tag-management-admin")
    @Tag("property-2-create-then-read-round-trip")
    void createThenReadProducesDtoWithMatchingFields(
            @ForAll("validCreateRequests") TagDefinitionCreateRequest request
    ) {
        // Set up mocks fresh for each iteration
        TagDefinitionRepository repository = mock(TagDefinitionRepository.class);
        TagDefinitionService service = new TagDefinitionService(repository);

        // Mock: no duplicate exists
        when(repository.existsByTagNameIgnoreCase(any())).thenReturn(false);

        // Capture the entity passed to save and simulate persistence
        ArgumentCaptor<TagDefinition> captor = ArgumentCaptor.forClass(TagDefinition.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> {
            TagDefinition entity = invocation.getArgument(0);
            // Simulate JPA @PrePersist lifecycle callback
            entity.setTagName(entity.getTagName() != null ? entity.getTagName().toUpperCase() : null);
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            // Simulate ID generation
            entity.setId(UUID.randomUUID());
            return entity;
        });

        // Mock: findById returns the saved entity
        when(repository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            TagDefinition saved = captor.getValue();
            if (saved.getId().equals(id)) {
                return Optional.of(saved);
            }
            return Optional.empty();
        });

        // ACT: Create
        TagDefinitionDTO created = service.createTagDefinition(request);

        // ACT: Read by returned ID
        TagDefinitionDTO retrieved = service.getTagDefinition(created.id());

        // ASSERT: Round-trip fields match
        assertThat(retrieved.tagName())
                .as("tagName must be uppercased version of input")
                .isEqualTo(request.tagName().toUpperCase());

        assertThat(retrieved.tagDescription())
                .as("tagDescription must match input")
                .isEqualTo(request.tagDescription());

        assertThat(retrieved.applicableEntityTypes())
                .as("applicableEntityTypes must match input")
                .isEqualTo(request.applicableEntityTypes());

        assertThat(retrieved.allowedValues())
                .as("allowedValues must match input")
                .isEqualTo(request.allowedValues());

        assertThat(retrieved.createdBy())
                .as("createdBy must match input")
                .isEqualTo(request.createdBy());

        // ASSERT: create and read return identical DTOs
        assertThat(retrieved).isEqualTo(created);
    }

    // ========== Arbitraries ==========

    /**
     * Generates valid TagDefinitionCreateRequest instances with:
     * - Non-blank tagName (alphanumeric + underscore)
     * - Optional tagDescription (nullable)
     * - Non-empty applicableEntityTypes (valid EntityType values)
     * - Optional allowedValues (nullable or non-empty list)
     * - Non-blank createdBy
     */
    @Provide
    Arbitrary<TagDefinitionCreateRequest> validCreateRequests() {
        Arbitrary<String> tagNames = Arbitraries.strings()
                .withCharRange('a', 'z')
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
                .as(TagDefinitionCreateRequest::new);
    }
}
