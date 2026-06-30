// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.sifunity.testprovider.config.ConfigSnapshot;
import org.sifunity.testprovider.config.ProviderConfig;
import org.sifunity.testprovider.dto.ConfigureRequest;
import org.sifunity.testprovider.exception.ValidationException;
import org.sifunity.testprovider.service.RuntimeConfigServiceImpl;

/**
 * Property 4: Configure atomicity.
 *
 * <p><b>Validates: Requirements 1.7</b>
 *
 * <p>When {@code configure(R)} throws a {@link ValidationException}, the live {@link ProviderConfig} is
 * left exactly as it was: the snapshot taken after the failed call equals the snapshot taken before it.
 * Validation runs (and fails) before any mutation, so no field — including the raw secrets and port — is
 * touched when a request is rejected.
 *
 * <pre>∀ C, R: invalid(R) ⟹ snapshot_after = snapshot_before</pre>
 *
 * <p>Distinct from Property 1 (required-field validation), this property focuses on snapshot equality for
 * <em>all</em> invalid causes — both missing required fields and a syntactically invalid {@code gatewayUrl}
 * — using {@link ConfigSnapshot}'s record-based {@code equals}.
 */
class ConfigureAtomicityPropertyTest {

    /**
     * For any pre-seeded provider state and any invalid configure request, the call throws a
     * {@link ValidationException} and the configuration snapshot is unchanged.
     */
    @Property(tries = 20)
    void invalidConfigureLeavesSnapshotUnchanged(
            @ForAll("seedStates") String[] seed,
            @ForAll("invalidRequests") ConfigureRequest request) {

        ProviderConfig config = new ProviderConfig();
        config.setGatewayUrl(seed[0]);
        config.setSessionToken(seed[1]);
        config.setSharedSecret(seed[2]);
        config.setDomainId(seed[3]);
        config.setSolutionId(seed[4]);
        config.setQueueId(seed[5]);

        RuntimeConfigServiceImpl service = new RuntimeConfigServiceImpl(config, Optional.empty());

        ConfigSnapshot before = config.snapshot();

        assertThatThrownBy(() -> service.configure(request)).isInstanceOf(ValidationException.class);

        ConfigSnapshot after = config.snapshot();
        // ConfigSnapshot is a record: equals compares every field (incl. raw secrets and port).
        assertThat(after).isEqualTo(before);
    }

    /**
     * Arbitrary pre-seed state for the live config: six fields (gatewayUrl, sessionToken, sharedSecret,
     * domainId, solutionId, queueId), each possibly present, null, or blank. The atomicity guarantee must
     * hold regardless of the starting state.
     */
    @Provide
    Arbitrary<String[]> seedStates() {
        Arbitrary<String> field = maybeBlank();
        return Combinators.combine(field, field, field, field, field, field)
                .as((gw, st, ss, dom, sol, q) -> new String[] {gw, st, ss, dom, sol, q});
    }

    /**
     * Invalid configure requests, covering both invalidity causes:
     * <ul>
     *   <li>at least one required field ({@code sessionToken}, {@code sharedSecret}, {@code domainId},
     *       {@code solutionId}) missing/blank, or</li>
     *   <li>all required fields present but {@code gatewayUrl} syntactically invalid.</li>
     * </ul>
     */
    @Provide
    Arbitrary<ConfigureRequest> invalidRequests() {
        return Arbitraries.oneOf(missingRequiredFieldRequests(), invalidGatewayUrlRequests());
    }

    /** Requests guaranteed to be missing at least one required field. */
    private Arbitrary<ConfigureRequest> missingRequiredFieldRequests() {
        Arbitrary<String> field = maybeBlank();
        return Combinators.combine(
                        Arbitraries.integers().between(0, 3), // index of a field forced blank
                        field,
                        field,
                        field,
                        field,
                        Arbitraries.oneOf(present(), Arbitraries.just(null), Arbitraries.just("")))
                .as((forcedIdx, a, b, c, d, queueId) -> {
                    String[] required = {a, b, c, d};
                    required[forcedIdx] = ""; // guarantee at least one required field is blank
                    // gatewayUrl null so the only invalidity cause is the missing required field
                    return new ConfigureRequest(null, required[0], required[1], required[2], required[3], queueId);
                });
    }

    /** Requests with all required fields present but a syntactically invalid {@code gatewayUrl}. */
    private Arbitrary<ConfigureRequest> invalidGatewayUrlRequests() {
        Arbitrary<String> badUrl = Arbitraries.oneOf(
                Arbitraries.just("not a url"),
                Arbitraries.just("ftp://example.com"),
                Arbitraries.just("http://"),
                Arbitraries.just("https://"),
                Arbitraries.just("://missing-scheme"),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10));
        return Combinators.combine(
                        badUrl,
                        present(),
                        present(),
                        present(),
                        present(),
                        Arbitraries.oneOf(present(), Arbitraries.just(null)))
                .as(ConfigureRequest::new);
    }

    /** A non-blank value. */
    private Arbitrary<String> present() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
    }

    /** A value that may be present, null, blank, or whitespace-only. */
    private Arbitrary<String> maybeBlank() {
        return Arbitraries.oneOf(
                present(),
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.just("   "));
    }
}
