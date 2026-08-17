// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.sifunity.gateway.util.UrlPrefixParser;
import org.sifunity.gateway.util.UrlPrefixParser.ParsedPath;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for {@link UrlPrefixParser}.
 *
 * <p><strong>Property 1: URL Path Parsing Correctness</strong></p>
 *
 * <p>For any valid URL path containing a domain/solution prefix
 * ({@code /{domainId}/{solutionId}/{resource}/...}), the {@code UrlPrefixParser}
 * shall correctly extract the {@code domainId}, {@code solutionId}, and
 * {@code resourcePath} segments. For domain-management paths ({@code /domains/...}),
 * {@code domainId} and {@code solutionId} shall be null. For solution-management
 * paths ({@code /{domainId}/solutions/...}), {@code solutionId} shall be null and
 * {@code domainId} shall match the first segment.</p>
 *
 * <p><strong>Validates: Requirements 3.12, 4.7, 4.8, 5.4, 5.5</strong></p>
 */
@Tag("domain-solution-url-prefix")
@Tag("property-1-url-path-parsing-correctness")
class UrlPrefixParserPropertyTest {

    // ========== Property: Resource path extraction correctness ==========

    /**
     * For any random domainId, solutionId, and resource path, parsing
     * {@code /{domainId}/{solutionId}/{resource}} correctly extracts all three
     * components and the original path can be reconstructed.
     *
     * <p><strong>Validates: Requirements 3.12, 5.4, 5.5</strong></p>
     */
    @Property(tries = 200)
    @Label("Resource paths: domainId, solutionId, and resourcePath are correctly extracted")
    void resourcePathParsingCorrectness(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("validResourcePaths") String resource) {

        // Ensure solutionId is not "solutions" to avoid tier-2 matching
        Assume.that(!solutionId.equals("solutions"));

        String fullPath = "/" + domainId + "/" + solutionId + resource;

        ParsedPath result = UrlPrefixParser.parse(fullPath);

        assertThat(result.domainId())
                .as("domainId extracted from '%s'", fullPath)
                .isEqualTo(domainId);
        assertThat(result.solutionId())
                .as("solutionId extracted from '%s'", fullPath)
                .isEqualTo(solutionId);
        assertThat(result.resourcePath())
                .as("resourcePath extracted from '%s'", fullPath)
                .isEqualTo(resource.isEmpty() ? "/" : resource);
        assertThat(result.isResourcePath())
                .as("isResourcePath() for '%s'", fullPath)
                .isTrue();
        assertThat(result.isDomainManagementPath()).isFalse();
        assertThat(result.isSolutionManagementPath()).isFalse();
    }

    // ========== Property: Domain management paths ==========

    /**
     * For any suffix, parsing {@code /domains/{suffix}} returns null domainId
     * and null solutionId, and the full path as resourcePath.
     *
     * <p><strong>Validates: Requirements 4.7</strong></p>
     */
    @Property(tries = 100)
    @Label("Domain management paths: domainId and solutionId are null")
    void domainManagementPathsReturnNulls(
            @ForAll("domainManagementSuffixes") String suffix) {

        String fullPath = "/domains" + suffix;

        ParsedPath result = UrlPrefixParser.parse(fullPath);

        assertThat(result.domainId())
                .as("domainId for domain-management path '%s'", fullPath)
                .isNull();
        assertThat(result.solutionId())
                .as("solutionId for domain-management path '%s'", fullPath)
                .isNull();
        assertThat(result.resourcePath())
                .as("resourcePath for domain-management path '%s'", fullPath)
                .isEqualTo(fullPath);
        assertThat(result.isDomainManagementPath()).isTrue();
        assertThat(result.isSolutionManagementPath()).isFalse();
        assertThat(result.isResourcePath()).isFalse();
    }

    // ========== Property: Solution management paths ==========

    /**
     * For any domainId and solution suffix, parsing
     * {@code /{domainId}/solutions/{suffix}} returns the correct domainId,
     * null solutionId, and the resource path starting from /solutions.
     *
     * <p><strong>Validates: Requirements 4.8</strong></p>
     */
    @Property(tries = 200)
    @Label("Solution management paths: solutionId is null, domainId is correct")
    void solutionManagementPathsReturnNullSolutionId(
            @ForAll("validSegments") String domainId,
            @ForAll("solutionManagementSuffixes") String suffix) {

        // domainId must not be "domains" to avoid tier-1 matching
        Assume.that(!domainId.equals("domains"));
        // domainId must not be "actuator" to avoid actuator path matching
        Assume.that(!domainId.equals("actuator"));

        String fullPath = "/" + domainId + "/solutions" + suffix;

        ParsedPath result = UrlPrefixParser.parse(fullPath);

        assertThat(result.domainId())
                .as("domainId for solution-management path '%s'", fullPath)
                .isEqualTo(domainId);
        assertThat(result.solutionId())
                .as("solutionId for solution-management path '%s'", fullPath)
                .isNull();
        assertThat(result.resourcePath())
                .as("resourcePath for solution-management path '%s'", fullPath)
                .startsWith("/solutions");
        assertThat(result.isSolutionManagementPath()).isTrue();
        assertThat(result.isDomainManagementPath()).isFalse();
        assertThat(result.isResourcePath()).isFalse();
    }

    // ========== Property: Path reconstruction ==========

    /**
     * For any resource path, the original full path can be reconstructed from
     * the parsed components: {@code /{domainId}/{solutionId}{resourcePath}}.
     *
     * <p><strong>Validates: Requirements 3.12, 5.4, 5.5</strong></p>
     */
    @Property(tries = 200)
    @Label("Resource paths: original path can be reconstructed from parsed components")
    void resourcePathReconstruction(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("validResourcePaths") String resource) {

        Assume.that(!solutionId.equals("solutions"));

        String fullPath = "/" + domainId + "/" + solutionId + resource;

        ParsedPath result = UrlPrefixParser.parse(fullPath);

        String reconstructed = "/" + result.domainId() + "/" + result.solutionId() + result.resourcePath();

        // Handle the edge case where resource is empty → resourcePath becomes "/"
        // and the trailing slash in the original path may differ
        if (resource.isEmpty()) {
            assertThat(reconstructed).isEqualTo("/" + domainId + "/" + solutionId + "/");
        } else {
            assertThat(reconstructed).isEqualTo(fullPath);
        }
    }

    // ========== Property: Tier classification is mutually exclusive ==========

    /**
     * For any parseable path, exactly one of isDomainManagementPath(),
     * isSolutionManagementPath(), or isResourcePath() is true — or all are false
     * for edge cases (null/blank/single-segment).
     *
     * <p><strong>Validates: Requirements 3.12</strong></p>
     */
    @Property(tries = 200)
    @Label("Tier classification: at most one tier flag is true")
    void tierClassificationMutuallyExclusive(
            @ForAll("anyValidPath") String path) {

        ParsedPath result = UrlPrefixParser.parse(path);

        int trueCount = 0;
        if (result.isDomainManagementPath()) trueCount++;
        if (result.isSolutionManagementPath()) trueCount++;
        if (result.isResourcePath()) trueCount++;

        assertThat(trueCount)
                .as("Exactly one tier flag should be true for path '%s', got %d", path, trueCount)
                .isLessThanOrEqualTo(1);
    }

    // ========== Arbitraries ==========

    /**
     * Generates valid URL path segments (alphanumeric with hyphens/underscores).
     * These represent domainId or solutionId values.
     */
    @Provide
    Arbitrary<String> validSegments() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(30)
                .filter(s -> !s.isBlank());
    }

    /**
     * Generates valid resource path suffixes (e.g., "/environments", "/queues/123/messages").
     * Always starts with "/" or is empty (for the /{domainId}/{solutionId} only case).
     */
    @Provide
    Arbitrary<String> validResourcePaths() {
        Arbitrary<String> segment = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(20);

        // Empty resource (just /{domainId}/{solutionId})
        Arbitrary<String> empty = Arbitraries.just("");

        // Single resource: /environments, /queues, etc.
        Arbitrary<String> single = segment.map(s -> "/" + s);

        // Two segments: /queues/123
        Arbitrary<String> two = Combinators.combine(segment, segment)
                .as((a, b) -> "/" + a + "/" + b);

        // Three segments: /queues/123/messages
        Arbitrary<String> three = Combinators.combine(segment, segment, segment)
                .as((a, b, c) -> "/" + a + "/" + b + "/" + c);

        return Arbitraries.oneOf(empty, single, two, three);
    }

    /**
     * Generates suffixes for domain management paths (/domains{suffix}).
     * E.g., "", "/", "/iowa", "/iowa/subresource".
     */
    @Provide
    Arbitrary<String> domainManagementSuffixes() {
        Arbitrary<String> segment = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(20);

        return Arbitraries.oneOf(
                Arbitraries.just(""),
                Arbitraries.just("/"),
                segment.map(s -> "/" + s),
                Combinators.combine(segment, segment).as((a, b) -> "/" + a + "/" + b)
        );
    }

    /**
     * Generates suffixes for solution management paths (/{domainId}/solutions{suffix}).
     * E.g., "", "/", "/production", "/production/details".
     */
    @Provide
    Arbitrary<String> solutionManagementSuffixes() {
        Arbitrary<String> segment = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(20);

        return Arbitraries.oneOf(
                Arbitraries.just(""),
                Arbitraries.just("/"),
                segment.map(s -> "/" + s),
                Combinators.combine(segment, segment).as((a, b) -> "/" + a + "/" + b)
        );
    }

    /**
     * Generates any valid path across all three tiers for mutual exclusivity testing.
     */
    @Provide
    Arbitrary<String> anyValidPath() {
        Arbitrary<String> segment = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(15);

        // Domain management paths
        Arbitrary<String> domainPaths = Arbitraries.oneOf(
                Arbitraries.just("/domains"),
                segment.map(s -> "/domains/" + s)
        );

        // Solution management paths
        Arbitrary<String> solutionPaths = segment.map(d -> "/" + d + "/solutions");

        // Resource paths (ensure solutionId != "solutions")
        Arbitrary<String> resourcePaths = Combinators.combine(segment, segment, segment)
                .as((d, s, r) -> "/" + d + "/" + s + "/" + r)
                .filter(p -> {
                    String[] parts = p.split("/", -1);
                    return parts.length >= 4 && !parts[2].equals("solutions");
                });

        // Edge cases
        Arbitrary<String> edgeCases = Arbitraries.of(
                "/", "", "/actuator/health", "/something"
        );

        return Arbitraries.oneOf(domainPaths, solutionPaths, resourcePaths, edgeCases);
    }
}
