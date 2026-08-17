// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.mockito.Mockito;
import org.sifunity.broker.environment.domain.EntityType;
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
 * Property-based test for TagDefinition entityType filter correctness.
 *
 * <p><strong>Property 7: EntityType filter correctness</strong></p>
 *
 * <p>For any entityType filter value and any set of persisted TagDefinitions,
 * all definitions returned by the filtered list SHALL contain that entityType
 * in their applicableEntityTypes array, and no definition containing that
 * entityType SHALL be omitted.</p>
 *
 * <p><strong>Validates: Requirements 4.4</strong></p>
 */
@Tag("tag-management-admin")
@Tag("property-7-entity-type-filter-correctness")
class TagDefinitionEntityTypeFilterPropertyTest {

    private static final List<String> ALL_ENTITY_TYPES = List.of(
            EntityType.SOLUTION.name(),
            EntityType.ZONE.name(),
            EntityType.CONTEXT.name(),
            EntityType.ENVIRONMENT.name(),
            EntityType.QUEUE.name()
    );

    /**
     * Property 7: All returned definitions contain the filter entityType.
     *
     * <p><strong>Validates: Requirements 4.4</strong></p>
     *
     * <p>When listing tag definitions with an entityType filter, every returned
     * definition SHALL have that entityType in its applicableEntityTypes array.</p>
     */
    @Property(tries = 20)
    @Tag("tag-management-admin")
    @Tag("property-7-entity-type-filter-correctness")
    void filteredListOnlyContainsDefinitionsWithMatchingEntityType(
            @ForAll("entityTypeFilter") String filterType,
            @ForAll("tagDefinitionLists") List<TagDefinition> allDefinitions) {

        // Compute what the repository should return (matching definitions only)
        List<TagDefinition> matchingDefinitions = allDefinitions.stream()
                .filter(td -> td.getApplicableEntityTypes().contains(filterType))
                .sorted(Comparator.comparing(TagDefinition::getTagName))
                .collect(Collectors.toList());

        TagDefinitionRepository repository = Mockito.mock(TagDefinitionRepository.class);
        when(repository.findByApplicableEntityType(filterType))
                .thenReturn(matchingDefinitions);

        TagDefinitionService service = new TagDefinitionService(repository);

        // Act
        List<TagDefinitionDTO> result = service.listTagDefinitions(filterType);

        // Assert: every returned definition contains the filter type
        assertThat(result).isNotNull();
        for (TagDefinitionDTO dto : result) {
            assertThat(dto.applicableEntityTypes())
                    .as("Definition '%s' should contain entityType '%s'",
                            dto.tagName(), filterType)
                    .contains(filterType);
        }
    }

    /**
     * Property 7: No matching definition is omitted from the filtered result.
     *
     * <p><strong>Validates: Requirements 4.4</strong></p>
     *
     * <p>When listing tag definitions with an entityType filter, no definition
     * whose applicableEntityTypes contains that type SHALL be excluded from
     * the result.</p>
     */
    @Property(tries = 20)
    @Tag("tag-management-admin")
    @Tag("property-7-entity-type-filter-correctness")
    void filteredListDoesNotOmitDefinitionsWithMatchingEntityType(
            @ForAll("entityTypeFilter") String filterType,
            @ForAll("tagDefinitionLists") List<TagDefinition> allDefinitions) {

        // Compute expected matching definitions
        List<TagDefinition> matchingDefinitions = allDefinitions.stream()
                .filter(td -> td.getApplicableEntityTypes().contains(filterType))
                .sorted(Comparator.comparing(TagDefinition::getTagName))
                .collect(Collectors.toList());

        TagDefinitionRepository repository = Mockito.mock(TagDefinitionRepository.class);
        when(repository.findByApplicableEntityType(filterType))
                .thenReturn(matchingDefinitions);

        TagDefinitionService service = new TagDefinitionService(repository);

        // Act
        List<TagDefinitionDTO> result = service.listTagDefinitions(filterType);

        // Assert: result size matches expected matching count
        assertThat(result).hasSize(matchingDefinitions.size());

        // Assert: all expected tag names are present in result
        Set<String> resultNames = result.stream()
                .map(TagDefinitionDTO::tagName)
                .collect(Collectors.toSet());

        Set<String> expectedNames = matchingDefinitions.stream()
                .map(TagDefinition::getTagName)
                .collect(Collectors.toSet());

        assertThat(resultNames).isEqualTo(expectedNames);
    }

    /**
     * Property 7: Definitions that don't include the filter type are excluded.
     *
     * <p><strong>Validates: Requirements 4.4</strong></p>
     *
     * <p>When listing tag definitions with an entityType filter, definitions
     * whose applicableEntityTypes does NOT contain the filter type SHALL NOT
     * appear in the result.</p>
     */
    @Property(tries = 20)
    @Tag("tag-management-admin")
    @Tag("property-7-entity-type-filter-correctness")
    void filteredListExcludesDefinitionsWithoutMatchingEntityType(
            @ForAll("entityTypeFilter") String filterType,
            @ForAll("tagDefinitionLists") List<TagDefinition> allDefinitions) {

        // The repository correctly returns only matching definitions
        List<TagDefinition> matchingDefinitions = allDefinitions.stream()
                .filter(td -> td.getApplicableEntityTypes().contains(filterType))
                .sorted(Comparator.comparing(TagDefinition::getTagName))
                .collect(Collectors.toList());

        // Non-matching definitions should NOT appear
        Set<String> nonMatchingNames = allDefinitions.stream()
                .filter(td -> !td.getApplicableEntityTypes().contains(filterType))
                .map(TagDefinition::getTagName)
                .collect(Collectors.toSet());

        TagDefinitionRepository repository = Mockito.mock(TagDefinitionRepository.class);
        when(repository.findByApplicableEntityType(filterType))
                .thenReturn(matchingDefinitions);

        TagDefinitionService service = new TagDefinitionService(repository);

        // Act
        List<TagDefinitionDTO> result = service.listTagDefinitions(filterType);

        // Assert: no non-matching definition appears in result
        Set<String> resultNames = result.stream()
                .map(TagDefinitionDTO::tagName)
                .collect(Collectors.toSet());

        for (String nonMatchingName : nonMatchingNames) {
            assertThat(resultNames)
                    .as("Definition '%s' should NOT appear in filtered results for type '%s'",
                            nonMatchingName, filterType)
                    .doesNotContain(nonMatchingName);
        }
    }

    // ========== Arbitraries ==========

    /**
     * Provides a random EntityType filter value from the valid enum values.
     */
    @Provide
    Arbitrary<String> entityTypeFilter() {
        return Arbitraries.of(ALL_ENTITY_TYPES);
    }

    /**
     * Generates lists of TagDefinition entities with unique uppercase tag names
     * and random subsets of EntityType values as applicableEntityTypes.
     */
    @Provide
    Arbitrary<List<TagDefinition>> tagDefinitionLists() {
        Arbitrary<String> uppercaseTagNames = Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withChars('_')
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(s -> !s.isBlank());

        Arbitrary<List<String>> entityTypeSubsets = Arbitraries.of(ALL_ENTITY_TYPES)
                .list()
                .ofMinSize(1)
                .ofMaxSize(5)
                .uniqueElements();

        return Combinators.combine(
                uppercaseTagNames.list().ofMinSize(0).ofMaxSize(10).uniqueElements(),
                entityTypeSubsets.list().ofMinSize(0).ofMaxSize(10)
        ).as((names, typeLists) -> {
            List<TagDefinition> definitions = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                List<String> types = i < typeLists.size() ? typeLists.get(i) : List.of("SOLUTION");
                definitions.add(buildTagDefinition(names.get(i), types));
            }
            return definitions;
        });
    }

    private TagDefinition buildTagDefinition(String tagName, List<String> applicableEntityTypes) {
        TagDefinition td = new TagDefinition();
        td.setId(UUID.randomUUID());
        td.setTagName(tagName);
        td.setTagDescription("Description for " + tagName);
        td.setApplicableEntityTypes(applicableEntityTypes);
        td.setAllowedValues(null);
        td.setCreatedBy("test-user");
        td.setCreatedAt(Instant.now());
        td.setUpdatedAt(Instant.now());
        return td;
    }
}
