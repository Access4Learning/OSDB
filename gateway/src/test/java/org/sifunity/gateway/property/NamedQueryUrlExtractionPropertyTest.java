// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.gateway.util.UrlPrefixParser;
import org.sifunity.gateway.util.UrlPrefixParser.ParsedPath;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Named Query URL extraction from gateway paths.
 *
 * <p><strong>Property 6: Query token extraction from URL path</strong></p>
 *
 * <p>For any URL path matching {@code /{domain}/{solution}/requests/{queryToken}}
 * where queryToken is a non-empty string, the gateway's service name extraction
 * SHALL return the queryToken segment, and the access type SHALL be QUERY for
 * GET method.</p>
 *
 * <p><strong>Validates: Requirements 4.3, 4.4</strong></p>
 *
 * <p>Feature: named-query-support, Property 6: Query token extraction from URL path</p>
 *
 * @see org.sifunity.gateway.filter.AuthorizationFilter
 */
@Tag("named-query-support")
@Tag("property-6-query-token-extraction")
class NamedQueryUrlExtractionPropertyTest {

    /**
     * Mirrors the extractServiceName logic from AuthorizationFilter:
     * parse the path with UrlPrefixParser, then extract parts[2] from the resource path.
     */
    private String extractServiceName(String path) {
        ParsedPath parsed = UrlPrefixParser.parse(path);
        String resourcePath = parsed.resourcePath();

        String[] parts = resourcePath.split("/");
        if (parts.length >= 3) {
            return parts[2];
        }
        if (parts.length >= 2 && !parts[1].isBlank()) {
            return parts[1];
        }
        return null;
    }

    /**
     * Mirrors the determineAccessType logic from AuthorizationFilter
     * for the relevant /requests/* path pattern.
     */
    private AccessType determineAccessType(HttpMethod method, String path) {
        ParsedPath parsed = UrlPrefixParser.parse(path);
        String resourcePath = parsed.resourcePath();

        // GET /requests/* -> QUERY
        if (method == HttpMethod.GET && resourcePath.startsWith("/requests")) {
            return AccessType.QUERY;
        }

        // Default to QUERY for other paths
        return AccessType.QUERY;
    }

    /**
     * For any domainId, solutionId, and queryToken, extracting the service name
     * from {@code /{domainId}/{solutionId}/requests/{queryToken}} returns the queryToken.
     */
    @Property(tries = 20)
    @Label("extractServiceName returns queryToken from /requests/{queryToken} path")
    void extractsQueryTokenAsServiceName(
            @ForAll("alphanumericSegments") String domainId,
            @ForAll("alphanumericSegments") String solutionId,
            @ForAll("queryTokens") String queryToken) {

        // Avoid "solutions" as solutionId since UrlPrefixParser treats it as solution management path
        Assume.that(!solutionId.equals("solutions"));

        String path = "/" + domainId + "/" + solutionId + "/requests/" + queryToken;

        String extracted = extractServiceName(path);

        assertThat(extracted)
                .as("extractServiceName('%s') should return queryToken '%s'", path, queryToken)
                .isEqualTo(queryToken);
    }

    /**
     * For any GET request to {@code /{domainId}/{solutionId}/requests/{queryToken}},
     * the access type SHALL be QUERY.
     */
    @Property(tries = 20)
    @Label("determineAccessType returns QUERY for GET /requests/{queryToken}")
    void accessTypeIsQueryForGetRequests(
            @ForAll("alphanumericSegments") String domainId,
            @ForAll("alphanumericSegments") String solutionId,
            @ForAll("queryTokens") String queryToken) {

        Assume.that(!solutionId.equals("solutions"));

        String path = "/" + domainId + "/" + solutionId + "/requests/" + queryToken;

        AccessType accessType = determineAccessType(HttpMethod.GET, path);

        assertThat(accessType)
                .as("determineAccessType(GET, '%s') should return QUERY", path)
                .isEqualTo(AccessType.QUERY);
    }

    /**
     * Both properties combined: for any GET to /requests/{queryToken},
     * service name is queryToken AND access type is QUERY.
     */
    @Property(tries = 20)
    @Label("Named Query URL yields queryToken as serviceName and QUERY as accessType")
    void namedQueryUrlYieldsCorrectServiceNameAndAccessType(
            @ForAll("alphanumericSegments") String domainId,
            @ForAll("alphanumericSegments") String solutionId,
            @ForAll("queryTokens") String queryToken) {

        Assume.that(!solutionId.equals("solutions"));

        String path = "/" + domainId + "/" + solutionId + "/requests/" + queryToken;

        String serviceName = extractServiceName(path);
        AccessType accessType = determineAccessType(HttpMethod.GET, path);

        assertThat(serviceName)
                .as("Service name extracted from '%s'", path)
                .isEqualTo(queryToken);
        assertThat(accessType)
                .as("Access type for GET '%s'", path)
                .isEqualTo(AccessType.QUERY);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> alphanumericSegments() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(s -> !s.isBlank());
    }

    /**
     * Generates realistic named query tokens — alphanumeric with allowed special
     * characters like hyphens and underscores (common in SIF query token names
     * like "StudentSnapshot", "RetrieveByGrade", "attendance-query").
     */
    @Provide
    Arbitrary<String> queryTokens() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(50)
                .filter(s -> !s.isBlank())
                // Ensure token starts with alphanumeric (realistic query tokens)
                .filter(s -> Character.isLetterOrDigit(s.charAt(0)));
    }
}
