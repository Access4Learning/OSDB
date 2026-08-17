// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.extension.ProviderRequestContext;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.sifunity.requestsconnector.auth.AuthSigningInterceptor;
import org.sifunity.requestsconnector.auth.ProviderCredentialService;
import org.sifunity.requestsconnector.auth.ProviderCredentials;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthSigningInterceptor.
 *
 * <p>Validates Requirements:</p>
 * <ul>
 *   <li>3.1: Generate ISO-8601 timestamp and attach as timestamp header</li>
 *   <li>3.2: Compute HMAC via SifAuthenticationUtils.computeHmac</li>
 *   <li>3.3: Set authorization header to SIF_HMACSHA256 Base64({sessionToken}:{hmac})</li>
 *   <li>4.1: Set authorization header on requestHeaders map</li>
 *   <li>4.2: Set timestamp header on requestHeaders map</li>
 *   <li>4.3: Overwrite existing authorization/timestamp headers regardless of casing</li>
 *   <li>4.5: Null providerEnvironmentId does not modify requestHeaders</li>
 *   <li>8.1: Log ERROR and skip signing when session token unavailable</li>
 *   <li>8.2: Log ERROR and skip signing when shared secret unavailable</li>
 *   <li>8.3: Forward request without auth headers when signing skipped</li>
 *   <li>8.4: Null providerEnvironmentId skips without ERROR/WARN log</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthSigningInterceptor Unit Tests")
class AuthSigningInterceptorTest {

    @Mock
    private ProviderCredentialService credentialService;

    private AuthSigningInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AuthSigningInterceptor(credentialService);
    }

    // ========== Known-Good HMAC Tests (Requirements 3.1, 3.2, 3.3, 4.1, 4.2) ==========

    @Test
    @DisplayName("Known-good HMAC computation produces expected authorization header")
    void knownGoodHmac_producesExpectedAuthorizationHeader() {
        // Given
        UUID envId = UUID.randomUUID();
        String sessionToken = "test-session-token-abc123";
        String sharedSecret = "my-secret-key-xyz789";
        Map<String, String> headers = new HashMap<>();

        when(credentialService.getCredentials(envId))
                .thenReturn(Optional.of(new ProviderCredentials(sessionToken, sharedSecret)));

        ProviderRequestContext context = new ProviderRequestContext(
                "domain1", "solution1", envId, "https://provider.example.com/students", headers);

        // When
        interceptor.beforeProviderRequest(context);

        // Then - authorization header present and starts with correct prefix
        assertThat(headers).containsKey("authorization");
        assertThat(headers).containsKey("timestamp");

        String authHeader = headers.get("authorization");
        String timestamp = headers.get("timestamp");

        // Verify format: SIF_HMACSHA256 <base64>
        assertThat(authHeader).startsWith("SIF_HMACSHA256 ");

        // Decode Base64 payload
        String base64Part = authHeader.substring("SIF_HMACSHA256 ".length());
        String decoded = new String(Base64.getDecoder().decode(base64Part), StandardCharsets.UTF_8);

        // Payload format: {sessionToken}:{hmac}
        int colonIndex = decoded.indexOf(':');
        assertThat(colonIndex).isGreaterThan(0);

        String parsedToken = decoded.substring(0, colonIndex);
        String parsedHmac = decoded.substring(colonIndex + 1);

        // Session token in header matches input
        assertThat(parsedToken).isEqualTo(sessionToken);

        // Recompute HMAC and verify it matches
        String expectedHmac = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);
        assertThat(parsedHmac).isEqualTo(expectedHmac);

        // Timestamp is valid ISO-8601 instant
        assertThat(java.time.Instant.parse(timestamp)).isNotNull();
    }

    // ========== Null ProviderEnvironmentId Tests (Requirements 4.5, 8.4) ==========

    @Test
    @DisplayName("Null providerEnvironmentId skips signing without modifying headers")
    void nullProviderEnvironmentId_skipsSigningWithoutModifyingHeaders() {
        // Given
        Map<String, String> headers = new HashMap<>();
        headers.put("x-custom", "preserved-value");

        ProviderRequestContext context = new ProviderRequestContext(
                "domain1", "solution1", null, "https://provider.example.com/students", headers);

        // When
        interceptor.beforeProviderRequest(context);

        // Then - headers unchanged, no authorization or timestamp added
        assertThat(headers).hasSize(1);
        assertThat(headers).containsEntry("x-custom", "preserved-value");
        assertThat(headers).doesNotContainKey("authorization");
        assertThat(headers).doesNotContainKey("timestamp");

        // Credential service never called
        verifyNoInteractions(credentialService);
    }

    // ========== Credential Service Empty Tests (Requirements 8.1, 8.2, 8.3) ==========

    @Test
    @DisplayName("Credential service returning empty skips signing without adding auth headers")
    void credentialServiceEmpty_skipsSigningWithoutAuthHeaders() {
        // Given
        UUID envId = UUID.randomUUID();
        Map<String, String> headers = new HashMap<>();
        headers.put("x-existing", "keep-me");

        when(credentialService.getCredentials(envId)).thenReturn(Optional.empty());

        ProviderRequestContext context = new ProviderRequestContext(
                "domain1", "solution1", envId, "https://provider.example.com/students", headers);

        // When
        interceptor.beforeProviderRequest(context);

        // Then - no authorization or timestamp headers added
        assertThat(headers).doesNotContainKey("authorization");
        assertThat(headers).doesNotContainKey("timestamp");
        // Existing headers preserved
        assertThat(headers).containsEntry("x-existing", "keep-me");
    }

    // ========== Header Overwrite Tests (Requirement 4.3) ==========

    @Test
    @DisplayName("Existing authorization header (lowercase) is overwritten")
    void existingAuthorizationLowercase_isOverwritten() {
        // Given
        UUID envId = UUID.randomUUID();
        String sessionToken = "new-token";
        String sharedSecret = "new-secret";
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", "old-auth-value");
        headers.put("timestamp", "old-timestamp");

        when(credentialService.getCredentials(envId))
                .thenReturn(Optional.of(new ProviderCredentials(sessionToken, sharedSecret)));

        ProviderRequestContext context = new ProviderRequestContext(
                "domain1", "solution1", envId, "https://provider.example.com/students", headers);

        // When
        interceptor.beforeProviderRequest(context);

        // Then - old values replaced
        assertThat(headers.get("authorization")).startsWith("SIF_HMACSHA256 ");
        assertThat(headers.get("authorization")).isNotEqualTo("old-auth-value");
        assertThat(headers.get("timestamp")).isNotEqualTo("old-timestamp");
    }

    @Test
    @DisplayName("Existing Authorization header (mixed case) is overwritten")
    void existingAuthorizationMixedCase_isOverwritten() {
        // Given
        UUID envId = UUID.randomUUID();
        String sessionToken = "token-mixed";
        String sharedSecret = "secret-mixed";
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer old-value");
        headers.put("Timestamp", "2020-01-01T00:00:00Z");

        when(credentialService.getCredentials(envId))
                .thenReturn(Optional.of(new ProviderCredentials(sessionToken, sharedSecret)));

        ProviderRequestContext context = new ProviderRequestContext(
                "domain1", "solution1", envId, "https://provider.example.com/students", headers);

        // When
        interceptor.beforeProviderRequest(context);

        // Then - mixed case keys removed, replaced with lowercase
        assertThat(headers).doesNotContainKey("Authorization");
        assertThat(headers).doesNotContainKey("Timestamp");
        assertThat(headers).containsKey("authorization");
        assertThat(headers).containsKey("timestamp");
        assertThat(headers.get("authorization")).startsWith("SIF_HMACSHA256 ");
    }

    @Test
    @DisplayName("Existing AUTHORIZATION header (uppercase) is overwritten")
    void existingAuthorizationUppercase_isOverwritten() {
        // Given
        UUID envId = UUID.randomUUID();
        String sessionToken = "token-upper";
        String sharedSecret = "secret-upper";
        Map<String, String> headers = new HashMap<>();
        headers.put("AUTHORIZATION", "BASIC old-upper");
        headers.put("TIMESTAMP", "1999-12-31T23:59:59Z");
        headers.put("x-other", "untouched");

        when(credentialService.getCredentials(envId))
                .thenReturn(Optional.of(new ProviderCredentials(sessionToken, sharedSecret)));

        ProviderRequestContext context = new ProviderRequestContext(
                "domain1", "solution1", envId, "https://provider.example.com/students", headers);

        // When
        interceptor.beforeProviderRequest(context);

        // Then - uppercase keys removed, lowercase keys set
        assertThat(headers).doesNotContainKey("AUTHORIZATION");
        assertThat(headers).doesNotContainKey("TIMESTAMP");
        assertThat(headers).containsKey("authorization");
        assertThat(headers).containsKey("timestamp");
        assertThat(headers.get("authorization")).startsWith("SIF_HMACSHA256 ");
        // Other headers untouched
        assertThat(headers).containsEntry("x-other", "untouched");
    }
}
