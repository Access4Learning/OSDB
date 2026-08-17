// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for connector RewritePath regex patterns.
 *
 * <p><strong>Property 6: Connector RewritePath Preserves Prefix</strong></p>
 *
 * <p>For any {@code domainId}, {@code solutionId}, connector type (requests,
 * responses, events, services), and path segment, the gateway RewritePath shall
 * transform {@code /{domainId}/{solutionId}/{connector}/{segment}} to
 * {@code /{domainId}/{solutionId}/api/v1/{connector}/{segment}}, preserving the
 * domain and solution prefix segments exactly.</p>
 *
 * <p><strong>Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5</strong></p>
 */
@Tag("domain-solution-url-prefix")
@Tag("property-6-connector-rewritepath-preserves-prefix")
class ConnectorRewritePathPropertyTest {

    /**
     * The regex pattern used by Spring Cloud Gateway RewritePath filter for connectors.
     * This is the same pattern defined in application-local.yml for each connector route.
     * The pattern captures domainId, solutionId, and the trailing segment.
     *
     * <p>Example from config:
     * {@code RewritePath=/(?<domainId>[^/]+)/(?<solutionId>[^/]+)/events/(?<segment>.*),
     * /${domainId}/${solutionId}/api/v1/events/${segment}}</p>
     *
     * We parameterize the connector name to test all four connector types with one pattern.
     */
    private static final String REWRITE_REGEX_TEMPLATE =
            "/(?<domainId>[^/]+)/(?<solutionId>[^/]+)/%s/(?<segment>.*)";

    private static final String REPLACEMENT_TEMPLATE =
            "/${domainId}/${solutionId}/api/v1/%s/${segment}";

    // ========== Property: RewritePath preserves domain/solution prefix ==========

    /**
     * For any random domainId, solutionId, connector type, and path segment,
     * the RewritePath regex transforms the input path to include {@code /api/v1/}
     * between the solution prefix and the connector, while preserving domainId
     * and solutionId exactly.
     *
     * <p><strong>Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5</strong></p>
     */
    @Property(tries = 200)
    @Label("RewritePath preserves domainId and solutionId and inserts /api/v1/ before connector")
    void rewritePathPreservesPrefixAndInsertsApiVersion(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("connectorTypes") String connector,
            @ForAll("pathSegments") String segment) {

        String inputPath = "/" + domainId + "/" + solutionId + "/" + connector + "/" + segment;

        String regex = String.format(REWRITE_REGEX_TEMPLATE, connector);
        String replacement = String.format(REPLACEMENT_TEMPLATE, connector);

        String result = applyRewritePath(inputPath, regex, replacement);

        String expectedPath = "/" + domainId + "/" + solutionId + "/api/v1/" + connector + "/" + segment;

        assertThat(result)
                .as("RewritePath of '%s' with connector '%s'", inputPath, connector)
                .isEqualTo(expectedPath);
    }

    // ========== Property: domainId is preserved exactly ==========

    /**
     * The domainId segment in the rewritten path is identical to the original.
     *
     * <p><strong>Validates: Requirements 7.5</strong></p>
     */
    @Property(tries = 200)
    @Label("RewritePath output contains the exact original domainId as first segment")
    void rewrittenPathContainsExactDomainId(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("connectorTypes") String connector,
            @ForAll("pathSegments") String segment) {

        String inputPath = "/" + domainId + "/" + solutionId + "/" + connector + "/" + segment;

        String regex = String.format(REWRITE_REGEX_TEMPLATE, connector);
        String replacement = String.format(REPLACEMENT_TEMPLATE, connector);

        String result = applyRewritePath(inputPath, regex, replacement);

        // Extract the first segment from the rewritten path
        String[] resultSegments = result.split("/", -1);
        // resultSegments[0] is empty (leading slash), [1] is domainId
        assertThat(resultSegments[1])
                .as("domainId preserved in rewritten path for input '%s'", inputPath)
                .isEqualTo(domainId);
    }

    // ========== Property: solutionId is preserved exactly ==========

    /**
     * The solutionId segment in the rewritten path is identical to the original.
     *
     * <p><strong>Validates: Requirements 7.5</strong></p>
     */
    @Property(tries = 200)
    @Label("RewritePath output contains the exact original solutionId as second segment")
    void rewrittenPathContainsExactSolutionId(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("connectorTypes") String connector,
            @ForAll("pathSegments") String segment) {

        String inputPath = "/" + domainId + "/" + solutionId + "/" + connector + "/" + segment;

        String regex = String.format(REWRITE_REGEX_TEMPLATE, connector);
        String replacement = String.format(REPLACEMENT_TEMPLATE, connector);

        String result = applyRewritePath(inputPath, regex, replacement);

        // Extract the second segment from the rewritten path
        String[] resultSegments = result.split("/", -1);
        // resultSegments[0] is empty (leading slash), [1] is domainId, [2] is solutionId
        assertThat(resultSegments[2])
                .as("solutionId preserved in rewritten path for input '%s'", inputPath)
                .isEqualTo(solutionId);
    }

    // ========== Property: /api/v1/ is inserted ==========

    /**
     * The rewritten path always contains {@code /api/v1/{connector}/} after the
     * solution prefix.
     *
     * <p><strong>Validates: Requirements 7.1, 7.2, 7.3, 7.4</strong></p>
     */
    @Property(tries = 200)
    @Label("RewritePath output contains /api/v1/{connector}/ after the domain/solution prefix")
    void rewrittenPathContainsApiV1Connector(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("connectorTypes") String connector,
            @ForAll("pathSegments") String segment) {

        String inputPath = "/" + domainId + "/" + solutionId + "/" + connector + "/" + segment;

        String regex = String.format(REWRITE_REGEX_TEMPLATE, connector);
        String replacement = String.format(REPLACEMENT_TEMPLATE, connector);

        String result = applyRewritePath(inputPath, regex, replacement);

        String expectedMiddle = "/api/v1/" + connector + "/";
        assertThat(result)
                .as("Rewritten path contains '/api/v1/%s/' for input '%s'", connector, inputPath)
                .contains(expectedMiddle);
    }

    // ========== Property: regex matches the input path ==========

    /**
     * The RewritePath regex always matches a well-formed connector input path.
     *
     * <p><strong>Validates: Requirements 7.1, 7.2, 7.3, 7.4</strong></p>
     */
    @Property(tries = 200)
    @Label("RewritePath regex matches any well-formed /{domainId}/{solutionId}/{connector}/{segment} path")
    void regexMatchesWellFormedConnectorPath(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("connectorTypes") String connector,
            @ForAll("pathSegments") String segment) {

        String inputPath = "/" + domainId + "/" + solutionId + "/" + connector + "/" + segment;

        String regex = String.format(REWRITE_REGEX_TEMPLATE, connector);
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(inputPath);

        assertThat(matcher.matches())
                .as("Regex '%s' should match input path '%s'", regex, inputPath)
                .isTrue();

        assertThat(matcher.group("domainId")).isEqualTo(domainId);
        assertThat(matcher.group("solutionId")).isEqualTo(solutionId);
        assertThat(matcher.group("segment")).isEqualTo(segment);
    }

    // ========== Helper: Apply RewritePath ==========

    /**
     * Simulates Spring Cloud Gateway's RewritePath filter behavior.
     * Uses Java's {@link String#replaceAll(String, String)} which is what
     * Spring Cloud Gateway's RewritePathGatewayFilterFactory uses internally.
     */
    private String applyRewritePath(String path, String regex, String replacement) {
        return path.replaceAll(regex, replacement);
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
     * Generates the four connector types used in gateway RewritePath rules.
     */
    @Provide
    Arbitrary<String> connectorTypes() {
        return Arbitraries.of("events", "requests", "responses", "services");
    }

    /**
     * Generates path segments that appear after the connector name.
     * These can be empty or multi-segment paths like "abc/123/def".
     * Segments do not contain slashes at the start.
     */
    @Provide
    Arbitrary<String> pathSegments() {
        Arbitrary<String> singleSegment = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(20);

        // Single segment: "abc"
        Arbitrary<String> single = singleSegment;

        // Two segments: "abc/123"
        Arbitrary<String> two = Combinators.combine(singleSegment, singleSegment)
                .as((a, b) -> a + "/" + b);

        // Three segments: "abc/123/def"
        Arbitrary<String> three = Combinators.combine(singleSegment, singleSegment, singleSegment)
                .as((a, b, c) -> a + "/" + b + "/" + c);

        return Arbitraries.oneOf(single, two, three);
    }
}
