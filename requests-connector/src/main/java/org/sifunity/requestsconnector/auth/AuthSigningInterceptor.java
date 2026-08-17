// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.auth;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.extension.ProviderRequestContext;
import org.sifunity.broker.common.extension.ProviderRequestInterceptor;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Interceptor that signs outbound provider requests with SIF HMAC-SHA256 authentication.
 *
 * <p>Before each outbound HTTP call to a provider endpoint, this interceptor:
 * <ol>
 *   <li>Retrieves the provider environment's session token and shared secret</li>
 *   <li>Generates an ISO-8601 timestamp</li>
 *   <li>Computes an HMAC-SHA256 signature</li>
 *   <li>Injects {@code authorization} and {@code timestamp} headers</li>
 * </ol>
 *
 * <p>If credentials cannot be retrieved (cache miss + fetch failure), the request
 * is forwarded unsigned. A null {@code providerEnvironmentId} causes a silent skip.</p>
 *
 * <p>Requirements: 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 6.1, 8.1–8.4, 9.1, 9.2</p>
 */
@Component
@Slf4j
public class AuthSigningInterceptor implements ProviderRequestInterceptor {

    private final ProviderCredentialService credentialService;

    public AuthSigningInterceptor(ProviderCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @Override
    public void beforeProviderRequest(ProviderRequestContext context) {
        if (context.providerEnvironmentId() == null) {
            return;
        }

        Optional<ProviderCredentials> creds = credentialService.getCredentials(
                context.providerEnvironmentId());

        if (creds.isEmpty()) {
            log.error("Cannot sign request: credentials unavailable for providerEnvironmentId={}",
                    context.providerEnvironmentId());
            return;
        }

        String timestamp = Instant.now().toString();
        String hmac = SifAuthenticationUtils.computeHmac(
                creds.get().sessionToken(), timestamp, creds.get().sharedSecret());

        String payload = creds.get().sessionToken() + ":" + hmac;
        String authValue = "SIF_HMACSHA256 " + Base64.getEncoder()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        // Remove existing authorization/timestamp headers (case-insensitive)
        context.requestHeaders().entrySet()
                .removeIf(e -> e.getKey().equalsIgnoreCase("authorization")
                        || e.getKey().equalsIgnoreCase("timestamp"));

        context.requestHeaders().put("authorization", authValue);
        context.requestHeaders().put("timestamp", timestamp);
    }

    @Override
    public void afterProviderRequest(ProviderRequestContext context) {
        // No cleanup needed
    }
}
