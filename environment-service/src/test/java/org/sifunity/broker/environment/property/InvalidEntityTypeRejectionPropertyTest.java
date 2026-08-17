// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.dto.TagDefinitionCreateRequest;
import org.sifunity.broker.environment.repository.TagDefinitionRepository;
import org.sifunity.broker.environment.service.TagDefinitionService;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based test for Property 5: Invalid EntityType rejection.
 *
 * <p><strong>Property 5: Invalid EntityType rejection</strong></p>
 *
 * <p>For any string that is not a valid EntityType enum value (SOLUTION, ZONE,
 * CONTEXT, ENVIRONMENT, QUEUE), a create request containing that string in
 * applicableEntityTypes SHALL be rejected with HTTP 400.</p>
 *
 * <p><strong>Validates: Requirements 3.5</strong></p>
 *
 * <p>This test exercises the TagDefinitionService.createTagDefinition validation
 * that each string in applicableEntityTypes is a valid EntityType enum value.
 * Invalid values trigger an IllegalArgumentException (mapped to HTTP 400 by the
 * controller layer).</p>
 */
class InvalidEntityTypeRejectionPropertyTest {

    private static final Set<String> VALID_ENTITY_TYPES = Arrays.stream(EntityType.values())
            .map(Enum::name)
            .collect(Collectors.toSet());

    /**
     * Property 5: Invalid EntityType rejection — sole invalid value
     *
     * <p>A create request whose applicableEntityTypes contains only an invalid
     * EntityType string SHALL be rejected with an IllegalArgumentException.</p>
     *
     * <p><strong>Validates: Requirements 3.5</strong></p>
     */
    @Property(tries = 20)
    @Tag("tag-management-admin")
    @Tag("property-5-invalid-entity-type-rejection")
    void soleInvalidEntityTypeIsRejected(
            @ForAll("invalidEntityTypeStrings") String invalidType) {

        TagDefinitionRepository repository = mock(TagDefinitionRepository.class);
        TagDefinitionService service = new TagDefinitionService(repository);

        // Mock: no duplicate exists (so we reach EntityType validation)
        when(repository.existsByTagNameIgnoreCase(any())).thenReturn(false);

        TagDefinitionCreateRequest request = new TagDefinitionCreateRequest(
                "VALID_TAG_NAME",
                "Some description",
                List.of(invalidType),
                null,
                "admin-user"
        );

        assertThatThrownBy(() -> service.createTagDefinition(request))
                .as("Invalid EntityType '%s' should be rejected", invalidType)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(invalidType);
    }

    /**
     * Property 5: Invalid EntityType rejection — mixed valid and invalid values
     *
     * <p>A create request whose applicableEntityTypes contains at least one invalid
     * EntityType string (even alongside valid ones) SHALL be rejected with an
     * IllegalArgumentException.</p>
     *
     * <p><strong>Validates: Requirements 3.5</strong></p>
     */
    @Property(tries = 20)
    @Tag("tag-management-admin")
    @Tag("property-5-invalid-entity-type-rejection")
    void mixedValidAndInvalidEntityTypesIsRejected(
            @ForAll("invalidEntityTypeStrings") String invalidType,
            @ForAll("validEntityTypeLists") List<String> validTypes) {

        TagDefinitionRepository repository = mock(TagDefinitionRepository.class);
        TagDefinitionService service = new TagDefinitionService(repository);

        when(repository.existsByTagNameIgnoreCase(any())).thenReturn(false);

        // Combine valid types with the invalid one
        List<String> mixedTypes = new java.util.ArrayList<>(validTypes);
        mixedTypes.add(invalidType);

        TagDefinitionCreateRequest request = new TagDefinitionCreateRequest(
                "MIXED_TAG",
                "Description",
                mixedTypes,
                null,
                "admin-user"
        );

        assertThatThrownBy(() -> service.createTagDefinition(request))
                .as("List containing invalid EntityType '%s' should be rejected", invalidType)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(invalidType);
    }

    // ========== Arbitraries ==========

    /**
     * Generates strings that are NOT valid EntityType enum values.
     * Includes random alphabetic strings, lowercase variants of valid names,
     * and strings with special characters.
     */
    @Provide
    Arbitrary<String> invalidEntityTypeStrings() {
        // Random alphabetic strings that are unlikely to match enum values
        Arbitrary<String> randomStrings = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_', '-')
                .ofMinLength(1)
                .ofMaxLength(30)
                .filter(s -> !VALID_ENTITY_TYPES.contains(s));

        // Lowercase variants of valid enum names (e.g., "solution", "zone")
        Arbitrary<String> lowercaseVariants = Arbitraries.of(EntityType.values())
                .map(e -> e.name().toLowerCase());

        // Mixed-case variants (e.g., "Solution", "zOnE")
        Arbitrary<String> mixedCaseVariants = Arbitraries.of(EntityType.values())
                .map(e -> {
                    String name = e.name();
                    // Capitalize first letter only, lowercase rest
                    return name.charAt(0) + name.substring(1).toLowerCase();
                });

        return Arbitraries.oneOf(randomStrings, lowercaseVariants, mixedCaseVariants);
    }

    /**
     * Generates non-empty lists of valid EntityType enum value strings.
     */
    @Provide
    Arbitrary<List<String>> validEntityTypeLists() {
        return Arbitraries.of(EntityType.values())
                .map(Enum::name)
                .list()
                .ofMinSize(1)
                .ofMaxSize(3)
                .uniqueElements();
    }
}
