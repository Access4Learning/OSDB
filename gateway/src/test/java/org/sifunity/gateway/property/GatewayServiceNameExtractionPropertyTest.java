// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.sifunity.gateway.util.UrlPrefixParser;
import org.sifunity.gateway.util.UrlPrefixParser.ParsedPath;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for gateway service name extraction from functional service URLs.
 *
 * <p><strong>Property 5: Gateway service name extraction from functional service URL</strong></p>
 *
 * <p>For any request path matching {@code /{domainId}/{solutionId}/services/{functionalServiceName}/**},
 * the AuthorizationFilter SHALL extract the functionalServiceName as the service name for ACL lookup,
 * not the literal string "services".</p>
 *
 * <p><strong>Validates: Requirements 4.1</strong></p>
 *
 * @see org.sifunity.gateway.filter.AuthorizationFilter#extractServiceName
 */
@Tag("sif-functional-services-redesign")
@Tag("property-5-gateway-service-name-extraction")
class GatewayServiceNameExtractionPropertyTest {

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
     * For any domainId, solutionId, and functionalServiceName, extracting the service name
     * from {@code /{domainId}/{solutionId}/services/{functionalServiceName}} returns the
     * functionalServiceName, not the literal "services".
     */
    @Property(tries = 20)
    @Label("extractServiceName returns functionalServiceName from /services/{name} path")
    void extractsServiceNameFromJobLevelPath(
            @ForAll("alphanumericSegments") String domainId,
            @ForAll("alphanumericSegments") String solutionId,
            @ForAll("alphanumericSegments") String functionalServiceName) {

        Assume.that(!solutionId.equals("solutions"));

        String path = "/" + domainId + "/" + solutionId + "/services/" + functionalServiceName;

        String extracted = extractServiceName(path);

        assertThat(extracted)
                .as("extractServiceName('%s') should return '%s', not 'services'",
                        path, functionalServiceName)
                .isEqualTo(functionalServiceName)
                .isNotEqualTo("services");
    }

    /**
     * For any domainId, solutionId, functionalServiceName, and jobId, extracting the service
     * name from {@code /{domainId}/{solutionId}/services/{functionalServiceName}/{jobId}}
     * still returns the functionalServiceName.
     */
    @Property(tries = 20)
    @Label("extractServiceName returns functionalServiceName from /services/{name}/{jobId} path")
    void extractsServiceNameFromJobDetailPath(
            @ForAll("alphanumericSegments") String domainId,
            @ForAll("alphanumericSegments") String solutionId,
            @ForAll("alphanumericSegments") String functionalServiceName,
            @ForAll("alphanumericSegments") String jobId) {

        Assume.that(!solutionId.equals("solutions"));

        String path = "/" + domainId + "/" + solutionId + "/services/" + functionalServiceName + "/" + jobId;

        String extracted = extractServiceName(path);

        assertThat(extracted)
                .as("extractServiceName('%s') should return '%s'", path, functionalServiceName)
                .isEqualTo(functionalServiceName);
    }

    /**
     * For any domainId, solutionId, functionalServiceName, jobId, and phaseName,
     * extracting the service name from a phase-level path still returns the functionalServiceName.
     */
    @Property(tries = 20)
    @Label("extractServiceName returns functionalServiceName from phase-level path")
    void extractsServiceNameFromPhaseLevelPath(
            @ForAll("alphanumericSegments") String domainId,
            @ForAll("alphanumericSegments") String solutionId,
            @ForAll("alphanumericSegments") String functionalServiceName,
            @ForAll("alphanumericSegments") String jobId,
            @ForAll("alphanumericSegments") String phaseName) {

        Assume.that(!solutionId.equals("solutions"));

        String path = "/" + domainId + "/" + solutionId + "/services/"
                + functionalServiceName + "/" + jobId + "/" + phaseName;

        String extracted = extractServiceName(path);

        assertThat(extracted)
                .as("extractServiceName('%s') should return '%s'", path, functionalServiceName)
                .isEqualTo(functionalServiceName);
    }

    /**
     * For state operation paths ({@code /services/{name}/{jobId}/{phase}/states/state}),
     * the extracted service name is still the functionalServiceName.
     */
    @Property(tries = 20)
    @Label("extractServiceName returns functionalServiceName from state operation path")
    void extractsServiceNameFromStateOperationPath(
            @ForAll("alphanumericSegments") String domainId,
            @ForAll("alphanumericSegments") String solutionId,
            @ForAll("alphanumericSegments") String functionalServiceName,
            @ForAll("alphanumericSegments") String jobId,
            @ForAll("alphanumericSegments") String phaseName) {

        Assume.that(!solutionId.equals("solutions"));

        String path = "/" + domainId + "/" + solutionId + "/services/"
                + functionalServiceName + "/" + jobId + "/" + phaseName + "/states/state";

        String extracted = extractServiceName(path);

        assertThat(extracted)
                .as("extractServiceName('%s') should return '%s'", path, functionalServiceName)
                .isEqualTo(functionalServiceName);
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
}
