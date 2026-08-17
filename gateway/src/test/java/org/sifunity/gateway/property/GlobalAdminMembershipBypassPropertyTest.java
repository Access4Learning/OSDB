// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.gateway.util.UrlPrefixParser;
import org.sifunity.gateway.util.UrlPrefixParser.ParsedPath;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for global admin membership bypass.
 *
 * <p><strong>Feature: global-admin-bootstrap, Property 2: Global Admin Membership Bypass</strong></p>
 *
 * <p>For any authenticated request where {@code tokenDomainId} equals {@code GLOBAL_DOMAIN}
 * and {@code tokenSolutionId} equals {@code GLOBAL_SOLUTION}, and for any URL path containing
 * any {@code domainId}/{@code solutionId} prefix, the MembershipValidationFilter SHALL skip
 * membership validation and allow the request to proceed, regardless of whether the URL
 * domain/solution matches the token's domain/solution.</p>
 *
 * <p>This test replicates the membership validation logic from
 * {@code MembershipValidationFilter} using pure functions, verifying that global admin
 * environments always bypass the check.</p>
 *
 * <p><strong>Validates: Requirements 3.1</strong></p>
 */
@Tag("global-admin-bootstrap")
@Tag("property-2-global-admin-membership-bypass")
class GlobalAdminMembershipBypassPropertyTest {

    // ========== Validation result enum ==========

    enum ValidationResult {
        /** Request allowed through (bypassed or matched) */
        ALLOWED,
        /** Domain mismatch → 403 */
        DOMAIN_MISMATCH,
        /** Solution mismatch → 403 */
        SOLUTION_MISMATCH,
        /** Path is exempt from validation */
        EXEMPT
    }

    // ========== Membership validation logic (mirrors updated MembershipValidationFilter) ==========

    /**
     * Replicates the membership validation logic from the updated {@code MembershipValidationFilter},
     * using {@code GlobalAdminConstants.isGlobalAdmin()} for the admin bypass check.
     *
     * @param path             the request URL path
     * @param hasEnvironmentType whether an environmentType attribute is present
     * @param tokenDomainId    the domain ID from the token
     * @param tokenSolutionId  the solution ID from the token
     * @return the validation result
     */
    private ValidationResult validateMembership(String path,
                                                 boolean hasEnvironmentType,
                                                 String tokenDomainId,
                                                 String tokenSolutionId) {
        ParsedPath parsed = UrlPrefixParser.parse(path);

        // Domain management paths — skip validation
        if (parsed.isDomainManagementPath()) {
            return ValidationResult.EXEMPT;
        }

        // No environmentType means unauthenticated (exempt path)
        if (!hasEnvironmentType) {
            return ValidationResult.EXEMPT;
        }

        // Global admin environments bypass membership validation
        if (GlobalAdminConstants.isGlobalAdmin(tokenDomainId, tokenSolutionId)) {
            return ValidationResult.ALLOWED;
        }

        // Solution management paths — validate domain only
        if (parsed.isSolutionManagementPath()) {
            if (tokenDomainId == null || !tokenDomainId.equals(parsed.domainId())) {
                return ValidationResult.DOMAIN_MISMATCH;
            }
            return ValidationResult.ALLOWED;
        }

        // Full resource paths — validate both domain and solution
        if (parsed.isResourcePath()) {
            if (tokenDomainId == null || !tokenDomainId.equals(parsed.domainId())) {
                return ValidationResult.DOMAIN_MISMATCH;
            }
            if (tokenSolutionId == null || !tokenSolutionId.equals(parsed.solutionId())) {
                return ValidationResult.SOLUTION_MISMATCH;
            }
            return ValidationResult.ALLOWED;
        }

        return ValidationResult.EXEMPT;
    }

    // ========== Property: Global admin bypasses membership for resource paths ==========

    /**
     * For any request with global admin token domain/solution and any URL path with
     * any domainId/solutionId/resource combination, membership validation is bypassed
     * and the request is ALLOWED, even when the URL domain/solution don't match.
     *
     * <p><strong>Validates: Requirements 3.1</strong></p>
     */
    @Property(tries = 200)
    @Label("Global admin bypasses membership validation for resource paths")
    void globalAdminBypassesMembershipForResourcePaths(
            @ForAll("validSegments") String urlDomainId,
            @ForAll("validSegments") String urlSolutionId,
            @ForAll("resourceSuffixes") String resource) {

        Assume.that(!urlSolutionId.equals("solutions"));
        Assume.that(!urlDomainId.equals("domains"));
        Assume.that(!urlDomainId.equals("actuator"));

        String path = "/" + urlDomainId + "/" + urlSolutionId + resource;

        ValidationResult result = validateMembership(path, true,
                GlobalAdminConstants.GLOBAL_DOMAIN, GlobalAdminConstants.GLOBAL_SOLUTION);

        assertThat(result)
                .as("Global admin should bypass membership for path '%s'", path)
                .isEqualTo(ValidationResult.ALLOWED);
    }

    // ========== Property: Global admin bypasses membership for solution management paths ==========

    /**
     * For any request with global admin token domain/solution and any solution management
     * path, membership validation is bypassed regardless of URL domain.
     *
     * <p><strong>Validates: Requirements 3.1</strong></p>
     */
    @Property(tries = 200)
    @Label("Global admin bypasses membership validation for solution management paths")
    void globalAdminBypassesMembershipForSolutionPaths(
            @ForAll("validSegments") String urlDomainId,
            @ForAll("solutionManagementSuffixes") String suffix) {

        Assume.that(!urlDomainId.equals("domains"));
        Assume.that(!urlDomainId.equals("actuator"));

        String path = "/" + urlDomainId + "/solutions" + suffix;

        ValidationResult result = validateMembership(path, true,
                GlobalAdminConstants.GLOBAL_DOMAIN, GlobalAdminConstants.GLOBAL_SOLUTION);

        assertThat(result)
                .as("Global admin should bypass membership for solution path '%s'", path)
                .isEqualTo(ValidationResult.ALLOWED);
    }

    // ========== Property: Non-global-admin with mismatched domain is rejected ==========

    /**
     * Contrast property: for non-global-admin environments with mismatched domain,
     * the request is rejected. This confirms the bypass is specific to global admin
     * token domain/solution.
     *
     * <p><strong>Validates: Requirements 3.1</strong></p>
     */
    @Property(tries = 200)
    @Label("Non-global-admin with mismatched domain is rejected (contrast)")
    void nonGlobalAdminWithMismatchedDomainIsRejected(
            @ForAll("validSegments") String urlDomainId,
            @ForAll("nonGlobalAdminDomainSolution") String[] tokenPair,
            @ForAll("validSegments") String urlSolutionId,
            @ForAll("resourceSuffixes") String resource) {

        Assume.that(!urlSolutionId.equals("solutions"));
        Assume.that(!urlDomainId.equals("domains"));
        Assume.that(!urlDomainId.equals("actuator"));
        Assume.that(!urlDomainId.equals(tokenPair[0]));

        String path = "/" + urlDomainId + "/" + urlSolutionId + resource;

        ValidationResult result = validateMembership(path, true, tokenPair[0], tokenPair[1]);

        assertThat(result)
                .as("Non-global-admin with mismatched domain should be DOMAIN_MISMATCH for path '%s' " +
                        "(token domain='%s', token solution='%s')",
                        path, tokenPair[0], tokenPair[1])
                .isEqualTo(ValidationResult.DOMAIN_MISMATCH);
    }

    // ========== Property: Non-global-admin with mismatched solution is rejected ==========

    /**
     * Contrast property: for non-global-admin environments with matching domain but
     * mismatched solution, the request is rejected with SOLUTION_MISMATCH.
     *
     * <p><strong>Validates: Requirements 3.1</strong></p>
     */
    @Property(tries = 200)
    @Label("Non-global-admin with mismatched solution is rejected (contrast)")
    void nonGlobalAdminWithMismatchedSolutionIsRejected(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String urlSolutionId,
            @ForAll("validSegments") String tokenSolutionId,
            @ForAll("resourceSuffixes") String resource) {

        Assume.that(!urlSolutionId.equals("solutions"));
        Assume.that(!urlSolutionId.equals(tokenSolutionId));
        Assume.that(!domainId.equals("domains"));
        Assume.that(!domainId.equals("actuator"));
        // Ensure this is NOT a global admin
        Assume.that(!GlobalAdminConstants.isGlobalAdmin(domainId, tokenSolutionId));

        String path = "/" + domainId + "/" + urlSolutionId + resource;

        ValidationResult result = validateMembership(path, true, domainId, tokenSolutionId);

        assertThat(result)
                .as("Non-global-admin with mismatched solution should be SOLUTION_MISMATCH for path '%s' " +
                        "(token solution='%s')", path, tokenSolutionId)
                .isEqualTo(ValidationResult.SOLUTION_MISMATCH);
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
    Arbitrary<String> resourceSuffixes() {
        return Arbitraries.of(
                "/environments", "/queues", "/subscriptions", "/jobs",
                "/acl-entries", "/providers", "/zones",
                "/requests/StudentPersonal", "/events/StudentPersonal",
                "/responses/StudentPersonal", "/services/StudentPersonal"
        );
    }

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
     * Generates domain/solution pairs that are NOT global admin.
     * This ensures the contrast properties test non-admin scenarios.
     */
    @Provide
    Arbitrary<String[]> nonGlobalAdminDomainSolution() {
        Arbitrary<String> segments = validSegments();
        return Combinators.combine(segments, segments)
                .as((d, s) -> new String[]{d, s})
                .filter(pair -> !GlobalAdminConstants.isGlobalAdmin(pair[0], pair[1]));
    }
}
