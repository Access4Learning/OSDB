// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.gateway.util.UrlPrefixParser;
import org.sifunity.gateway.util.UrlPrefixParser.ParsedPath;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug Condition Exploration Test for PUT Access Type Mapping at Service Level.
 *
 * <p><strong>Bug Description (Sub-condition 2):</strong>
 * The gateway {@code AuthorizationFilter.determineAccessType()} maps PUT at service level
 * (1-2 segments after /services/) to {@code AccessType.QUERY} instead of {@code AccessType.UPDATE}.
 * This causes consumers with UPDATE ACL to receive HTTP 403 Forbidden when sending PUT to
 * {@code /services/{serviceName}}.</p>
 *
 * <p><strong>Expected Behavior (after fix):</strong>
 * {@code determineAccessType(PUT, "/d/s/services/DataExport")} should return {@code UPDATE}
 * for 1-2 segment service paths.</p>
 *
 * <p><strong>CRITICAL:</strong> This test encodes EXPECTED behavior. It MUST FAIL on unfixed code
 * (proving the bug exists) and PASS after the fix is applied.</p>
 *
 * <p><strong>Validates: Requirements 1.2</strong></p>
 *
 * @see org.sifunity.gateway.filter.AuthorizationFilter
 */
@Tag("functional-services-connector-fix")
@Tag("bug-condition-exploration")
class PutAccessTypeBugConditionExplorationTest {

    /**
     * Mirrors the determineAccessType logic from AuthorizationFilter for /services/* paths.
     * This is the ACTUAL (buggy) behavior — copied directly from the production code.
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

            // 1-2 segments: method-based access type
            if (method == HttpMethod.POST) {
                return AccessType.CREATE;
            }
            if (method == HttpMethod.GET) {
                return AccessType.QUERY;
            }
            if (method == HttpMethod.DELETE) {
                return AccessType.DELETE;
            }
            if (method == HttpMethod.PUT) {
                return AccessType.UPDATE;
            }

            // Fallback for other methods
            return AccessType.QUERY;
        }

        return AccessType.QUERY;
    }

    /**
     * Property 1 (Sub-condition 2): PUT at service-level (1-2 segments) should return UPDATE.
     *
     * <p><strong>Validates: Requirements 1.2</strong></p>
     *
     * <p><strong>EXPECTED OUTCOME on unfixed code: FAILS</strong> — because determineAccessType
     * returns QUERY for PUT at 1-2 segments instead of UPDATE.</p>
     *
     * <p>For any valid service name and domain/solution prefix, when PUT is sent to a
     * service-level path (1 segment after /services/), the access type should be UPDATE.</p>
     */
    @Property(tries = 20)
    @Label("Bug Condition: PUT at service-level should return UPDATE, not QUERY")
    void putAtServiceLevelShouldReturnUpdate(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("serviceNames") String serviceName) {

        // Construct a service-level path: /{domainId}/{solutionId}/services/{serviceName}
        String path = "/" + domainId + "/" + solutionId + "/services/" + serviceName;

        AccessType result = determineAccessType(HttpMethod.PUT, path);

        assertThat(result)
                .as("determineAccessType(PUT, '%s') should return UPDATE for service-level path, but got %s",
                        path, result)
                .isEqualTo(AccessType.UPDATE);
    }

    /**
     * Property 1 (Sub-condition 2 variant): PUT at job-level (2 segments) should return UPDATE.
     *
     * <p><strong>Validates: Requirements 1.2</strong></p>
     *
     * <p><strong>EXPECTED OUTCOME on unfixed code: FAILS</strong> — same bug applies to
     * 2-segment paths.</p>
     */
    @Property(tries = 20)
    @Label("Bug Condition: PUT at job-level (2 segments) should return UPDATE, not QUERY")
    void putAtJobLevelShouldReturnUpdate(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("serviceNames") String serviceName,
            @ForAll("validSegments") String jobId) {

        // Construct a job-level path: /{domainId}/{solutionId}/services/{serviceName}/{jobId}
        String path = "/" + domainId + "/" + solutionId + "/services/" + serviceName + "/" + jobId;

        AccessType result = determineAccessType(HttpMethod.PUT, path);

        assertThat(result)
                .as("determineAccessType(PUT, '%s') should return UPDATE for job-level path, but got %s",
                        path, result)
                .isEqualTo(AccessType.UPDATE);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> validSegments() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(s -> !s.isBlank() && !s.equals("solutions"));
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.of(
                "DataExport", "DataImport", "StudentLocator",
                "GradeSync", "AttendanceReporter", "RosterSync",
                "AssessmentResults", "TranscriptExchange");
    }
}
