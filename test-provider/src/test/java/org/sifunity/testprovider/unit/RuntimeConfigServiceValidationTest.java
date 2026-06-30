// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sifunity.testprovider.config.ProviderConfig;
import org.sifunity.testprovider.dto.ConfigureRequest;
import org.sifunity.testprovider.dto.ProviderConfigView;
import org.sifunity.testprovider.exception.ValidationException;
import org.sifunity.testprovider.service.PollRunnerNotifier;
import org.sifunity.testprovider.service.RuntimeConfigServiceImpl;

/**
 * Unit tests for {@link RuntimeConfigServiceImpl} validation edge cases.
 *
 * <p>Covers per-field missing-field error identification, blank vs whitespace rejection, gatewayUrl
 * syntax validation (invalid rejected, valid http/https accepted), and idempotent reconfiguration with
 * the same valid body.</p>
 *
 * <p><b>Validates: Requirements 1.7, 4.6</b></p>
 */
class RuntimeConfigServiceValidationTest {

    /** Build a fresh service backed by an empty config store with no poll runner notifier. */
    private RuntimeConfigServiceImpl newService() {
        return new RuntimeConfigServiceImpl(new ProviderConfig(), Optional.<PollRunnerNotifier>empty());
    }

    /** A fully valid configure request used as a baseline for negative variations. */
    private ConfigureRequest validRequest() {
        return new ConfigureRequest(
                "http://localhost:8080", "session-token", "shared-secret", "domain-1", "solution-1", "queue-1");
    }

    // ---------------------------------------------------------------------------------------------
    // Per-field missing-field messages
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("missing sessionToken is identified in the validation errors")
    void missingSessionTokenIdentified() {
        ConfigureRequest request = new ConfigureRequest(
                "http://localhost:8080", null, "shared-secret", "domain-1", "solution-1", "queue-1");

        Map<String, String> errors = configureExpectingErrors(request);

        assertThat(errors).containsKey("sessionToken");
        assertThat(errors).doesNotContainKeys("sharedSecret", "domainId", "solutionId");
    }

    @Test
    @DisplayName("missing sharedSecret is identified in the validation errors")
    void missingSharedSecretIdentified() {
        ConfigureRequest request = new ConfigureRequest(
                "http://localhost:8080", "session-token", null, "domain-1", "solution-1", "queue-1");

        Map<String, String> errors = configureExpectingErrors(request);

        assertThat(errors).containsKey("sharedSecret");
        assertThat(errors).doesNotContainKeys("sessionToken", "domainId", "solutionId");
    }

    @Test
    @DisplayName("missing domainId is identified in the validation errors")
    void missingDomainIdIdentified() {
        ConfigureRequest request = new ConfigureRequest(
                "http://localhost:8080", "session-token", "shared-secret", null, "solution-1", "queue-1");

        Map<String, String> errors = configureExpectingErrors(request);

        assertThat(errors).containsKey("domainId");
        assertThat(errors).doesNotContainKeys("sessionToken", "sharedSecret", "solutionId");
    }

    @Test
    @DisplayName("missing solutionId is identified in the validation errors")
    void missingSolutionIdIdentified() {
        ConfigureRequest request = new ConfigureRequest(
                "http://localhost:8080", "session-token", "shared-secret", "domain-1", null, "queue-1");

        Map<String, String> errors = configureExpectingErrors(request);

        assertThat(errors).containsKey("solutionId");
        assertThat(errors).doesNotContainKeys("sessionToken", "sharedSecret", "domainId");
    }

    @Test
    @DisplayName("all required fields missing are each identified")
    void allRequiredFieldsMissingIdentified() {
        ConfigureRequest request = new ConfigureRequest(null, null, null, null, null, null);

        Map<String, String> errors = configureExpectingErrors(request);

        assertThat(errors).containsOnlyKeys("sessionToken", "sharedSecret", "domainId", "solutionId");
    }

    // ---------------------------------------------------------------------------------------------
    // Blank vs whitespace inputs rejected
    // ---------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("blank or whitespace-only sessionToken is rejected")
    void blankOrWhitespaceSessionTokenRejected(String blank) {
        ConfigureRequest request = new ConfigureRequest(
                "http://localhost:8080", blank, "shared-secret", "domain-1", "solution-1", "queue-1");

        Map<String, String> errors = configureExpectingErrors(request);

        assertThat(errors).containsKey("sessionToken");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("blank or whitespace-only required fields are all rejected")
    void blankOrWhitespaceAllRequiredFieldsRejected(String blank) {
        ConfigureRequest request = new ConfigureRequest(
                "http://localhost:8080", blank, blank, blank, blank, "queue-1");

        Map<String, String> errors = configureExpectingErrors(request);

        assertThat(errors).containsOnlyKeys("sessionToken", "sharedSecret", "domainId", "solutionId");
    }

    @Test
    @DisplayName("invalid request leaves the configuration unchanged (no mutation)")
    void invalidRequestLeavesConfigUnchanged() {
        ProviderConfig config = new ProviderConfig();
        RuntimeConfigServiceImpl service =
                new RuntimeConfigServiceImpl(config, Optional.<PollRunnerNotifier>empty());

        var before = config.snapshot();

        assertThatThrownBy(() -> service.configure(
                new ConfigureRequest(null, "  ", "shared-secret", "domain-1", "solution-1", "queue-1")))
                .isInstanceOf(ValidationException.class);

        assertThat(config.snapshot()).isEqualTo(before);
    }

    // ---------------------------------------------------------------------------------------------
    // gatewayUrl validation
    // ---------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"not a url", "ftp://x", "://nohost", "http://", "https://", "justtext"})
    @DisplayName("invalid gatewayUrl is rejected and identified")
    void invalidGatewayUrlRejected(String badUrl) {
        ConfigureRequest request = new ConfigureRequest(
                badUrl, "session-token", "shared-secret", "domain-1", "solution-1", "queue-1");

        Map<String, String> errors = configureExpectingErrors(request);

        assertThat(errors).containsKey("gatewayUrl");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080", "https://broker.example.com", "http://127.0.0.1:9090/path"})
    @DisplayName("valid http/https gatewayUrl is accepted")
    void validGatewayUrlAccepted(String goodUrl) {
        RuntimeConfigServiceImpl service = newService();

        ProviderConfigView view = service.configure(new ConfigureRequest(
                goodUrl, "session-token", "shared-secret", "domain-1", "solution-1", "queue-1"));

        assertThat(view.configured()).isTrue();
        assertThat(view.gatewayUrl()).isEqualTo(goodUrl);
    }

    // ---------------------------------------------------------------------------------------------
    // Idempotent reconfiguration (Requirement 4.6)
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("configuring twice with the same valid body yields the same configured polling state")
    void idempotentReconfigurationYieldsSameState() {
        RuntimeConfigServiceImpl service = newService();
        ConfigureRequest request = validRequest();

        ProviderConfigView first = service.configure(request);
        ProviderConfigView second = service.configure(request);

        assertThat(second).isEqualTo(first);
        assertThat(second.delayedPollingEnabled()).isTrue();
        assertThat(second.queueIdConfigured()).isTrue();
        assertThat(second.configured()).isTrue();
    }

    @Test
    @DisplayName("idempotent reconfiguration without queueId keeps polling disabled across calls")
    void idempotentReconfigurationWithoutQueueIdKeepsPollingDisabled() {
        RuntimeConfigServiceImpl service = newService();
        ConfigureRequest request = new ConfigureRequest(
                "http://localhost:8080", "session-token", "shared-secret", "domain-1", "solution-1", null);

        ProviderConfigView first = service.configure(request);
        ProviderConfigView second = service.configure(request);

        assertThat(second).isEqualTo(first);
        assertThat(second.delayedPollingEnabled()).isFalse();
        assertThat(second.queueIdConfigured()).isFalse();
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** Invoke configure expecting a {@link ValidationException} and return its per-field errors. */
    private Map<String, String> configureExpectingErrors(ConfigureRequest request) {
        RuntimeConfigServiceImpl service = newService();
        ValidationException ex = catchThrowableOfType(
                () -> service.configure(request), ValidationException.class);
        assertThat(ex).as("expected a ValidationException").isNotNull();
        return ex.getErrors();
    }
}
