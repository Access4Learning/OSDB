// SPDX-License-Identifier: MIT
package org.sifunity.integration.security;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.sifunity.integration.base.ServiceEnabledIntegrationTestBase;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for internal path blocking.
 *
 * <p><strong>Property 5: Internal path blocking</strong></p>
 * <p>For any request path matching {@code /internal/**}, the Gateway SHALL return
 * HTTP 403 Forbidden regardless of authentication status.</p>
 *
 * <p>This is a lightweight integration-level property test that only hits the Gateway.
 * No environment setup is needed since the {@code InternalApiBlockingFilter} runs
 * at Order -1 (before authentication) and blocks all {@code /internal/**} paths
 * unconditionally.</p>
 *
 * <p><strong>Validates: Requirements 8.3</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("security")
@Tag("Feature: cross-service-integration-tests, Property 5: Internal path blocking")
public class InternalPathBlockingPropertyTest extends ServiceEnabledIntegrationTestBase {

    private boolean infrastructureReady = false;

    /**
     * Lazily starts infrastructure once before all property iterations.
     */
    private void ensureInfrastructure() {
        if (infrastructureReady) {
            return;
        }
        log.info("Setting up infrastructure for InternalPathBlockingPropertyTest");
        setupInfrastructure();
        infrastructureReady = true;
    }

    // ── Arbitraries ──────────────────────────────────────────────────

    /**
     * Generates random alphanumeric path suffixes (e.g., "abc123").
     */
    @Provide
    Arbitrary<String> alphanumericSuffixes() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(1)
            .ofMaxLength(30);
    }

    /**
     * Generates random path suffixes with slashes (e.g., "foo/bar/baz").
     */
    @Provide
    Arbitrary<String> pathsWithSlashes() {
        Arbitrary<String> segment = Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(10);
        return segment.list().ofMinSize(2).ofMaxSize(5)
            .map(segments -> String.join("/", segments));
    }

    /**
     * Generates random path suffixes with special characters
     * (hyphens, underscores, dots mixed with alphanumeric).
     */
    @Provide
    Arbitrary<String> pathsWithSpecialChars() {
        return Arbitraries.frequencyOf(
            Tuple.of(3, Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8)),
            Tuple.of(1, Arbitraries.of("-", "_", "."))
        ).list().ofMinSize(2).ofMaxSize(6)
            .map(parts -> String.join("", parts));
    }

    /**
     * Generates a combined arbitrary of all suffix types for property iteration.
     */
    @Provide
    Arbitrary<String> randomPathSuffixes() {
        return Arbitraries.oneOf(
            alphanumericSuffixes(),
            pathsWithSlashes(),
            pathsWithSpecialChars()
        );
    }

    // ── Property ─────────────────────────────────────────────────────

    /**
     * <strong>Property 5: Internal path blocking</strong>
     *
     * <p>For any random path suffix appended to {@code /internal/}, the Gateway
     * SHALL return HTTP 403 Forbidden.</p>
     *
     * <p>The {@code InternalApiBlockingFilter} blocks at root level
     * {@code /internal/**} (not under {@code /{domainId}/{solutionId}/}).</p>
     *
     * <p><strong>Validates: Requirements 8.3</strong></p>
     */
    @Property(tries = 10)
    @Tag("Feature: cross-service-integration-tests, Property 5: Internal path blocking")
    void internalPathBlockingForAnyPathSuffix(
        @ForAll("randomPathSuffixes") String pathSuffix
    ) {
        ensureInfrastructure();

        String internalPath = "/internal/" + pathSuffix;
        log.info("Property iteration: testing path '{}'", internalPath);

        Response response = given()
            .contentType("application/json")
            .accept("application/json")
            .header("timestamp", Instant.now().toString())
            .header("messageId", UUID.randomUUID().toString())
        .when()
            .get(internalPath)
        .then()
            .extract().response();

        log.info("Response status for '{}': {}", internalPath, response.getStatusCode());

        assertThat(response.getStatusCode())
            .as("Gateway should return 403 for internal path '%s'", internalPath)
            .isEqualTo(403);
    }
}
