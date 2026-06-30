// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.sifunity.testprovider.config.ProviderConfig;
import org.sifunity.testprovider.dto.PollResult;
import org.sifunity.testprovider.service.DelayedPollRunner;
import org.sifunity.testprovider.service.TestDataGenerator;
import org.springframework.util.StringUtils;

/**
 * Property 7: Poll gating.
 *
 * <p><b>Validates: Requirements 4.1, 4.2, 4.5</b>
 *
 * <p>{@code triggerOnce()} reports {@code polled = true} exactly when delayed polling is enabled; when
 * disabled it performs <em>no</em> queue interaction and reports
 * {@code { polled: false, messageProcessed: false }}.
 *
 * <pre>∀ state: triggerOnce().polled ⟺ delayedPollingEnabled(state)</pre>
 *
 * <p>This test focuses on the <b>disabled</b> branch, where the property can be verified without any
 * network access. {@link DelayedPollRunner#triggerOnce()} checks
 * {@link ProviderConfig#hasCompleteCredentials()} and a non-blank {@code queueId} <em>before</em> any
 * {@code RestClient} call, returning {@code { polled: false, messageProcessed: false }} for any incomplete
 * state. Each generated state is forced to have at least one blank required field (a credential or the
 * {@code queueId}), so delayed polling is always disabled. The assertion {@code polled == false} confirms
 * the gating short-circuited before any queue interaction — the "no queue interaction when disabled"
 * guarantee is structural (the method returns before the {@code RestClient} is ever used).
 *
 * <p>The enabled branch is exercised by the {@code @WebMvcTest}/integration tests with a stubbed broker
 * (see task 7.2); driving a real enabled poll here would hit the network and introduce flakiness.
 */
class PollGatingPropertyTest {

    /**
     * A field value that is either blank (empty or whitespace-only) or a clearly non-blank token.
     * Exercises both the "present" and "missing/blank" sides of every field.
     */
    @Provide
    Arbitrary<String> fieldValue() {
        Arbitrary<String> blanks = Arbitraries.of("", " ", "   ", "\t");
        Arbitrary<String> present = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(12);
        return Arbitraries.oneOf(blanks, present);
    }

    /**
     * For any configured state in which at least one required field (one of the four credentials or the
     * {@code queueId}) is blank, delayed polling is disabled and {@code triggerOnce()} returns
     * {@code { polled: false, messageProcessed: false }} without touching the queue.
     */
    @Property(tries = 20)
    void disabledStateNeverPolls(
            @ForAll("fieldValue") String sessionToken,
            @ForAll("fieldValue") String sharedSecret,
            @ForAll("fieldValue") String domainId,
            @ForAll("fieldValue") String solutionId,
            @ForAll("fieldValue") String queueId,
            @ForAll @IntRange(min = 0, max = 4) int blankIndex) {

        // Force one of the five required fields blank so the state is guaranteed to be polling-disabled.
        String[] values = {sessionToken, sharedSecret, domainId, solutionId, queueId};
        values[blankIndex] = "";

        ProviderConfig config = new ProviderConfig();
        config.setSessionToken(values[0]);
        config.setSharedSecret(values[1]);
        config.setDomainId(values[2]);
        config.setSolutionId(values[3]);
        config.setQueueId(values[4]);

        boolean completeCredentials = StringUtils.hasText(values[0])
                && StringUtils.hasText(values[1])
                && StringUtils.hasText(values[2])
                && StringUtils.hasText(values[3]);
        boolean delayedPollingEnabled = completeCredentials && StringUtils.hasText(values[4]);

        // Sanity: forcing a field blank guarantees the disabled state under test.
        assertThat(delayedPollingEnabled).isFalse();

        DelayedPollRunner runner = new DelayedPollRunner(config, new TestDataGenerator(), new ObjectMapper());

        // If gating did not short-circuit, this would attempt a real network poll; polled == false proves
        // the disabled path was taken and no queue interaction occurred.
        PollResult result = runner.triggerOnce();

        assertThat(result.polled()).isEqualTo(delayedPollingEnabled);
        assertThat(result.polled()).isFalse();
        assertThat(result.messageProcessed()).isFalse();
    }
}
