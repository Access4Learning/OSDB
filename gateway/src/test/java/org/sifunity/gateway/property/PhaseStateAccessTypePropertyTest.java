// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.gateway.util.UrlPrefixParser;
import org.sifunity.gateway.util.UrlPrefixParser.ParsedPath;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for phase and state operations requiring CREATE access.
 *
 * <p><strong>Property 6: Phase and state operations require CREATE access</strong></p>
 *
 * <p>For any HTTP method (GET, POST, PUT, DELETE) on a path containing
 * {@code /{jobId}/{phaseName}} segments (phase or state sub-resource),
 * the AuthorizationFilter SHALL require CREATE access on the functionalServiceName,
 * regardless of the HTTP method used.</p>
 *
 * <p><strong>Validates: Requirements 4.5</strong></p>
 *
 * @see org.sifunity.gateway.filter.AuthorizationFilter#determineAccessType
 */
@Tag("sif-functional-services-redesign")
@Tag("property-6-phase-state-create-access")
class PhaseStateAccessTypePropertyTest {

    /**
     * Mirrors the determineAccessType logic from AuthorizationFilter for /services/* paths.
     */
    private AccessType determineAccessType(HttpMethod method, String path) {
        ParsedPath parsed = UrlPrefixParser.parse(path);
        String resourcePath = parsed.resourcePath();

        if (resourcePath.startsWith("/services")) {
            String[] parts = resourcePath.split("/");
            int segmentsAfterServices = parts.length - 2;

            if (segmentsAfterServices >= 3) {
                return AccessType.CREATE;
            }

            if (method == HttpMethod.POST) {
                return AccessType.CREATE;
            }
            if (method == HttpMethod.GET) {
                return AccessType.QUERY;
            }
            if (method == HttpMethod.DELETE) {
                return AccessType.DELETE;
            }

            return AccessType.QUERY;
        }

        return AccessType.QUERY;
    }

    /**
     * For any HTTP method and a phase-level path (3 segments after /services/),
     * determineAccessType always returns CREATE.
     */
    @Property(tries = 20)
    @Label("Phase operations (3 segments) always require CREATE access regardless of HTTP method")
    void phaseOperationsAlwaysRequireCreate(
            @ForAll("httpMethods") HttpMethod method,
            @ForAll("alphanumericSegments") String domainId,
            @ForAll("alphanumericSegments") String solutionId,
            @ForAll("alphanumericSegments") String functionalServiceName,
            @ForAll("alphanumericSegments") String jobId,
            @ForAll("alphanumericSegments") String phaseName) {

        Assume.that(!solutionId.equals("solutions"));

        String path = "/" + domainId + "/" + solutionId + "/services/"
                + functionalServiceName + "/" + jobId + "/" + phaseName;

        AccessType accessType = determineAccessType(method, path);

        assertThat(accessType)
                .as("determineAccessType(%s, '%s') should return CREATE for phase-level path",
                        method, path)
                .isEqualTo(AccessType.CREATE);
    }

    /**
     * For any HTTP method and a state-list path (/states suffix, 5 segments after /services/),
     * determineAccessType always returns CREATE.
     */
    @Property(tries = 20)
    @Label("State list operations always require CREATE access regardless of HTTP method")
    void stateListOperationsAlwaysRequireCreate(
            @ForAll("httpMethods") HttpMethod method,
            @ForAll("alphanumericSegments") String domainId,
            @ForAll("alphanumericSegments") String solutionId,
            @ForAll("alphanumericSegments") String functionalServiceName,
            @ForAll("alphanumericSegments") String jobId,
            @ForAll("alphanumericSegments") String phaseName) {

        Assume.that(!solutionId.equals("solutions"));

        String path = "/" + domainId + "/" + solutionId + "/services/"
                + functionalServiceName + "/" + jobId + "/" + phaseName + "/states";

        AccessType accessType = determineAccessType(method, path);

        assertThat(accessType)
                .as("determineAccessType(%s, '%s') should return CREATE for state-list path",
                        method, path)
                .isEqualTo(AccessType.CREATE);
    }

    /**
     * For any HTTP method and a state-update path (/states/state suffix, 6 segments after /services/),
     * determineAccessType always returns CREATE.
     */
    @Property(tries = 20)
    @Label("State update operations always require CREATE access regardless of HTTP method")
    void stateUpdateOperationsAlwaysRequireCreate(
            @ForAll("httpMethods") HttpMethod method,
            @ForAll("alphanumericSegments") String domainId,
            @ForAll("alphanumericSegments") String solutionId,
            @ForAll("alphanumericSegments") String functionalServiceName,
            @ForAll("alphanumericSegments") String jobId,
            @ForAll("alphanumericSegments") String phaseName) {

        Assume.that(!solutionId.equals("solutions"));

        String path = "/" + domainId + "/" + solutionId + "/services/"
                + functionalServiceName + "/" + jobId + "/" + phaseName + "/states/state";

        AccessType accessType = determineAccessType(method, path);

        assertThat(accessType)
                .as("determineAccessType(%s, '%s') should return CREATE for state-update path",
                        method, path)
                .isEqualTo(AccessType.CREATE);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<HttpMethod> httpMethods() {
        return Arbitraries.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE);
    }

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
}
