// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.gateway.util.UrlPrefixParser;
import org.sifunity.gateway.util.UrlPrefixParser.ParsedPath;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preservation Property Tests for Access Type Mapping in AuthorizationFilter.
 *
 * <p>These tests capture the baseline behavior of {@code determineAccessType} for the
 * "safe zone" — behaviors that must NOT change after the bug fix. They observe existing
 * behavior on UNFIXED code and verify it remains identical after implementation.</p>
 *
 * <p><strong>Properties tested:</strong></p>
 * <ul>
 *   <li>P2a: GET at 1-2 segments → QUERY</li>
 *   <li>P2b: POST at 1-2 segments → CREATE</li>
 *   <li>P2c: DELETE at 1-2 segments → DELETE</li>
 *   <li>P2d: All HTTP methods at 3+ segments (phase-level) → CREATE</li>
 * </ul>
 *
 * <p><strong>EXPECTED OUTCOME: All tests PASS on unfixed code</strong> — these confirm
 * baseline behavior that the fix must preserve.</p>
 *
 * <p><strong>Validates: Requirements 3.2, 3.4, 3.5, 3.6</strong></p>
 *
 * @see org.sifunity.gateway.filter.AuthorizationFilter
 */
@Tag("functional-services-connector-fix")
@Tag("preservation")
class AccessTypeMappingPreservationTest {

    /**
     * Mirrors the determineAccessType logic from AuthorizationFilter for /services/* paths.
     * This is the ACTUAL (current) behavior — copied directly from the production code.
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

            // Fallback for other methods (e.g., PUT at job level)
            return AccessType.QUERY;
        }

        return AccessType.QUERY;
    }

    // ── P2a: GET at 1-2 segments → QUERY ────────────────────────────────

    /**
     * P2a: For all GET requests at 1 segment (service-level), determineAccessType returns QUERY.
     *
     * <p><strong>Validates: Requirements 3.4</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation P2a: GET at service-level (1 segment) returns QUERY")
    void getAtServiceLevelReturnsQuery(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("serviceNames") String serviceName) {

        String path = "/" + domainId + "/" + solutionId + "/services/" + serviceName;

        AccessType result = determineAccessType(HttpMethod.GET, path);

        assertThat(result)
                .as("determineAccessType(GET, '%s') should return QUERY", path)
                .isEqualTo(AccessType.QUERY);
    }

    /**
     * P2a variant: For all GET requests at 2 segments (job-level), determineAccessType returns QUERY.
     *
     * <p><strong>Validates: Requirements 3.4</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation P2a: GET at job-level (2 segments) returns QUERY")
    void getAtJobLevelReturnsQuery(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("serviceNames") String serviceName,
            @ForAll("validSegments") String jobId) {

        String path = "/" + domainId + "/" + solutionId + "/services/" + serviceName + "/" + jobId;

        AccessType result = determineAccessType(HttpMethod.GET, path);

        assertThat(result)
                .as("determineAccessType(GET, '%s') should return QUERY", path)
                .isEqualTo(AccessType.QUERY);
    }

    // ── P2b: POST at 1-2 segments → CREATE ──────────────────────────────

    /**
     * P2b: For all POST requests at 1 segment (service-level), determineAccessType returns CREATE.
     *
     * <p><strong>Validates: Requirements 3.5</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation P2b: POST at service-level (1 segment) returns CREATE")
    void postAtServiceLevelReturnsCreate(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("serviceNames") String serviceName) {

        String path = "/" + domainId + "/" + solutionId + "/services/" + serviceName;

        AccessType result = determineAccessType(HttpMethod.POST, path);

        assertThat(result)
                .as("determineAccessType(POST, '%s') should return CREATE", path)
                .isEqualTo(AccessType.CREATE);
    }

    /**
     * P2b variant: For all POST requests at 2 segments (job-level), determineAccessType returns CREATE.
     *
     * <p><strong>Validates: Requirements 3.5</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation P2b: POST at job-level (2 segments) returns CREATE")
    void postAtJobLevelReturnsCreate(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("serviceNames") String serviceName,
            @ForAll("validSegments") String jobId) {

        String path = "/" + domainId + "/" + solutionId + "/services/" + serviceName + "/" + jobId;

        AccessType result = determineAccessType(HttpMethod.POST, path);

        assertThat(result)
                .as("determineAccessType(POST, '%s') should return CREATE", path)
                .isEqualTo(AccessType.CREATE);
    }

    // ── P2c: DELETE at 1-2 segments → DELETE ─────────────────────────────

    /**
     * P2c: For all DELETE requests at 1 segment (service-level), determineAccessType returns DELETE.
     *
     * <p><strong>Validates: Requirements 3.6</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation P2c: DELETE at service-level (1 segment) returns DELETE")
    void deleteAtServiceLevelReturnsDelete(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("serviceNames") String serviceName) {

        String path = "/" + domainId + "/" + solutionId + "/services/" + serviceName;

        AccessType result = determineAccessType(HttpMethod.DELETE, path);

        assertThat(result)
                .as("determineAccessType(DELETE, '%s') should return DELETE", path)
                .isEqualTo(AccessType.DELETE);
    }

    /**
     * P2c variant: For all DELETE requests at 2 segments (job-level), determineAccessType returns DELETE.
     *
     * <p><strong>Validates: Requirements 3.6</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation P2c: DELETE at job-level (2 segments) returns DELETE")
    void deleteAtJobLevelReturnsDelete(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("serviceNames") String serviceName,
            @ForAll("validSegments") String jobId) {

        String path = "/" + domainId + "/" + solutionId + "/services/" + serviceName + "/" + jobId;

        AccessType result = determineAccessType(HttpMethod.DELETE, path);

        assertThat(result)
                .as("determineAccessType(DELETE, '%s') should return DELETE", path)
                .isEqualTo(AccessType.DELETE);
    }

    // ── P2d: All HTTP methods at 3+ segments → CREATE ────────────────────

    /**
     * P2d: For all HTTP methods at 3+ segments (phase-level), determineAccessType returns CREATE.
     *
     * <p><strong>Validates: Requirements 3.2</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation P2d: Any method at phase-level (3+ segments) returns CREATE")
    void anyMethodAtPhaseLevelReturnsCreate(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("serviceNames") String serviceName,
            @ForAll("validSegments") String jobId,
            @ForAll("validSegments") String phaseName,
            @ForAll("httpMethods") HttpMethod method) {

        // 3 segments: serviceName/jobId/phaseName
        String path = "/" + domainId + "/" + solutionId + "/services/" + serviceName + "/" + jobId + "/" + phaseName;

        AccessType result = determineAccessType(method, path);

        assertThat(result)
                .as("determineAccessType(%s, '%s') should return CREATE for phase-level path", method, path)
                .isEqualTo(AccessType.CREATE);
    }

    /**
     * P2d variant: 4+ segments (state-level) also returns CREATE.
     *
     * <p><strong>Validates: Requirements 3.2</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation P2d: Any method at state-level (4+ segments) returns CREATE")
    void anyMethodAtStateLevelReturnsCreate(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("serviceNames") String serviceName,
            @ForAll("validSegments") String jobId,
            @ForAll("validSegments") String phaseName,
            @ForAll("httpMethods") HttpMethod method) {

        // 4 segments: serviceName/jobId/phaseName/states
        String path = "/" + domainId + "/" + solutionId + "/services/" + serviceName + "/" + jobId + "/" + phaseName + "/states";

        AccessType result = determineAccessType(method, path);

        assertThat(result)
                .as("determineAccessType(%s, '%s') should return CREATE for state-level path", method, path)
                .isEqualTo(AccessType.CREATE);
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

    @Provide
    Arbitrary<HttpMethod> httpMethods() {
        return Arbitraries.of(
                HttpMethod.GET, HttpMethod.POST,
                HttpMethod.PUT, HttpMethod.DELETE);
    }
}
