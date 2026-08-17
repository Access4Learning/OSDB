// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.mockito.Mockito;
import org.sifunity.broker.environment.domain.TagDefinition;
import org.sifunity.broker.environment.dto.TagDefinitionDTO;
import org.sifunity.broker.environment.repository.TagDefinitionRepository;
import org.sifunity.broker.environment.service.TagDefinitionService;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Property-based test for TagDefinition list ordering.
 *
 * <p><strong>Property 6: List ordering by tag_name</strong></p>
 *
 * <p>For any set of persisted TagDefinitions, listing all definitions SHALL return
 * them in ascending alphabetical order by tag_name.</p>
 *
 * <p><strong>Validates: Requirements 4.1</strong></p>
 *
 * <p>This test uses a mocked TagDefinitionRepository to verify that the service
 * layer returns definitions in the order provided by the repository's
 * {@code findAllByOrderByTagNameAsc()} method, which guarantees alphabetical
 * ordering by tag_name.</p>
 */
class TagDefinitionListOrderingPropertyTest {

    /**
     * Property 6: List ordering by tag_name
     *
     * <p><strong>Validates: Requirements 4.1</strong></p>
     *
     * <p>For any set of persisted TagDefinitions, listing all definitions SHALL
     * return them in ascending alphabetical order by tag_name.</p>
     */
    @Property(tries = 20)
    @Tag("Feature: tag-management-admin, Property 6: List ordering by tag_name")
    void listTagDefinitionsReturnsResultsInAscendingOrderByTagName(
            @ForAll("tagDefinitionLists") List<TagDefinition> unsortedDefinitions) {

        // Sort definitions by tag_name ascending (simulating what the repository query does)
        List<TagDefinition> sortedDefinitions = unsortedDefinitions.stream()
                .sorted(Comparator.comparing(TagDefinition::getTagName))
                .collect(Collectors.toList());

        // Mock repository to return sorted list (as the real DB query would)
        TagDefinitionRepository repository = Mockito.mock(TagDefinitionRepository.class);
        when(repository.findAllByOrderByTagNameAsc()).thenReturn(sortedDefinitions);

        TagDefinitionService service = new TagDefinitionService(repository);

        // Act
        List<TagDefinitionDTO> result = service.listTagDefinitions(null);

        // Assert: result is in ascending alphabetical order by tagName
        assertThat(result).isNotNull();
        assertThat(result).hasSize(sortedDefinitions.size());

        for (int i = 0; i < result.size() - 1; i++) {
            String current = result.get(i).tagName();
            String next = result.get(i + 1).tagName();
            assertThat(current.compareTo(next))
                    .as("Tag '%s' at index %d should come before or equal '%s' at index %d",
                            current, i, next, i + 1)
                    .isLessThanOrEqualTo(0);
        }
    }

    /**
     * Property 6: List ordering — result tag names match sorted input
     *
     * <p><strong>Validates: Requirements 4.1</strong></p>
     *
     * <p>The tag names in the returned list SHALL exactly match the tag names
     * of the input sorted alphabetically ascending.</p>
     */
    @Property(tries = 20)
    @Tag("Feature: tag-management-admin, Property 6: List ordering by tag_name")
    void listTagDefinitionsTagNamesMatchSortedInput(
            @ForAll("tagDefinitionLists") List<TagDefinition> unsortedDefinitions) {

        // Sort definitions by tag_name ascending
        List<TagDefinition> sortedDefinitions = unsortedDefinitions.stream()
                .sorted(Comparator.comparing(TagDefinition::getTagName))
                .collect(Collectors.toList());

        TagDefinitionRepository repository = Mockito.mock(TagDefinitionRepository.class);
        when(repository.findAllByOrderByTagNameAsc()).thenReturn(sortedDefinitions);

        TagDefinitionService service = new TagDefinitionService(repository);

        // Act
        List<TagDefinitionDTO> result = service.listTagDefinitions(null);

        // Assert: tag names in result match sorted order
        List<String> resultNames = result.stream()
                .map(TagDefinitionDTO::tagName)
                .toList();

        List<String> expectedNames = sortedDefinitions.stream()
                .map(TagDefinition::getTagName)
                .toList();

        assertThat(resultNames).isEqualTo(expectedNames);
    }

    // ========== Arbitraries ==========

    /**
     * Generates lists of TagDefinition entities with unique uppercase tag names.
     * Names are uppercase because the entity's @PrePersist hook uppercases them.
     */
    @Provide
    Arbitrary<List<TagDefinition>> tagDefinitionLists() {
        Arbitrary<String> uppercaseTagNames = Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withChars('_')
                .ofMinLength(1)
                .ofMaxLength(30)
                .filter(s -> !s.isBlank());

        return uppercaseTagNames.list()
                .ofMinSize(0)
                .ofMaxSize(20)
                .uniqueElements()
                .map(names -> names.stream()
                        .map(this::buildTagDefinition)
                        .collect(Collectors.toList()));
    }

    private TagDefinition buildTagDefinition(String tagName) {
        TagDefinition td = new TagDefinition();
        td.setId(UUID.randomUUID());
        td.setTagName(tagName);
        td.setTagDescription("Description for " + tagName);
        td.setApplicableEntityTypes(List.of("SOLUTION"));
        td.setAllowedValues(null);
        td.setCreatedBy("test-user");
        td.setCreatedAt(Instant.now());
        td.setUpdatedAt(Instant.now());
        return td;
    }
}
