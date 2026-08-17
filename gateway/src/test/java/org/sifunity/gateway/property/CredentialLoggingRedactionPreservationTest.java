// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.ArgumentCaptor;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.TokenValidationRequest;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Preservation property tests for credential logging redaction (Property 2), gateway module.
 *
 * <p><b>Observation-first methodology.</b> These tests capture the CURRENT (unfixed) behavior of
 * the non-credential-emitting code paths so that the same assertions continue to hold after the
 * fix. They are EXPECTED TO PASS on the unfixed code, establishing the baseline that the fix must
 * preserve. The fix only masks credential values in log statements; it must not change the
 * authentication outcome (Basic-Auth to HMAC conversion) nor any non-credential diagnostic log.</p>
 *
 * <p>Non-bug condition (from design): {@code isBugCondition(X)} returns false — statements that
 * emit no recoverable credential value, plus all functional authentication logic.</p>
 *
 * <p>Covers the two {@code gateway} preservation concerns:</p>
 * <ul>
 *   <li>Authentication outcome: Basic-Auth to SIF HMAC conversion produces an unchanged
 *       {@code sessionToken} and HMAC hash for token validation (accept/reject determinant).</li>
 *   <li>Non-sensitive logging: a credential-free diagnostic message (exempt-path log) is emitted
 *       verbatim.</li>
 * </ul>
 *
 * <p>Uses {@code @Property(tries = 5)} to keep the run fast per the speed requirement for these
 * preservation properties.</p>
 *
 * <b>Validates: Requirements 3.3, 3.4</b>
 */
class CredentialLoggingRedactionPreservationTest {

    private static final String AUTH_FILTER_LABEL = "converted Basic Auth for sessionToken=";

    /**
     * Credential-shaped values that contain no ':' (so the Basic-Auth {@code token:secret} split
     * is unambiguous) and are non-blank (blank tokens never reach the conversion path).
     */
    @Provide
    Arbitrary<String> tokenParts() {
        return Arbitraries.strings()
                .alpha().numeric().ofMinLength(1).ofMaxLength(40);
    }

    /** Path suffixes for building exempt actuator paths; alphanumeric, no whitespace. */
    @Provide
    Arbitrary<String> pathSuffixes() {
        return Arbitraries.strings()
                .withCharRange('a', 'z').withCharRange('A', 'Z').withCharRange('0', '9')
                .ofMinLength(1).ofMaxLength(20);
    }

    // --- Property 2: Authentication conversion outcome is preserved ---

    @Property(tries = 5)
    void basicAuthToHmacConversionOutcomeIsUnchanged(
            @ForAll("tokenParts") String sessionToken,
            @ForAll("tokenParts") String sharedSecret) {

        TokenValidationRequest captured = runAuthFilterConversionAndCaptureValidationRequest(
                sessionToken, sharedSecret);

        // The conversion must forward the same sessionToken unchanged.
        assertThat(captured.sessionToken())
                .as("Basic-Auth to HMAC conversion must forward the sessionToken unchanged")
                .isEqualTo(sessionToken);

        // The HMAC hash must be the deterministic HMAC of (sessionToken, timestamp, sharedSecret).
        // This is the accept/reject determinant that downstream token validation relies on.
        String expectedHmac = SifAuthenticationUtils.computeHmac(
                sessionToken, captured.timestamp(), sharedSecret);
        assertThat(captured.hash())
                .as("Basic-Auth to HMAC conversion must compute the correct HMAC hash")
                .isEqualTo(expectedHmac);
    }

    // --- Property 2: Non-credential diagnostic log is emitted verbatim ---

    @Property(tries = 5)
    void exemptPathDiagnosticLogIsEmittedVerbatim(@ForAll("pathSuffixes") String suffix) {
        String path = "/actuator/" + suffix;
        String expectedMessage = "Path " + path + " is exempt from authentication";

        List<String> messages = runFilterForExemptPathAndCaptureLogs(path);

        assertThat(messages)
                .as("The credential-free exempt-path diagnostic log must be emitted verbatim. "
                        + "Captured messages: %s", messages)
                .contains(expectedMessage);
        // Sanity: this non-credential path emits no Basic-Auth conversion message.
        assertThat(messages).noneMatch(m -> m.contains(AUTH_FILTER_LABEL));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private TokenValidationRequest runAuthFilterConversionAndCaptureValidationRequest(
            String sessionToken, String sharedSecret) {
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        when(mockClient.validateToken(any(TokenValidationRequest.class))).thenReturn(Mono.empty());
        GatewayMetricsService mockMetrics = mock(GatewayMetricsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        AuthenticationFilter filter = new AuthenticationFilter(mockClient, objectMapper, mockMetrics);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        String credentials = sessionToken + ":" + sharedSecret;
        String basicHeader = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/data/StudentPersonal")
                .header(SifHeaders.AUTHORIZATION, basicHeader)
                .header("Referer", "http://localhost:8080/swagger-ui/index.html")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // The conversion + validateToken call run eagerly in filter().
        filter.filter(exchange, mockChain);

        ArgumentCaptor<TokenValidationRequest> captor =
                ArgumentCaptor.forClass(TokenValidationRequest.class);
        verify(mockClient).validateToken(captor.capture());
        return captor.getValue();
    }

    private List<String> runFilterForExemptPathAndCaptureLogs(String path) {
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayMetricsService mockMetrics = mock(GatewayMetricsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        AuthenticationFilter filter = new AuthenticationFilter(mockClient, objectMapper, mockMetrics);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Logger logger = (Logger) LoggerFactory.getLogger(AuthenticationFilter.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            filter.filter(exchange, mockChain);
            List<String> messages = new ArrayList<>();
            for (ILoggingEvent event : appender.list) {
                messages.add(event.getFormattedMessage());
            }
            return messages;
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }
}
