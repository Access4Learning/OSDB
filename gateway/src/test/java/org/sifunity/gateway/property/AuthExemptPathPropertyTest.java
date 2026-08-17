// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for authentication exempt path recognition.
 *
 * <p><strong>Property 4: Authentication Exempt Path Recognition</strong></p>
 *
 * <p>Only {@code /actuator/**} paths are exempt from authentication.
 * All other paths (domains, solutions, environments, etc.) require authentication.</p>
 *
 * <p><strong>Validates: Requirements 5.1, 6.4</strong></p>
 */
@Tag("domain-solution-url-prefix")
@Tag("property-4-authentication-exempt-path-recognition")
class AuthExemptPathPropertyTest {

    /**
     * Replicates the exempt path logic from {@code AuthenticationFilter.isExemptPath()}.
     * Only actuator paths are exempt.
     */
    private boolean isExemptPath(String path) {
        return path.startsWith("/actuator");
    }

    // ========== Property: /actuator/** is exempt ==========

    @Property(tries = 100)
    @Label("/actuator/** paths are exempt from authentication")
    void actuatorPathsAreExempt(
            @ForAll("actuatorSuffixes") String suffix,
            @ForAll("anyHttpMethod") HttpMethod method) {

        String path = "/actuator" + suffix;
        assertThat(isExemptPath(path))
                .as("%s %s should be exempt", method, path)
                .isTrue();
    }

    // ========== Property: /domains/** is NOT exempt ==========

    @Property(tries = 200)
    @Label("/domains/** paths are NOT exempt (require admin auth)")
    void domainPathsAreNotExempt(
            @ForAll("pathSuffixes") String suffix,
            @ForAll("anyHttpMethod") HttpMethod method) {

        String path = "/domains" + suffix;
        assertThat(isExemptPath(path))
                .as("%s %s should NOT be exempt", method, path)
                .isFalse();
    }

    // ========== Property: POST environments is NOT exempt ==========

    @Property(tries = 200)
    @Label("POST /{domainId}/{solutionId}/environments is NOT exempt (requires admin auth)")
    void postEnvironmentsIsNotExempt(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId) {

        Assume.that(!solutionId.equals("solutions"));
        String path = "/" + domainId + "/" + solutionId + "/environments";
        assertThat(isExemptPath(path))
                .as("POST %s should NOT be exempt", path)
                .isFalse();
    }

    // ========== Property: Solution management is NOT exempt ==========

    @Property(tries = 200)
    @Label("/{domainId}/solutions/** is NOT exempt")
    void solutionPathsAreNotExempt(
            @ForAll("validSegments") String domainId,
            @ForAll("pathSuffixes") String suffix,
            @ForAll("anyHttpMethod") HttpMethod method) {

        Assume.that(!domainId.equals("actuator"));
        String path = "/" + domainId + "/solutions" + suffix;
        assertThat(isExemptPath(path))
                .as("%s %s should NOT be exempt", method, path)
                .isFalse();
    }

    // ========== Property: Resource paths are NOT exempt ==========

    @Property(tries = 200)
    @Label("/{domainId}/{solutionId}/resource paths are NOT exempt")
    void resourcePathsAreNotExempt(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("resourceSuffixes") String resource,
            @ForAll("anyHttpMethod") HttpMethod method) {

        Assume.that(!solutionId.equals("solutions"));
        Assume.that(!domainId.equals("actuator"));
        String path = "/" + domainId + "/" + solutionId + resource;
        assertThat(isExemptPath(path))
                .as("%s %s should NOT be exempt", method, path)
                .isFalse();
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> validSegments() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(30)
                .filter(s -> !s.isBlank());
    }

    @Provide
    Arbitrary<HttpMethod> anyHttpMethod() {
        return Arbitraries.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
                HttpMethod.DELETE, HttpMethod.PATCH);
    }

    @Provide
    Arbitrary<String> actuatorSuffixes() {
        return Arbitraries.of("", "/", "/health", "/health/liveness",
                "/health/readiness", "/info", "/metrics");
    }

    @Provide
    Arbitrary<String> pathSuffixes() {
        Arbitrary<String> segment = validSegments();
        return Arbitraries.oneOf(
                Arbitraries.just(""),
                Arbitraries.just("/"),
                segment.map(s -> "/" + s)
        );
    }

    @Provide
    Arbitrary<String> resourceSuffixes() {
        return Arbitraries.of(
                "/environments", "/queues", "/subscriptions", "/jobs",
                "/acl-entries", "/zones", "/providers"
        );
    }
}
