// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.property;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.sifunity.testprovider.config.ProviderConfig;
import org.sifunity.testprovider.dto.ConfigureRequest;
import org.sifunity.testprovider.service.RuntimeConfigServiceImpl;

/**
 * Property 2: gatewayUrl retention.
 *
 * <p><b>Validates: Requirements 1.4, 1.5</b>
 *
 * <p>For any current config {@code C} and any valid request {@code R} that omits {@code gatewayUrl},
 * after {@code configure(R)} the {@code gatewayUrl} equals {@code C.gatewayUrl} (Requirement 1.4).
 * When a valid {@code gatewayUrl} is provided, it is stored trimmed (Requirement 1.5).
 *
 * <pre>∀ C, R: R.gatewayUrl = null ⟹ after.gatewayUrl = C.gatewayUrl</pre>
 */
class GatewayUrlRetentionPropertyTest {

    /**
     * When a valid configure request omits {@code gatewayUrl} (null), the prior gateway URL seeded into
     * the config is retained unchanged after configure.
     */
    @Property(tries = 20)
    void omittedGatewayUrlRetainsPriorValue(
            @ForAll("validHttpUrls") String priorGatewayUrl,
            @ForAll("validRequestsWithoutGatewayUrl") ConfigureRequest request) {

        ProviderConfig config = new ProviderConfig();
        config.setGatewayUrl(priorGatewayUrl);
        RuntimeConfigServiceImpl service = new RuntimeConfigServiceImpl(config, Optional.empty());

        service.configure(request);

        // The omitted gatewayUrl must leave the previously configured value untouched.
        assertThat(config.getGatewayUrl()).isEqualTo(priorGatewayUrl);
    }

    /**
     * When a valid configure request provides a {@code gatewayUrl} (possibly with surrounding
     * whitespace), the trimmed value is stored, replacing any prior value.
     */
    @Property(tries = 20)
    void providedGatewayUrlIsStoredTrimmed(
            @ForAll("validHttpUrls") String priorGatewayUrl,
            @ForAll("validHttpUrls") String newGatewayUrl,
            @ForAll("validRequestsWithoutGatewayUrl") ConfigureRequest base) {

        ProviderConfig config = new ProviderConfig();
        config.setGatewayUrl(priorGatewayUrl);
        RuntimeConfigServiceImpl service = new RuntimeConfigServiceImpl(config, Optional.empty());

        // Provide the new gatewayUrl padded with whitespace; the service must trim it before storing.
        ConfigureRequest request = new ConfigureRequest(
                "  " + newGatewayUrl + "  ",
                base.sessionToken(),
                base.sharedSecret(),
                base.domainId(),
                base.solutionId(),
                base.queueId());

        service.configure(request);

        assertThat(config.getGatewayUrl()).isEqualTo(newGatewayUrl);
    }

    /**
     * Valid configure requests that omit {@code gatewayUrl} (null). All required fields are non-blank
     * alphanumeric values; {@code queueId} is randomly present or absent (it does not affect retention).
     */
    @Provide
    Arbitrary<ConfigureRequest> validRequestsWithoutGatewayUrl() {
        Arbitrary<String> nonBlank = Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(24);
        Arbitrary<String> optionalQueueId = Arbitraries.oneOf(
                Arbitraries.just((String) null),
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(24));

        return Combinators.combine(nonBlank, nonBlank, nonBlank, nonBlank, optionalQueueId)
                .as((sessionToken, sharedSecret, domainId, solutionId, queueId) ->
                        new ConfigureRequest(null, sessionToken, sharedSecret, domainId, solutionId, queueId));
    }

    /**
     * Syntactically valid absolute http/https URLs (scheme + host), the form the service accepts.
     */
    @Provide
    Arbitrary<String> validHttpUrls() {
        Arbitrary<String> scheme = Arbitraries.of("http", "https");
        Arbitrary<String> host = hostLabels();
        Arbitrary<String> tld = Arbitraries.of("com", "org", "net", "io", "test");
        Arbitrary<Integer> port = Arbitraries.integers().between(1, 65535);

        return Combinators.combine(scheme, host, tld, port)
                .as((s, h, t, p) -> s + "://" + h + "." + t + ":" + p);
    }

    private Arbitrary<String> hostLabels() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(12);
    }
}
