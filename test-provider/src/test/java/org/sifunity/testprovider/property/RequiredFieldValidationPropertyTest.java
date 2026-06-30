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
import org.sifunity.testprovider.service.PollRunnerNotifier;
import org.sifunity.testprovider.service.RuntimeConfigServiceImpl;

/**
 * Property 1: Required-field validation.
 *
 * <p><b>Validates: Requirements 1.2, 1.7</b>
 *
 * <p>For any configure request {@code R} missing at least one of {@code sessionToken},
 * {@code sharedSecret}, {@code domainId}, {@code solutionId}, {@code configure(R)} throws a
 * {@link ValidationException} and leaves the configuration unchanged.
 *
 * <pre>∀ R: missingRequired(R) ⟹ throws ∧ snapshot_after = snapshot_before</pre>
 */
class RequiredFieldValidationPropertyTest {

    /**
     * A request missing at least one required field is rejected with a {@link ValidationException},
     * and the live configuration snapshot is identical before and after the call (no mutation).
     */
    @Property(tries = 20)
    void missingRequiredFieldThrowsAndLeavesConfigUnchanged(
            @ForAll("requestsMissingRequiredField") ConfigureRequest request) {
        // Fresh config store + service with no poll runner notifier, per the task setup.
        ProviderConfig config = new ProviderConfig();
        RuntimeConfigServiceImpl service =
                new RuntimeConfigServiceImpl(config, Optional.<PollRunnerNotifier>empty());

        ConfigSnapshot before = config.snapshot();

        assertThatThrownBy(() -> service.configure(request))
                .isInstanceOf(ValidationException.class);

        ConfigSnapshot after = config.snapshot();
        assertThat(after).isEqualTo(before);
    }

    /**
     * Generate {@link ConfigureRequest}s that are guaranteed to be missing at least one required field.
     * Each required field is independently either present (a non-blank value) or blank (null/empty/
     * whitespace); the combination is filtered so at least one required field is blank. {@code gatewayUrl}
     * is left null so that only required-field validation can fail, and {@code queueId} is optional.
     */
    @Provide
    Arbitrary<ConfigureRequest> requestsMissingRequiredField() {
        Arbitrary<String> requiredField = Arbitraries.oneOf(blank(), validValue());
        Arbitrary<String> optionalQueueId = Arbitraries.oneOf(blank(), validValue());

        return Combinators.combine(requiredField, requiredField, requiredField, requiredField, optionalQueueId)
                .as((sessionToken, sharedSecret, domainId, solutionId, queueId) ->
                        new ConfigureRequest(null, sessionToken, sharedSecret, domainId, solutionId, queueId))
                .filter(RequiredFieldValidationPropertyTest::missingRequiredField);
    }

    /** True when at least one required field is blank (null/empty/whitespace). */
    private static boolean missingRequiredField(ConfigureRequest r) {
        return isBlank(r.sessionToken())
                || isBlank(r.sharedSecret())
                || isBlank(r.domainId())
                || isBlank(r.solutionId());
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Blank candidates: null, empty, and whitespace-only strings. */
    private static Arbitrary<String> blank() {
        return Arbitraries.of(null, "", " ", "   ", "\t");
    }

    /** Non-blank candidate values. */
    private static Arbitrary<String> validValue() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20)
                .filter(s -> !s.trim().isEmpty());
    }
}
