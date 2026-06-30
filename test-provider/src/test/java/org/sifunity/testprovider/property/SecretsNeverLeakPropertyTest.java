// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.sifunity.testprovider.config.ProviderConfig;
import org.sifunity.testprovider.dto.ConfigureRequest;
import org.sifunity.testprovider.dto.ProviderConfigView;
import org.sifunity.testprovider.service.RuntimeConfigServiceImpl;

/**
 * Property 3: Secrets never leak.
 *
 * <p><b>Validates: Requirements 3.3, 3.4</b>
 *
 * <p>For all reachable states, neither the {@code configure} response view nor the
 * {@code getMaskedConfig} status view contains the raw {@code sessionToken} or {@code sharedSecret}
 * substring (when length ≥ 8). Secrets are always rendered through {@link
 * org.sifunity.testprovider.util.SecretMasker#mask}, which collapses the middle of the value into
 * {@code ****}, so the contiguous raw secret can never appear in the serialized JSON.
 *
 * <pre>∀ state: rawSecret ∉ serialize(view(state))</pre>
 */
class SecretsNeverLeakPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * For any pair of random secrets (length ≥ 8) supplied via a valid configure call, the raw
     * {@code sessionToken} and {@code sharedSecret} never appear in the JSON serialization of either
     * the configure response view or the masked status view.
     */
    @Property(tries = 20)
    void rawSecretsNeverAppearInSerializedViews(
            @ForAll @AlphaChars @StringLength(min = 8, max = 64) String sessionToken,
            @ForAll @AlphaChars @StringLength(min = 8, max = 64) String sharedSecret)
            throws Exception {

        // Fresh service over a fresh live config so each example is independent.
        ProviderConfig config = new ProviderConfig();
        RuntimeConfigServiceImpl service =
                new RuntimeConfigServiceImpl(config, Optional.empty());

        ConfigureRequest request =
                new ConfigureRequest(
                        "http://localhost:8080",
                        sessionToken,
                        sharedSecret,
                        "domain-1",
                        "solution-1",
                        "queue-1");

        // configure() returns the masked view; getMaskedConfig() reads it back independently.
        ProviderConfigView configureView = service.configure(request);
        ProviderConfigView statusView = service.getMaskedConfig();

        String configureJson = objectMapper.writeValueAsString(configureView);
        String statusJson = objectMapper.writeValueAsString(statusView);

        assertThat(configureJson).doesNotContain(sessionToken);
        assertThat(configureJson).doesNotContain(sharedSecret);
        assertThat(statusJson).doesNotContain(sessionToken);
        assertThat(statusJson).doesNotContain(sharedSecret);
    }
}
