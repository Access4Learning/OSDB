// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.property;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.sifunity.testprovider.config.ProviderConfig;
import org.sifunity.testprovider.dto.ConfigureRequest;
import org.sifunity.testprovider.dto.ProviderConfigView;
import org.sifunity.testprovider.service.RuntimeConfigServiceImpl;
import org.springframework.util.StringUtils;

/**
 * Property 6: Polling enabled iff complete config.
 *
 * <p><b>Validates: Requirements 4.1, 4.2, 4.5, 6.3</b>
 *
 * <p>{@code delayedPollingEnabled} in any view is {@code true} exactly when a complete credential set
 * is present and the live {@code queueId} is non-blank.
 *
 * <pre>∀ state: view.delayedPollingEnabled ⟺ (completeCredentials ∧ queueId ≠ null ∧ queueId ≠ "")</pre>
 *
 * <p>Combinations with a complete credential set pass validation and are applied through
 * {@link RuntimeConfigServiceImpl#configure}. Combinations missing a required credential cannot go through
 * {@code configure} (it would reject them), so the {@link ProviderConfig} fields are set directly and the
 * flag is read back via {@link RuntimeConfigServiceImpl#getMaskedConfig}. Either way, the derived
 * {@code delayedPollingEnabled} flag must match {@code completeCredentials ∧ queueId non-blank}.
 */
class PollingEnabledFlagPropertyTest {

    /**
     * A credential value that is either blank (empty or whitespace-only) or a clearly non-blank token.
     * This exercises both the "present" and "missing/blank" sides of every field.
     */
    @Provide
    Arbitrary<String> credValue() {
        Arbitrary<String> blanks = Arbitraries.of("", " ", "   ", "\t");
        Arbitrary<String> present = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(12);
        return Arbitraries.oneOf(blanks, present);
    }

    /**
     * For any combination of credential/queueId values, the view's {@code delayedPollingEnabled} flag is
     * true exactly when all four credentials are non-blank and {@code queueId} is non-blank.
     */
    @Property(tries = 20)
    void delayedPollingEnabledIffCompleteConfig(
            @ForAll("credValue") String sessionToken,
            @ForAll("credValue") String sharedSecret,
            @ForAll("credValue") String domainId,
            @ForAll("credValue") String solutionId,
            @ForAll("credValue") String queueId) {

        ProviderConfig config = new ProviderConfig();
        RuntimeConfigServiceImpl service = new RuntimeConfigServiceImpl(config, Optional.empty());

        boolean completeCredentials = StringUtils.hasText(sessionToken)
                && StringUtils.hasText(sharedSecret)
                && StringUtils.hasText(domainId)
                && StringUtils.hasText(solutionId);
        boolean expected = completeCredentials && StringUtils.hasText(queueId);

        boolean actual;
        if (completeCredentials) {
            // Valid credential set: route through configure() (gatewayUrl omitted, retained as default).
            ConfigureRequest request =
                    new ConfigureRequest(null, sessionToken, sharedSecret, domainId, solutionId, queueId);
            ProviderConfigView view = service.configure(request);
            actual = view.delayedPollingEnabled();
        } else {
            // Missing a required credential: configure() would reject it, so set fields directly and read back.
            config.setSessionToken(sessionToken);
            config.setSharedSecret(sharedSecret);
            config.setDomainId(domainId);
            config.setSolutionId(solutionId);
            config.setQueueId(queueId);
            ProviderConfigView view = service.getMaskedConfig();
            actual = view.delayedPollingEnabled();
        }

        assertThat(actual).isEqualTo(expected);
    }
}
