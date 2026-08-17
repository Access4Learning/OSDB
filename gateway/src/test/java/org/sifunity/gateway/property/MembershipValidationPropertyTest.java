// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.sifunity.gateway.util.UrlPrefixParser;
import org.sifunity.gateway.util.UrlPrefixParser.ParsedPath;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for membership validation enforcement.
 *
 * <p><strong>Property 2: Membership Validation Enforcement</strong></p>
 *
 * <p>For any authenticated environment and any URL with a
 * {@code /{domainId}/{solutionId}} prefix, if the environment's domain does not
 * match {@code domainId} OR the environment's solution does not match
 * {@code solutionId}, the system shall return HTTP 403 Forbidden. Conversely,
 * if both match, the request shall be allowed through.</p>
 *
 * <p>This test replicates the membership validation logic from
 * {@code MembershipValidationFilter} using pure functions, avoiding the need
 * for a Spring context or reactive infrastructure.</p>
 *
 * <p><strong>Validates: Requirements 4.1, 4.2, 4.3, 4.4</strong></p>
 */
@Tag("domain-solution-url-prefix")
@Tag("property-2-membership-validation-enforcement")
class MembershipValidationPropertyTest {

    // ========== Validation result enum ==========

    enum ValidationResult {
        /** Request allowed through */
        ALLOWED,
        /** Domain mismatch → 403 MEMBERSHIP_001 */
        DOMAIN_MISMATCH,
        /** Solution mismatch → 403 MEMBERSHIP_002 */
        SOLUTION_MISMATCH,
        /** Path is exempt from validation (domain-management, etc.) */
        EXEMPT
    }

    // ========== Membership validation logic (mirrors MembershipValidationFilter) ==========

    /**
     * Replicates the membership validation logic from {@code MembershipValidationFilter}.
     *
     * @param path            the request URL path
     * @param tokenDomainId   the domain ID from the token (environment's domain)
     * @param tokenSolutionId the solution ID from the token (environment's solution)
     * @return the validation result
     */
    private ValidationResult validateMembership(String path,
                                                 String tokenDomainId,
                                                 String tokenSolutionId) {
        ParsedPath parsed = UrlPrefixParser.parse(path);

        // Domain management paths — skip validation
        if (parsed.isDomainManagementPath()) {
            return ValidationResult.EXEMPT;
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

        // Fallback — no prefix to validate
        return ValidationResult.EXEMPT;
    }


    // ========== Property: Matching domain and solution → ALLOWED ==========

    /**
     * For any domainId, solutionId, and resource, when the token's domain and solution
     * match the URL's domain and solution, the request is allowed through.
     *
     * <p><strong>Validates: Requirements 4.1, 4.2</strong></p>
     */
    @Property(tries = 200)
    @Label("Matching domain and solution allows the request through")
    void matchingDomainAndSolutionAllowsRequest(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("resourceSuffixes") String resource) {

        Assume.that(!solutionId.equals("solutions"));

        String path = "/" + domainId + "/" + solutionId + resource;

        ValidationResult result = validateMembership(path, domainId, solutionId);

        assertThat(result)
                .as("Matching domain '%s' and solution '%s' for path '%s' should be ALLOWED",
                        domainId, solutionId, path)
                .isEqualTo(ValidationResult.ALLOWED);
    }

    // ========== Property: Mismatched domain → DOMAIN_MISMATCH ==========

    /**
     * For any URL domainId and a different token domainId, the request is rejected
     * with DOMAIN_MISMATCH (HTTP 403).
     *
     * <p><strong>Validates: Requirements 4.1, 4.3</strong></p>
     */
    @Property(tries = 200)
    @Label("Mismatched domain rejects with DOMAIN_MISMATCH (403)")
    void mismatchedDomainRejectsRequest(
            @ForAll("validSegments") String urlDomainId,
            @ForAll("validSegments") String tokenDomainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("resourceSuffixes") String resource) {

        Assume.that(!solutionId.equals("solutions"));
        Assume.that(!urlDomainId.equals(tokenDomainId));

        String path = "/" + urlDomainId + "/" + solutionId + resource;

        ValidationResult result = validateMembership(path, tokenDomainId, solutionId);

        assertThat(result)
                .as("Token domain '%s' != URL domain '%s' for path '%s' should be DOMAIN_MISMATCH",
                        tokenDomainId, urlDomainId, path)
                .isEqualTo(ValidationResult.DOMAIN_MISMATCH);
    }

    // ========== Property: Mismatched solution → SOLUTION_MISMATCH ==========

    /**
     * For any URL solutionId and a different token solutionId (with matching domain),
     * the request is rejected with SOLUTION_MISMATCH (HTTP 403).
     *
     * <p><strong>Validates: Requirements 4.2, 4.4</strong></p>
     */
    @Property(tries = 200)
    @Label("Mismatched solution (with matching domain) rejects with SOLUTION_MISMATCH (403)")
    void mismatchedSolutionRejectsRequest(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String urlSolutionId,
            @ForAll("validSegments") String tokenSolutionId,
            @ForAll("resourceSuffixes") String resource) {

        Assume.that(!urlSolutionId.equals("solutions"));
        Assume.that(!tokenSolutionId.equals("solutions"));
        Assume.that(!urlSolutionId.equals(tokenSolutionId));

        String path = "/" + domainId + "/" + urlSolutionId + resource;

        ValidationResult result = validateMembership(path, domainId, tokenSolutionId);

        assertThat(result)
                .as("Token solution '%s' != URL solution '%s' for path '%s' should be SOLUTION_MISMATCH",
                        tokenSolutionId, urlSolutionId, path)
                .isEqualTo(ValidationResult.SOLUTION_MISMATCH);
    }

    // ========== Property: Null token domain → DOMAIN_MISMATCH ==========

    /**
     * When the token has no domain (null), any resource path should be rejected
     * with DOMAIN_MISMATCH.
     *
     * <p><strong>Validates: Requirements 4.1, 4.3</strong></p>
     */
    @Property(tries = 100)
    @Label("Null token domain rejects with DOMAIN_MISMATCH (403)")
    void nullTokenDomainRejectsRequest(
            @ForAll("validSegments") String urlDomainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("resourceSuffixes") String resource) {

        Assume.that(!solutionId.equals("solutions"));

        String path = "/" + urlDomainId + "/" + solutionId + resource;

        ValidationResult result = validateMembership(path, null, solutionId);

        assertThat(result)
                .as("Null token domain for path '%s' should be DOMAIN_MISMATCH", path)
                .isEqualTo(ValidationResult.DOMAIN_MISMATCH);
    }

    // ========== Property: Null token solution → SOLUTION_MISMATCH ==========

    /**
     * When the token has no solution (null) but domain matches, any resource path
     * should be rejected with SOLUTION_MISMATCH.
     *
     * <p><strong>Validates: Requirements 4.2, 4.4</strong></p>
     */
    @Property(tries = 100)
    @Label("Null token solution (with matching domain) rejects with SOLUTION_MISMATCH (403)")
    void nullTokenSolutionRejectsRequest(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("resourceSuffixes") String resource) {

        Assume.that(!solutionId.equals("solutions"));

        String path = "/" + domainId + "/" + solutionId + resource;

        ValidationResult result = validateMembership(path, domainId, null);

        assertThat(result)
                .as("Null token solution for path '%s' should be SOLUTION_MISMATCH", path)
                .isEqualTo(ValidationResult.SOLUTION_MISMATCH);
    }

    // ========== Property: Solution management paths validate domain only ==========

    /**
     * For solution management paths ({@code /{domainId}/solutions/**}), only domain
     * membership is validated. Matching domain → ALLOWED, mismatched → DOMAIN_MISMATCH.
     *
     * <p><strong>Validates: Requirements 4.1, 4.3</strong></p>
     */
    @Property(tries = 200)
    @Label("Solution management paths validate domain membership only")
    void solutionManagementPathsValidateDomainOnly(
            @ForAll("validSegments") String urlDomainId,
            @ForAll("validSegments") String tokenDomainId,
            @ForAll("solutionManagementSuffixes") String suffix) {

        Assume.that(!urlDomainId.equals("domains"));
        Assume.that(!urlDomainId.equals("actuator"));

        String path = "/" + urlDomainId + "/solutions" + suffix;

        ValidationResult result = validateMembership(path, tokenDomainId, "any-solution");

        if (urlDomainId.equals(tokenDomainId)) {
            assertThat(result)
                    .as("Matching domain for solution path '%s' should be ALLOWED", path)
                    .isEqualTo(ValidationResult.ALLOWED);
        } else {
            assertThat(result)
                    .as("Mismatched domain for solution path '%s' should be DOMAIN_MISMATCH", path)
                    .isEqualTo(ValidationResult.DOMAIN_MISMATCH);
        }
    }

    // ========== Property: Domain management paths are exempt ==========

    /**
     * Domain management paths ({@code /domains/**}) are always exempt from
     * membership validation regardless of token values.
     *
     * <p><strong>Validates: Requirements 4.1, 4.2</strong></p>
     */
    @Property(tries = 100)
    @Label("Domain management paths are exempt from membership validation")
    void domainManagementPathsAreExempt(
            @ForAll("domainManagementSuffixes") String suffix,
            @ForAll("validSegments") String tokenDomainId,
            @ForAll("validSegments") String tokenSolutionId) {

        String path = "/domains" + suffix;

        ValidationResult result = validateMembership(path, tokenDomainId, tokenSolutionId);

        assertThat(result)
                .as("Domain management path '%s' should be EXEMPT", path)
                .isEqualTo(ValidationResult.EXEMPT);
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
}
