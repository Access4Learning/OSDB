// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.TokenValidationRequest;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.sifunity.gateway.filter.AuthenticationFilter;
import org.sifunity.gateway.metrics.GatewayMetricsService;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug-condition exploration test for credential logging redaction (Property 1), gateway module.
 *
 * <p><b>This test is EXPECTED TO FAIL on the UNFIXED code.</b> Its failure documents the
 * counterexamples that prove per-request session tokens leak in recoverable form at DEBUG
 * level. It will pass once the fix masks the values via {@code CredentialMasker}.</p>
 *
 * <p>Bug condition (from design):
 * {@code isBugCondition(X) = X.emits_output AND X.output_contains_credential_value
 * AND X.credential_value_is_recoverable} — a log statement emits a credential in full
 * recoverable form at any level.</p>
 *
 * <p>Covers the two {@code gateway} defect locations (the {@code environment-service}
 * BootstrapComponent location and the shell scripts are covered by the same-named class in the
 * environment-service module):</p>
 * <ul>
 *   <li>{@code AuthenticationFilter} line 159 (DEBUG): Basic-Auth to HMAC conversion token</li>
 *   <li>{@code EnvironmentServiceClient.validateToken} line 133 (DEBUG): validation token</li>
 * </ul>
 *
 * <b>Validates: Requirements 1.3, 1.4, 2.3, 2.4</b>
 */
class CredentialLoggingRedactionBugConditionExplorationTest {

    private static final String AUTH_FILTER_LABEL = "converted Basic Auth for sessionToken=";
    private static final String ENV_CLIENT_LABEL = "Validating token for sessionToken: ";
    private static final String FIXED_SHARED_SECRET = "test-shared-secret-value";

    /**
     * Credential value shapes: empty, short (<=4), long alphanumeric, UUID-shaped, base64-shaped.
     * None contain ':' so the Basic-Auth {@code token:secret} split is unambiguous.
     */
    @Provide
    Arbitrary<String> sessionTokens() {
        Arbitrary<String> empty = Arbitraries.just("");
        Arbitrary<String> shortValue = Arbitraries.strings()
                .withCharRange('a', 'z').ofMinLength(1).ofMaxLength(4);
        Arbitrary<String> longValue = Arbitraries.strings()
                .alpha().numeric().ofMinLength(12).ofMaxLength(40);
        Arbitrary<String> uuidShaped = Arbitraries.randomValue(r -> UUID.randomUUID().toString());
        Arbitrary<String> base64Shaped = Arbitraries.strings()
                .withCharRange('A', 'Z').withCharRange('a', 'z').withCharRange('0', '9')
                .ofMinLength(20).ofMaxLength(44)
                .map(s -> s + "==");
        return Arbitraries.oneOf(empty, shortValue, longValue, uuidShaped, base64Shaped);
    }

    // --- Property 1: AuthenticationFilter Basic-Auth conversion token must be masked ---

    @Property(tries = 5)
    void authenticationFilterSessionTokenIsNotEmittedInRecoverableForm(
            @ForAll("sessionTokens") String sessionToken) {
        // Empty tokens never reach the conversion log (colon index guard) — not a recoverable leak.
        Assume.that(sessionToken != null && !sessionToken.isBlank());

        List<String> messages = runAuthFilterConversionAndCaptureLogs(sessionToken);

        boolean leaked = valueAppearsAfterLabel(messages, AUTH_FILTER_LABEL, sessionToken);
        assertThat(leaked)
                .as("AuthenticationFilter must NOT log the raw sessionToken during Basic-Auth "
                        + "conversion. Captured messages: %s", messages)
                .isFalse();
    }

    // --- Property 1: EnvironmentServiceClient validation token must be masked ---

    @Property(tries = 5)
    void environmentServiceClientSessionTokenIsNotEmittedInRecoverableForm(
            @ForAll("sessionTokens") String sessionToken) {
        Assume.that(sessionToken != null && !sessionToken.isBlank());

        List<String> messages = runValidateTokenAndCaptureLogs(sessionToken);

        boolean leaked = valueAppearsAfterLabel(messages, ENV_CLIENT_LABEL, sessionToken);
        assertThat(leaked)
                .as("EnvironmentServiceClient must NOT log the raw sessionToken during validation. "
                        + "Captured messages: %s", messages)
                .isFalse();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Returns true if any captured message contains {@code label} immediately followed by the
     * raw credential value. Only the portion after the label is inspected, so unrelated message
     * text cannot cause a false positive (robust for short values too).
     */
    private boolean valueAppearsAfterLabel(List<String> messages, String label, String value) {
        for (String message : messages) {
            int idx = message.indexOf(label);
            if (idx >= 0) {
                String suffix = message.substring(idx + label.length());
                if (suffix.contains(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> runAuthFilterConversionAndCaptureLogs(String sessionToken) {
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        when(mockClient.validateToken(any(TokenValidationRequest.class))).thenReturn(Mono.empty());
        GatewayMetricsService mockMetrics = mock(GatewayMetricsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        AuthenticationFilter filter = new AuthenticationFilter(mockClient, objectMapper, mockMetrics);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        String credentials = sessionToken + ":" + FIXED_SHARED_SECRET;
        String basicHeader = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/data/StudentPersonal")
                .header(SifHeaders.AUTHORIZATION, basicHeader)
                .header("Referer", "http://localhost:8080/swagger-ui/index.html")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Logger logger = (Logger) LoggerFactory.getLogger(AuthenticationFilter.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            // The Basic-Auth conversion (including the line-159 log) runs eagerly in filter().
            filter.filter(exchange, mockChain);
            return copyMessages(appender);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }

    private List<String> runValidateTokenAndCaptureLogs(String sessionToken) {
        EnvironmentServiceClient client = new EnvironmentServiceClient(WebClient.builder(), "http://localhost:9", "test-internal-token", null, null, io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("test"));
        TokenValidationRequest request =
                new TokenValidationRequest(sessionToken, "1705318200000", "test-hash");

        Logger logger = (Logger) LoggerFactory.getLogger(EnvironmentServiceClient.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            // The line-133 log runs eagerly when validateToken() is invoked (before subscription).
            client.validateToken(request);
            return copyMessages(appender);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }

    private List<String> copyMessages(ListAppender<ILoggingEvent> appender) {
        List<String> messages = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            messages.add(event.getFormattedMessage());
        }
        return messages;
    }
}
