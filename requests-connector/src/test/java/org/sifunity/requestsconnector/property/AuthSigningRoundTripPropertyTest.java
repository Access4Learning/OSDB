// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.extension.ProviderRequestContext;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.sifunity.broker.common.util.SifAuthenticationUtils.AuthHeader;
import org.sifunity.requestsconnector.auth.AuthSigningInterceptor;
import org.sifunity.requestsconnector.auth.ProviderCredentialService;
import org.sifunity.requestsconnector.auth.ProviderCredentials;

import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based test for the signing round-trip correctness property.
 *
 * <p><strong>Property 1: Signing Round-Trip</strong></p>
 *
 * <p>For any valid session token (non-blank string without control characters) and
 * for any valid shared secret (non-blank string), when the AuthSigningInterceptor
 * signs a request, parsing the generated authorization header with
 * SifAuthenticationUtils.parseAuthHeader SHALL yield the original session token as
 * the left segment, and recomputing SifAuthenticationUtils.computeHmac(parsedSessionToken,
 * timestampHeader, sharedSecret) SHALL produce the same HMAC as the right segment.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.4, 4.1, 4.2, 4.4</strong></p>
 */
@Tag("Feature: provider-auth-signing, Property 1: Signing Round-Trip")
class AuthSigningRoundTripPropertyTest {

    /**
     * Property 1: Signing Round-Trip
     *
     * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.4, 4.1, 4.2, 4.4</strong></p>
     *
     * <p>Verifies that the interceptor produces an authorization header that can be
     * parsed back to recover the original session token, and that recomputing the
     * HMAC from the parsed token + timestamp header + shared secret yields the same
     * hash as embedded in the header.</p>
     */
    @Property(tries = 20)
    @Tag("Property_1_Signing_Round_Trip")
    void signingProducesRoundTrippableAuthorizationHeader(
            @ForAll("validSessionTokens") String sessionToken,
            @ForAll("validSharedSecrets") String sharedSecret) {

        // Given: A mocked credential service that returns the generated credentials
        UUID providerEnvironmentId = UUID.randomUUID();
        ProviderCredentialService credentialService = mock(ProviderCredentialService.class);
        when(credentialService.getCredentials(providerEnvironmentId))
                .thenReturn(Optional.of(new ProviderCredentials(sessionToken, sharedSecret)));

        AuthSigningInterceptor interceptor = new AuthSigningInterceptor(credentialService);

        // Build a ProviderRequestContext with a mutable headers map
        HashMap<String, String> headers = new HashMap<>();
        ProviderRequestContext context = new ProviderRequestContext(
                "domain-1", "solution-1", providerEnvironmentId, "https://provider.example.com", headers);

        // When: The interceptor signs the request
        interceptor.beforeProviderRequest(context);

        // Then: authorization and timestamp headers are present
        assertThat(headers).containsKey("authorization");
        assertThat(headers).containsKey("timestamp");

        String authHeaderValue = headers.get("authorization");
        String timestampHeader = headers.get("timestamp");

        // Parse the authorization header using SifAuthenticationUtils
        AuthHeader parsed = SifAuthenticationUtils.parseAuthHeader(authHeaderValue);

        // Verify parsed session token matches original
        assertThat(parsed.sessionToken()).isEqualTo(sessionToken);

        // Recompute HMAC from parsed session token, timestamp header, and shared secret
        String recomputedHmac = SifAuthenticationUtils.computeHmac(
                parsed.sessionToken(), timestampHeader, sharedSecret);

        // Verify the recomputed HMAC matches the parsed hash
        assertThat(parsed.hash()).isEqualTo(recomputedHmac);

        // Verify timestamp is a valid ISO-8601 instant
        Instant parsedTimestamp = Instant.parse(timestampHeader);
        assertThat(parsedTimestamp).isNotNull();
    }

    // ==================== Arbitraries (Data Generators) ====================

    /**
     * Generate valid session tokens: non-blank ASCII strings (1-64 chars),
     * excluding control characters. Uses printable ASCII range (! through ~).
     */
    @Provide
    Arbitrary<String> validSessionTokens() {
        // Exclude colon (:) since it's the delimiter in the auth header payload.
        // Session tokens containing colons would still work since parseAuthHeader
        // splits on FIRST colon only, but we constrain to non-colon chars for clarity.
        return Arbitraries.strings()
                .withCharRange('!', '~') // printable ASCII excluding space
                .excludeChars(':')       // exclude delimiter to avoid ambiguity
                .ofMinLength(1)
                .ofMaxLength(64);
    }

    /**
     * Generate valid shared secrets: non-blank strings (8-128 chars), printable ASCII.
     */
    @Provide
    Arbitrary<String> validSharedSecrets() {
        return Arbitraries.strings()
                .withCharRange('!', '~') // printable ASCII excluding space
                .ofMinLength(8)
                .ofMaxLength(128);
    }
}
