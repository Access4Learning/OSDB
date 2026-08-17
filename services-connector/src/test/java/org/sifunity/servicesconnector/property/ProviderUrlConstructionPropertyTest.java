// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.property;

import net.jqwik.api.*;
import org.sifunity.servicesconnector.service.FunctionalServiceRoutingService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 3: Provider URL path construction.
 *
 * <p>For any combination of functionalServiceName, jobId, and phaseName path
 * segments, the services-connector SHALL construct the provider-facing URL by
 * appending the correct path suffix to the provider's registered endpoint.</p>
 *
 * <p>URL construction rules:</p>
 * <ul>
 *   <li>{@code /services/{name}} → {@code {endpoint}/{name}}</li>
 *   <li>{@code /services/{name}/{jobId}} → {@code {endpoint}/{name}/{jobId}}</li>
 *   <li>{@code /services/{name}/{jobId}/{phase}} → {@code {endpoint}/{name}/{jobId}/{phase}}</li>
 *   <li>{@code /services/{name}/{jobId}/{phase}/states} → {@code {endpoint}/{name}/{jobId}/{phase}/states}</li>
 *   <li>{@code /services/{name}/{jobId}/{phase}/states/state} → {@code {endpoint}/{name}/{jobId}/{phase}/states/state}</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements 2.6, 2.7</strong></p>
 */
@PropertyDefaults(tries = 20)
class ProviderUrlConstructionPropertyTest {

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> providerEndpoints() {
        Arbitrary<String> hosts = Arbitraries.of(
                "http://provider.example.com",
                "https://localhost:8080",
                "http://10.0.0.1:9090/api",
                "https://provider-svc.cluster.local"
        );
        Arbitrary<String> paths = Arbitraries.of("", "/v1", "/sif/services");
        return Combinators.combine(hosts, paths).as((host, path) -> host + path);
    }

    @Provide
    Arbitrary<String> providerEndpointsWithTrailingSlash() {
        return providerEndpoints().map(e -> e + "/");
    }

    @Provide
    Arbitrary<String> alphanumericNames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(30);
    }

    @Provide
    Arbitrary<String> jobIds() {
        return Arbitraries.oneOf(
                Arbitraries.create(() -> java.util.UUID.randomUUID().toString()),
                alphanumericNames()
        );
    }

    // ========== Properties ==========

    /**
     * Pattern 1: Service-level URL ({@code /services/{name}}).
     * When jobId and pathSuffix are null, the URL is {@code {endpoint}/{name}}.
     *
     * <p><strong>Validates: Requirements 2.6, 2.7</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 3: Provider URL path construction")
    void serviceLevelUrl_appendsOnlyServiceName(
            @ForAll("providerEndpoints") String endpoint,
            @ForAll("alphanumericNames") String serviceName) {

        String url = FunctionalServiceRoutingService.buildProviderUrl(
                endpoint, serviceName, null, null);

        assertThat(url).isEqualTo(endpoint + "/" + serviceName);
    }

    /**
     * Pattern 2: Job-level URL ({@code /services/{name}/{jobId}}).
     * When pathSuffix is null, the URL is {@code {endpoint}/{name}/{jobId}}.
     *
     * <p><strong>Validates: Requirements 2.6, 2.7</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 3: Provider URL path construction")
    void jobLevelUrl_appendsServiceNameAndJobId(
            @ForAll("providerEndpoints") String endpoint,
            @ForAll("alphanumericNames") String serviceName,
            @ForAll("jobIds") String jobId) {

        String url = FunctionalServiceRoutingService.buildProviderUrl(
                endpoint, serviceName, jobId, null);

        assertThat(url).isEqualTo(endpoint + "/" + serviceName + "/" + jobId);
    }

    /**
     * Pattern 3: Phase-level URL ({@code /services/{name}/{jobId}/{phase}}).
     * When pathSuffix is the phase name, the URL is {@code {endpoint}/{name}/{jobId}/{phase}}.
     *
     * <p><strong>Validates: Requirements 2.6, 2.7</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 3: Provider URL path construction")
    void phaseLevelUrl_appendsServiceNameJobIdAndPhase(
            @ForAll("providerEndpoints") String endpoint,
            @ForAll("alphanumericNames") String serviceName,
            @ForAll("jobIds") String jobId,
            @ForAll("alphanumericNames") String phaseName) {

        String url = FunctionalServiceRoutingService.buildProviderUrl(
                endpoint, serviceName, jobId, phaseName);

        assertThat(url).isEqualTo(
                endpoint + "/" + serviceName + "/" + jobId + "/" + phaseName);
    }

    /**
     * Pattern 4: Phase states URL ({@code /services/{name}/{jobId}/{phase}/states}).
     * When pathSuffix is "{phase}/states", the URL includes the states sub-resource.
     *
     * <p><strong>Validates: Requirements 2.6, 2.7</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 3: Provider URL path construction")
    void phaseStatesUrl_appendsStatesSubResource(
            @ForAll("providerEndpoints") String endpoint,
            @ForAll("alphanumericNames") String serviceName,
            @ForAll("jobIds") String jobId,
            @ForAll("alphanumericNames") String phaseName) {

        String pathSuffix = phaseName + "/states";

        String url = FunctionalServiceRoutingService.buildProviderUrl(
                endpoint, serviceName, jobId, pathSuffix);

        assertThat(url).isEqualTo(
                endpoint + "/" + serviceName + "/" + jobId + "/" + phaseName + "/states");
    }

    /**
     * Pattern 5: Phase state URL ({@code /services/{name}/{jobId}/{phase}/states/state}).
     * When pathSuffix is "{phase}/states/state", the URL includes the state sub-resource.
     *
     * <p><strong>Validates: Requirements 2.6, 2.7</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 3: Provider URL path construction")
    void phaseStateUrl_appendsStateSubResource(
            @ForAll("providerEndpoints") String endpoint,
            @ForAll("alphanumericNames") String serviceName,
            @ForAll("jobIds") String jobId,
            @ForAll("alphanumericNames") String phaseName) {

        String pathSuffix = phaseName + "/states/state";

        String url = FunctionalServiceRoutingService.buildProviderUrl(
                endpoint, serviceName, jobId, pathSuffix);

        assertThat(url).isEqualTo(
                endpoint + "/" + serviceName + "/" + jobId + "/" + phaseName + "/states/state");
    }

    /**
     * Trailing slash on provider endpoint SHALL be stripped before constructing the URL.
     * This ensures no double-slash appears in the constructed URL.
     *
     * <p><strong>Validates: Requirements 2.6, 2.7</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 3: Provider URL path construction")
    void trailingSlashOnEndpointIsStripped(
            @ForAll("providerEndpointsWithTrailingSlash") String endpointWithSlash,
            @ForAll("alphanumericNames") String serviceName,
            @ForAll("jobIds") String jobId,
            @ForAll("alphanumericNames") String phaseName) {

        String endpointWithoutSlash = endpointWithSlash.substring(0, endpointWithSlash.length() - 1);

        String url = FunctionalServiceRoutingService.buildProviderUrl(
                endpointWithSlash, serviceName, jobId, phaseName);

        // Should produce the same URL as without trailing slash
        String expectedUrl = FunctionalServiceRoutingService.buildProviderUrl(
                endpointWithoutSlash, serviceName, jobId, phaseName);

        assertThat(url).isEqualTo(expectedUrl);
        // No double slashes after protocol
        String afterProtocol = url.replaceFirst("^https?://", "");
        assertThat(afterProtocol).doesNotContain("//");
    }

    /**
     * The constructed URL SHALL never contain double slashes (except in the protocol).
     * This is a structural invariant across all URL patterns.
     *
     * <p><strong>Validates: Requirements 2.6, 2.7</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 3: Provider URL path construction")
    void constructedUrlNeverContainsDoubleSlashAfterProtocol(
            @ForAll("providerEndpoints") String endpoint,
            @ForAll("alphanumericNames") String serviceName,
            @ForAll("jobIds") String jobId,
            @ForAll("alphanumericNames") String phaseName) {

        String url = FunctionalServiceRoutingService.buildProviderUrl(
                endpoint, serviceName, jobId, phaseName);

        // Strip protocol prefix, then check no double slashes remain
        String afterProtocol = url.replaceFirst("^https?://", "");
        assertThat(afterProtocol).doesNotContain("//");
    }
}
