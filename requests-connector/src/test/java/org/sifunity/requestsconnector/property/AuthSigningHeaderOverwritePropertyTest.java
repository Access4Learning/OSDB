// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.extension.ProviderRequestContext;
import org.sifunity.requestsconnector.auth.AuthSigningInterceptor;
import org.sifunity.requestsconnector.auth.ProviderCredentialService;
import org.sifunity.requestsconnector.auth.ProviderCredentials;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based test for header overwrite (case-insensitive) behavior.
 *
 * <p><strong>Property 2: Header Overwrite (Case-Insensitive)</strong></p>
 *
 * <p>For any mutable header map containing entries with arbitrary case variations of
 * "authorization" or "timestamp" keys (e.g., "Authorization", "AUTHORIZATION", "Timestamp"),
 * after the AuthSigningInterceptor executes beforeProviderRequest with valid credentials,
 * the resulting map SHALL contain exactly one "authorization" entry and exactly one
 * "timestamp" entry (both lowercase), and no other case variants of those keys.</p>
 *
 * <p><strong>Validates: Requirements 4.3</strong></p>
 */
@Tag("Feature: provider-auth-signing, Property 2: Header Overwrite (Case-Insensitive)")
class AuthSigningHeaderOverwritePropertyTest {

    /**
     * Property 2: Header Overwrite (Case-Insensitive)
     *
     * <p><strong>Validates: Requirements 4.3</strong></p>
     *
     * <p>After signing, the header map contains exactly one lowercase "authorization"
     * and one lowercase "timestamp" entry, with no other case variants remaining.
     * Non-matching headers remain untouched.</p>
     */
    @Property(tries = 20)
    @Tag("Property_2_Header_Overwrite_Case_Insensitive")
    void headerOverwriteRemovesCaseVariants(
            @ForAll("headerMapsWithCaseVariants") Map<String, String> headers,
            @ForAll("otherHeaders") Map<String, String> otherHeaders) {

        // Merge other headers into the main map
        Map<String, String> mutableHeaders = new HashMap<>(headers);
        mutableHeaders.putAll(otherHeaders);

        // Snapshot of non-auth/non-timestamp headers before interceptor runs
        Map<String, String> expectedOtherHeaders = new HashMap<>();
        for (Map.Entry<String, String> entry : mutableHeaders.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase("authorization")
                    && !entry.getKey().equalsIgnoreCase("timestamp")) {
                expectedOtherHeaders.put(entry.getKey(), entry.getValue());
            }
        }

        // Mock credential service to return valid credentials
        ProviderCredentialService credentialService = mock(ProviderCredentialService.class);
        ProviderCredentials creds = new ProviderCredentials("testToken123", "sharedSecret456");
        when(credentialService.getCredentials(any(UUID.class))).thenReturn(Optional.of(creds));

        AuthSigningInterceptor interceptor = new AuthSigningInterceptor(credentialService);

        UUID envId = UUID.randomUUID();
        ProviderRequestContext context = new ProviderRequestContext(
                "domain1", "solution1", envId, "http://provider.example.com", mutableHeaders);

        // When
        interceptor.beforeProviderRequest(context);

        // Then: exactly one "authorization" key (lowercase)
        long authCount = mutableHeaders.keySet().stream()
                .filter(k -> k.equals("authorization"))
                .count();
        assertThat(authCount).as("Exactly one lowercase 'authorization' key").isEqualTo(1);

        // Then: exactly one "timestamp" key (lowercase)
        long timestampCount = mutableHeaders.keySet().stream()
                .filter(k -> k.equals("timestamp"))
                .count();
        assertThat(timestampCount).as("Exactly one lowercase 'timestamp' key").isEqualTo(1);

        // Then: no other case variants of "authorization" or "timestamp"
        long authVariants = mutableHeaders.keySet().stream()
                .filter(k -> k.equalsIgnoreCase("authorization") && !k.equals("authorization"))
                .count();
        assertThat(authVariants).as("No non-lowercase authorization variants").isEqualTo(0);

        long timestampVariants = mutableHeaders.keySet().stream()
                .filter(k -> k.equalsIgnoreCase("timestamp") && !k.equals("timestamp"))
                .count();
        assertThat(timestampVariants).as("No non-lowercase timestamp variants").isEqualTo(0);

        // Then: other non-matching headers remain untouched
        for (Map.Entry<String, String> entry : expectedOtherHeaders.entrySet()) {
            assertThat(mutableHeaders).containsEntry(entry.getKey(), entry.getValue());
        }
    }

    // ==================== Arbitraries (Data Generators) ====================

    /**
     * Generate header maps with random case variations of "authorization" and "timestamp" keys.
     * Includes 1-3 case variants of each key with random values.
     */
    @Provide
    Arbitrary<Map<String, String>> headerMapsWithCaseVariants() {
        Arbitrary<List<String>> authKeys = authorizationCaseVariants().list().ofMinSize(1).ofMaxSize(3);
        Arbitrary<List<String>> tsKeys = timestampCaseVariants().list().ofMinSize(1).ofMaxSize(3);
        Arbitrary<String> values = Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(20);

        return Combinators.combine(authKeys, tsKeys, values).as((authList, tsList, val) -> {
            Map<String, String> map = new HashMap<>();
            for (String key : authList) {
                map.put(key, "Bearer " + val + UUID.randomUUID().toString().substring(0, 8));
            }
            for (String key : tsList) {
                map.put(key, "2024-01-01T00:00:00Z");
            }
            return map;
        });
    }

    /**
     * Generate random case variations of "authorization".
     */
    private Arbitrary<String> authorizationCaseVariants() {
        return Arbitraries.of(
                "authorization", "Authorization", "AUTHORIZATION",
                "AuThOrIzAtIoN", "authorizaTION", "AUTHORIZATION");
    }

    /**
     * Generate random case variations of "timestamp".
     */
    private Arbitrary<String> timestampCaseVariants() {
        return Arbitraries.of(
                "timestamp", "Timestamp", "TIMESTAMP",
                "TimeStamp", "tImEsTaMp", "TIMESTAMP");
    }

    /**
     * Generate 0-5 other random headers that do NOT match "authorization" or "timestamp"
     * (case-insensitive). These should remain untouched after interceptor runs.
     */
    @Provide
    Arbitrary<Map<String, String>> otherHeaders() {
        Arbitrary<String> keys = Arbitraries.of(
                "content-type", "accept", "x-request-id", "x-correlation-id",
                "host", "user-agent", "cache-control", "x-forwarded-for");
        Arbitrary<String> values = Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(30);

        return Combinators.combine(keys, values).as((key, value) -> {
            Map<String, String> map = new HashMap<>();
            map.put(key, value);
            return map;
        }).list().ofMinSize(0).ofMaxSize(5).map(list -> {
            Map<String, String> merged = new HashMap<>();
            for (Map<String, String> m : list) {
                merged.putAll(m);
            }
            return merged;
        });
    }
}
