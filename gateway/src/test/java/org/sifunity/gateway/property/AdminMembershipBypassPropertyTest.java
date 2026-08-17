// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.gateway.util.UrlPrefixParser;
import org.sifunity.gateway.util.UrlPrefixParser.ParsedPath;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for global admin environment membership bypass.
 *
 * <p><strong>Property 3: Global Admin Environment Membership Bypass</strong></p>
 *
 * <p>For any environment whose token domain/solution match GLOBAL_DOMAIN/GLOBAL_SOLUTION
 * and any {@code domainId}/{@code solutionId} combination in the URL, the membership
 * validation shall be skipped and the request shall proceed regardless of the
 * environment's actual domain/solution membership.</p>
 *
 * <p><strong>Validates: Requirements 4.5</strong></p>
 */
@Tag("domain-solution-url-prefix")
@Tag("global-admin-bootstrap")
@Tag("property-3-admin-environment-membership-bypass")
class AdminMembershipBypassPropertyTest {

    // ========== Validation result enum ==========

    enum ValidationResult {
        ALLOWED,
        DOMAIN_MISMATCH,
        SOLUTION_MISMATCH,
        EXEMPT
    }

    // ========== Membership validation logic (mirrors updated MembershipValidationFilter) ==========

    private ValidationResult validateMembership(String path,
                                                 boolean hasEnvironmentType,
                                                 String tokenDomainId,
                                                 String tokenSolutionId) {
        ParsedPath parsed = UrlPrefixParser.parse(path);

        if (parsed.isDomainManagementPath()) {
            return ValidationResult.EXEMPT;
        }

        if (!hasEnvironmentType) {
            return ValidationResult.EXEMPT;
        }

        // Global admin environments bypass membership validation
        if (GlobalAdminConstants.isGlobalAdmin(tokenDomainId, tokenSolutionId)) {
            return ValidationResult.ALLOWED;
        }

        if (parsed.isSolutionManagementPath()) {
            if (tokenDomainId == null || !tokenDomainId.equals(parsed.domainId())) {
                return ValidationResult.DOMAIN_MISMATCH;
            }
            return ValidationResult.ALLOWED;
        }

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

    @Property(tries = 200)
    @Label("Global admin environment bypasses membership validation for resource paths")
    void adminBypassesMembershipForResourcePaths(
            @ForAll("validSegments") String urlDomainId,
            @ForAll("validSegments") String urlSolutionId,
            @ForAll("validSegments") String tokenDomainId,
            @ForAll("validSegments") String tokenSolutionId,
            @ForAll("resourceSuffixes") String resource) {

        Assume.that(!urlSolutionId.equals("solutions"));

        String path = "/" + urlDomainId + "/" + urlSolutionId + resource;

        ValidationResult result = validateMembership(path, true,
                GlobalAdminConstants.GLOBAL_DOMAIN, GlobalAdminConstants.GLOBAL_SOLUTION);

        assertThat(result)
                .as("Global admin should bypass membership for path '%s'", path)
                .isEqualTo(ValidationResult.ALLOWED);
    }

    // ========== Property: Global admin bypasses membership for solution management paths ==========

    @Property(tries = 200)
    @Label("Global admin environment bypasses membership validation for solution management paths")
    void adminBypassesMembershipForSolutionPaths(
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

    @Property(tries = 200)
    @Label("Non-global-admin environments with mismatched domain are rejected (contrast)")
    void nonAdminWithMismatchedDomainIsRejected(
            @ForAll("validSegments") String urlDomainId,
            @ForAll("validSegments") String tokenDomainId,
            @ForAll("validSegments") String solutionId,
            @ForAll("resourceSuffixes") String resource) {

        Assume.that(!solutionId.equals("solutions"));
        Assume.that(!urlDomainId.equals(tokenDomainId));
        Assume.that(!GlobalAdminConstants.isGlobalAdmin(tokenDomainId, solutionId));

        String path = "/" + urlDomainId + "/" + solutionId + resource;

        ValidationResult result = validateMembership(path, true, tokenDomainId, solutionId);

        assertThat(result)
                .as("Non-global-admin with mismatched domain should be DOMAIN_MISMATCH for path '%s'",
                        path)
                .isEqualTo(ValidationResult.DOMAIN_MISMATCH);
    }

    // ========== Property: Non-global-admin with mismatched solution is rejected ==========

    @Property(tries = 200)
    @Label("Non-global-admin environments with mismatched solution are rejected (contrast)")
    void nonAdminWithMismatchedSolutionIsRejected(
            @ForAll("validSegments") String domainId,
            @ForAll("validSegments") String urlSolutionId,
            @ForAll("validSegments") String tokenSolutionId,
            @ForAll("resourceSuffixes") String resource) {

        Assume.that(!urlSolutionId.equals("solutions"));
        Assume.that(!tokenSolutionId.equals("solutions"));
        Assume.that(!urlSolutionId.equals(tokenSolutionId));
        Assume.that(!GlobalAdminConstants.isGlobalAdmin(domainId, tokenSolutionId));

        String path = "/" + domainId + "/" + urlSolutionId + resource;

        ValidationResult result = validateMembership(path, true, domainId, tokenSolutionId);

        assertThat(result)
                .as("Non-global-admin with mismatched solution should be SOLUTION_MISMATCH for path '%s'",
                        path)
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
}
