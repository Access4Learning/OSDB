// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.dto.TagDefinitionCreateRequest;
import org.sifunity.broker.environment.exception.DuplicateTagDefinitionException;
import org.sifunity.broker.environment.repository.TagDefinitionRepository;
import org.sifunity.broker.environment.service.TagDefinitionService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based test for TagDefinitionService duplicate name rejection.
 *
 * <p><strong>Property 3: Duplicate name rejection is case-insensitive</strong></p>
 *
 * <p>For any existing TagDefinition with tagName T, attempting to create another
 * TagDefinition with any case variation of T (e.g., lowercase, mixed case) SHALL
 * result in a DuplicateTagDefinitionException.</p>
 *
 * <p><strong>Validates: Requirements 3.2</strong></p>
 *
 * <p>Feature: tag-management-admin, Property 3: Duplicate name rejection is case-insensitive</p>
 */
class DuplicateTagDefinitionNameRejectionPropertyTest {

    /**
     * Property 3: Duplicate name rejection is case-insensitive
     *
     * <p><strong>Validates: Requirements 3.2</strong></p>
     *
     * <p>For any existing TagDefinition with tagName T, attempting to create another
     * TagDefinition with any case variation of T SHALL result in a
     * DuplicateTagDefinitionException.</p>
     */
    @Property(tries = 20)
    @Tag("tag-management-admin")
    @Tag("property-3-duplicate-name-rejection")
    void createWithExistingNameCaseVariationThrowsDuplicateException(
            @ForAll("tagNamesWithCaseVariations") TagNamePair tagNamePair
    ) {
        // Set up mocks fresh for each iteration
        TagDefinitionRepository repository = mock(TagDefinitionRepository.class);
        TagDefinitionService service = new TagDefinitionService(repository);

        // Mock: a definition already exists with this name (case-insensitive)
        when(repository.existsByTagNameIgnoreCase(anyString())).thenReturn(true);

        // Build a valid create request using the case variation
        TagDefinitionCreateRequest request = new TagDefinitionCreateRequest(
                tagNamePair.caseVariation(),
                "Some description",
                List.of(EntityType.SOLUTION.name()),
                null,
                "admin-user"
        );

        // ACT + ASSERT: Creating with a case variation of an existing name throws
        assertThatThrownBy(() -> service.createTagDefinition(request))
                .isInstanceOf(DuplicateTagDefinitionException.class);
    }

    // ========== Arbitraries ==========

    /**
     * Generates pairs of (original tag name, case variation) where the case variation
     * differs in casing but represents the same logical name.
     */
    @Provide
    Arbitrary<TagNamePair> tagNamesWithCaseVariations() {
        Arbitrary<String> baseNames = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_')
                .ofMinLength(1)
                .ofMaxLength(50)
                .filter(s -> s.chars().anyMatch(Character::isLetter)); // ensure at least one letter for case variation

        return baseNames.flatMap(original -> {
            // Generate a case variation: randomly flip case of each character
            Arbitrary<String> variations = Arbitraries.of(
                    original.toLowerCase(),
                    original.toUpperCase(),
                    mixCase(original)
            );
            return variations.map(variation -> new TagNamePair(original, variation));
        });
    }

    /**
     * Creates a mixed-case version of the input by alternating upper/lower case.
     */
    private static String mixCase(String input) {
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (i % 2 == 0) {
                chars[i] = Character.toUpperCase(chars[i]);
            } else {
                chars[i] = Character.toLowerCase(chars[i]);
            }
        }
        return new String(chars);
    }

    /**
     * Record holding an original tag name and a case variation of it.
     */
    record TagNamePair(String original, String caseVariation) {}
}
