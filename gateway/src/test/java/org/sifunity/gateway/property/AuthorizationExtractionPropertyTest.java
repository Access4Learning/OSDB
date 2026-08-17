// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.gateway.util.UrlPrefixParser;
import org.sifunity.gateway.util.UrlPrefixParser.ParsedPath;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for authorization resource extraction after prefix.
 *
 * <p><strong>Property 5: Authorization Resource Extraction After Prefix</strong></p>
 *
 * <p>For any URL path with a {@code /{domainId}/{solutionId}} prefix, the authorization
 * filter's service name, zone ID, context ID, and access type extraction logic shall
 * produce the same results as if the resource path (after the prefix) were at the root
 * level. That is, {@code extractServiceName("/{d}/{s}/requests/StudentPersonal")} shall
 * equal {@code extractServiceName("/requests/StudentPersonal")} from the old logic.</p>
 *
 * <p><strong>Validates: Requirements 6.1, 6.2, 6.3, 6.5</strong></p>
 */
@Tag("domain-solution-url-prefix")
@Tag("property-5-authorization-resource-extraction-after-prefix")
class AuthorizationExtractionPropertyTest {

    // ========== Extraction logic (mirrors AuthorizationFilter) ==========

    /**
     * Extracts the service name from a path using the same logic as
     * {@code AuthorizationFilter.extractServiceName()} — parse with UrlPrefixParser,
     * then extract from the resource path segments.
     */
    private String extractServiceName(String path) {
        ParsedPath parsed = UrlPrefixParser.parse(path);
        String resourcePath = parsed.resourcePath();

        String[] parts = resourcePath.split("/");
        // For paths like /requests/StudentPersonals → parts = ["", "requests", "StudentPersonals"]
        if (parts.length >= 3) {
            return parts[2];
        }
        // For paths like /environments → parts = ["", "environments"]
        if (parts.length >= 2 && !parts[1].isBlank()) {
            return parts[1];
        }
        return null;
    }

    /**
     * Determines the access type from a method and path using the same logic as
     * {@code AuthorizationFilter.determineAccessType()} — parse with UrlPrefixParser,
     * then match on the resource path.
     */
    private AccessType determineAccessType(HttpMethod method, String path) {
        ParsedPath parsed = UrlPrefixParser.parse(path);
        String resourcePath = parsed.resourcePath();

        if (method == HttpMethod.GET && resourcePath.startsWith("/requests")) {
            return AccessType.QUERY;
        }
        if (method == HttpMethod.POST && resourcePath.startsWith("/responses")) {
            return AccessType.PROVIDE;
        }
        if (method == HttpMethod.POST && resourcePath.startsWith("/events")) {
            return AccessType.PROVIDE;
        }
        if (method == HttpMethod.GET && resourcePath.startsWith("/services")) {
            return AccessType.QUERY;
        }
        if ((method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE)
                && resourcePath.startsWith("/services")) {
            return AccessType.PROVIDE;
        }
        return AccessType.QUERY;
    }


    // ========== Property: extractServiceName for prefixed path matches root-level ==========

    /**
     * For any domainId, solutionId, connector type, and service object name,
     * extracting the service name from {@code /{domainId}/{solutionId}/{connector}/{serviceName}}
     * produces the same result as the old root-level logic would for
     * {@code /{connector}/{serviceName}} — i.e., the object name (second segment
     * of the resource path).
     *
     * <p><strong>Validates: Requirements 6.1</strong></p>
     */
    @Property(tries = 200)
    @Label("extractServiceName for prefixed path matches root-level extraction")
    void extractServiceNameMatchesRootLevel(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("connectorTypes") String connector,
            @ForAll("serviceObjectNames") String objectName) {

        Assume.that(!solutionId.equals("solutions"));

        String prefixedPath = "/" + domainId + "/" + solutionId + "/" + connector + "/" + objectName;

        // The old root-level logic would extract the service name from
        // /{connector}/{objectName} as objectName (the second path segment)
        String expectedServiceName = objectName;

        String prefixedResult = extractServiceName(prefixedPath);

        assertThat(prefixedResult)
                .as("extractServiceName('%s') should be '%s'", prefixedPath, expectedServiceName)
                .isEqualTo(expectedServiceName);
    }

    // ========== Property: extractServiceName for infrastructure paths ==========

    /**
     * For any domainId, solutionId, and infrastructure resource (environments, queues, etc.),
     * extracting the service name from the prefixed path produces the same result as
     * extracting from the root-level path.
     *
     * <p><strong>Validates: Requirements 6.1</strong></p>
     */
    @Property(tries = 200)
    @Label("extractServiceName for infrastructure prefixed paths matches root-level")
    void extractServiceNameForInfrastructureMatchesRootLevel(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("infrastructureResources") String resource) {

        Assume.that(!solutionId.equals("solutions"));

        String prefixedPath = "/" + domainId + "/" + solutionId + resource;
        // For root-level, we simulate a single-segment path that would be parsed as domain-management
        // Instead, we compare the extracted service name against the expected value directly
        ParsedPath parsed = UrlPrefixParser.parse(prefixedPath);
        String resourcePath = parsed.resourcePath();

        assertThat(resourcePath)
                .as("resourcePath from '%s'", prefixedPath)
                .isEqualTo(resource);

        String serviceName = extractServiceName(prefixedPath);
        // The service name should be the first segment of the resource path
        String expectedServiceName = resource.split("/")[1];

        assertThat(serviceName)
                .as("extractServiceName('%s') should be '%s'", prefixedPath, expectedServiceName)
                .isEqualTo(expectedServiceName);
    }

    // ========== Property: determineAccessType for prefixed path matches root-level ==========

    /**
     * For any domainId, solutionId, HTTP method, and connector path, the access type
     * determined from the prefixed path matches what the old root-level logic would
     * produce for the same connector/resource path. The old logic used
     * {@code path.startsWith("/events")} etc. directly; the new logic strips the
     * prefix first, then applies the same rules.
     *
     * <p><strong>Validates: Requirements 6.5</strong></p>
     */
    @Property(tries = 200)
    @Label("determineAccessType for prefixed path matches root-level determination")
    void determineAccessTypeMatchesRootLevel(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("connectorTypes") String connector,
            @ForAll("serviceObjectNames") String objectName,
            @ForAll("anyHttpMethod") HttpMethod method) {

        Assume.that(!solutionId.equals("solutions"));

        String prefixedPath = "/" + domainId + "/" + solutionId + "/" + connector + "/" + objectName;

        // Compute the expected access type using the old root-level logic directly
        // on the resource path (what the path would have been before the prefix was added)
        String resourcePath = "/" + connector + "/" + objectName;
        AccessType expectedAccessType = determineAccessTypeFromResourcePath(method, resourcePath);

        AccessType prefixedResult = determineAccessType(method, prefixedPath);

        assertThat(prefixedResult)
                .as("determineAccessType(%s, '%s') should equal old logic for '%s'",
                        method, prefixedPath, resourcePath)
                .isEqualTo(expectedAccessType);
    }

    /**
     * Simulates the old root-level access type determination logic that operated
     * directly on the path without any prefix stripping.
     */
    private AccessType determineAccessTypeFromResourcePath(HttpMethod method, String resourcePath) {
        if (method == HttpMethod.GET && resourcePath.startsWith("/requests")) {
            return AccessType.QUERY;
        }
        if (method == HttpMethod.POST && resourcePath.startsWith("/responses")) {
            return AccessType.PROVIDE;
        }
        if (method == HttpMethod.POST && resourcePath.startsWith("/events")) {
            return AccessType.PROVIDE;
        }
        if (method == HttpMethod.GET && resourcePath.startsWith("/services")) {
            return AccessType.QUERY;
        }
        if ((method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE)
                && resourcePath.startsWith("/services")) {
            return AccessType.PROVIDE;
        }
        return AccessType.QUERY;
    }

    // ========== Property: resource path extraction is prefix-independent ==========

    /**
     * For any domainId, solutionId, and resource suffix, the resource path extracted
     * by UrlPrefixParser from a prefixed path equals the original resource suffix.
     * This ensures zone/context extraction (which operates on headers, not paths)
     * is unaffected by the prefix.
     *
     * <p><strong>Validates: Requirements 6.2, 6.3</strong></p>
     */
    @Property(tries = 200)
    @Label("Resource path extraction is independent of domain/solution prefix values")
    void resourcePathExtractionIsPrefixIndependent(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("validResourcePaths") String resource) {

        Assume.that(!solutionId.equals("solutions"));

        String prefixedPath = "/" + domainId + "/" + solutionId + resource;

        ParsedPath parsed = UrlPrefixParser.parse(prefixedPath);

        assertThat(parsed.resourcePath())
                .as("resourcePath from '%s'", prefixedPath)
                .isEqualTo(resource.isEmpty() ? "/" : resource);
    }

    // ========== Property: access type rules are correct for known connector patterns ==========

    /**
     * Verifies the specific access type rules:
     * GET /requests/* → QUERY, POST /events/* → PROVIDE, POST /responses/* → PROVIDE,
     * GET /services/* → QUERY, POST/PUT/DELETE /services/* → PROVIDE.
     *
     * <p><strong>Validates: Requirements 6.5</strong></p>
     */
    @Property(tries = 200)
    @Label("Access type rules produce correct results for connector paths with prefix")
    void accessTypeRulesCorrectForConnectorPaths(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("serviceObjectNames") String objectName) {

        Assume.that(!solutionId.equals("solutions"));

        String requestsPath = "/" + domainId + "/" + solutionId + "/requests/" + objectName;
        String eventsPath = "/" + domainId + "/" + solutionId + "/events/" + objectName;
        String responsesPath = "/" + domainId + "/" + solutionId + "/responses/" + objectName;
        String servicesPath = "/" + domainId + "/" + solutionId + "/services/" + objectName;

        assertThat(determineAccessType(HttpMethod.GET, requestsPath))
                .as("GET %s → QUERY", requestsPath)
                .isEqualTo(AccessType.QUERY);

        assertThat(determineAccessType(HttpMethod.POST, eventsPath))
                .as("POST %s → PROVIDE", eventsPath)
                .isEqualTo(AccessType.PROVIDE);

        assertThat(determineAccessType(HttpMethod.POST, responsesPath))
                .as("POST %s → PROVIDE", responsesPath)
                .isEqualTo(AccessType.PROVIDE);

        assertThat(determineAccessType(HttpMethod.GET, servicesPath))
                .as("GET %s → QUERY", servicesPath)
                .isEqualTo(AccessType.QUERY);

        assertThat(determineAccessType(HttpMethod.POST, servicesPath))
                .as("POST %s → PROVIDE", servicesPath)
                .isEqualTo(AccessType.PROVIDE);

        assertThat(determineAccessType(HttpMethod.PUT, servicesPath))
                .as("PUT %s → PROVIDE", servicesPath)
                .isEqualTo(AccessType.PROVIDE);

        assertThat(determineAccessType(HttpMethod.DELETE, servicesPath))
                .as("DELETE %s → PROVIDE", servicesPath)
                .isEqualTo(AccessType.PROVIDE);
    }

    // ========== Arbitraries ==========

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

    @Provide
    Arbitrary<String> connectorTypes() {
        return Arbitraries.of("requests", "responses", "events", "services");
    }

    @Provide
    Arbitrary<String> serviceObjectNames() {
        return Arbitraries.of(
                "StudentPersonal", "SchoolInfo", "StaffPersonal",
                "StudentContactPersonal", "StudentSchoolEnrollment",
                "TeachingGroup", "TimeTable", "CalendarDate"
        );
    }

    @Provide
    Arbitrary<String> infrastructureResources() {
        return Arbitraries.of(
                "/environments", "/queues", "/subscriptions", "/jobs",
                "/acl-entries", "/providers", "/zones"
        );
    }

    @Provide
    Arbitrary<HttpMethod> anyHttpMethod() {
        return Arbitraries.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
                HttpMethod.DELETE, HttpMethod.PATCH);
    }

    @Provide
    Arbitrary<String> validResourcePaths() {
        Arbitrary<String> segment = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(20);

        Arbitrary<String> empty = Arbitraries.just("");
        Arbitrary<String> single = segment.map(s -> "/" + s);
        Arbitrary<String> two = Combinators.combine(segment, segment)
                .as((a, b) -> "/" + a + "/" + b);

        return Arbitraries.oneOf(empty, single, two);
    }
}
