// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.domain.TagDefinition;
import org.sifunity.broker.environment.dto.TagDefinitionCreateRequest;
import org.sifunity.broker.environment.exception.DuplicateTagDefinitionException;
import org.sifunity.broker.environment.repository.TagDefinitionRepository;
import org.sifunity.broker.environment.service.TagDefinitionService;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for case-insensitive duplicate name rejection.
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
class TagDefinitionDuplicateNameRejectionPropertyTest {

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
    void duplicateNameRejectionIsCaseInsensitive(
            @ForAll("tagNamesWithCaseVariation") TagNamePair tagNamePair
    ) {
        // Set up mocks fresh for each iteration
        TagDefinitionRepository repository = mock(TagDefinitionRepository.class);
        TagDefinitionService service = new TagDefinitionService(repository);

        // The first name was already "persisted" — repository reports it exists
        when(repository.existsByTagNameIgnoreCase(any())).thenReturn(true);

        // Build a create request using the case variation of the original name
        TagDefinitionCreateRequest duplicateRequest = new TagDefinitionCreateRequest(
                tagNamePair.caseVariation(),
                "some description",
                List.of(EntityType.SOLUTION.name()),
                null,
                "admin"
        );

        // ACT & ASSERT: creating with a case variation must throw DuplicateTagDefinitionException
        assertThatThrownBy(() -> service.createTagDefinition(duplicateRequest))
                .isInstanceOf(DuplicateTagDefinitionException.class);

        // Verify the repository was queried for existence (case-insensitive)
        verify(repository).existsByTagNameIgnoreCase(tagNamePair.caseVariation());

        // Verify save was never called — the duplicate was rejected before persistence
        verify(repository, never()).save(any());
    }

    // ========== Supporting Types ==========

    /**
     * Holds an original tag name and a case variation of it.
     */
    record TagNamePair(String original, String caseVariation) {}

    // ========== Arbitraries ==========

    /**
     * Generates pairs of (original tag name, case variation of that name).
     * The case variation is produced by randomly toggling the case of each character,
     * ensuring the variation differs in case but is equivalent case-insensitively.
     */
    @Provide
    Arbitrary<TagNamePair> tagNamesWithCaseVariation() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_')
                .ofMinLength(1)
                .ofMaxLength(50)
                .filter(s -> s.chars().anyMatch(Character::isLetter)) // ensure at least one letter for case variation
                .flatMap(original -> Arbitraries.randoms().map(random -> {
                    // Generate a case variation by randomly toggling each letter's case
                    StringBuilder variation = new StringBuilder(original.length());
                    for (char c : original.toCharArray()) {
                        if (Character.isLetter(c)) {
                            variation.append(random.nextBoolean() ? Character.toUpperCase(c) : Character.toLowerCase(c));
                        } else {
                            variation.append(c);
                        }
                    }
                    return new TagNamePair(original, variation.toString());
                }));
    }
}
