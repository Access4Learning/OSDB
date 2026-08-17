// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.sifunity.broker.common.config.HeaderBlacklistProperties;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.broker.common.util.NoOpTracingUtils;
import org.sifunity.requestsconnector.client.ProviderHttpClient;
import org.sifunity.requestsconnector.dto.ImmediateRequestDTO;
import org.sifunity.requestsconnector.service.ImmediateRequestHandler;
import org.sifunity.requestsconnector.util.MessageBuilder;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Property 10: Immediate flow forwards passthrough headers to provider.
 *
 * <p>For any set of non-blacklisted passthrough headers in a request,
 * the {@link ImmediateRequestHandler} / {@link ProviderHttpClient} should include
 * all of those headers as HTTP headers on the outbound request to the provider endpoint.</p>
 *
 * <p><strong>Validates: Requirements 7.1</strong></p>
 */
@Tag("Feature: header-passthrough-and-sanitization, Property 10: Immediate flow forwards passthrough headers to provider")
class ImmediateFlowHeaderForwardingPropertyTest {

    /**
     * Default blacklisted header names (case-insensitive).
     */
    private static final Set<String> BLACKLIST_NAMES;

    /**
     * Structured SIF header names excluded by the sanitizer.
     */
    private static final Set<String> STRUCTURED_SIF_HEADERS;

    static {
        TreeSet<String> bl = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        bl.addAll(HeaderBlacklistProperties.DEFAULTS);
        BLACKLIST_NAMES = Collections.unmodifiableSet(bl);

        TreeSet<String> sif = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        sif.addAll(List.of(
                "messageid", "messagetype", "servicename", "servicetype",
                "zoneid", "contextid", "requestid", "queueid", "sourceid"
        ));
        STRUCTURED_SIF_HEADERS = Collections.unmodifiableSet(sif);
    }

    // ========== Arbitraries ==========

    /**
     * Generates alpha header keys that do NOT collide with any blacklist entry
     * or structured SIF header name.
     */
    @Provide
    Arbitrary<String> safeHeaderKeys() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(30)
                .filter(k -> !BLACKLIST_NAMES.contains(k) && !STRUCTURED_SIF_HEADERS.contains(k));
    }

    /**
     * Generates arbitrary non-blacklisted header maps.
     */
    @Provide
    Arbitrary<Map<String, String>> passthroughHeaderMaps() {
        Arbitrary<String> values = Arbitraries.strings()
                .ascii()
                .ofMinLength(1)
                .ofMaxLength(50);
        return Arbitraries.maps(safeHeaderKeys(), values)
                .ofMinSize(1)
                .ofMaxSize(15);
    }

    // ========== Property ==========

    /**
     * For any set of non-blacklisted passthrough headers, the
     * {@link ImmediateRequestHandler#handleRequest} method should forward all
     * passthrough headers to the {@link ProviderHttpClient#sendRequest} call.
     */
    @Property(tries = 10)
    @Tag("Feature: header-passthrough-and-sanitization, Property 10: Immediate flow forwards passthrough headers to provider")
    @SuppressWarnings("unchecked")
    void allPassthroughHeadersAreForwardedToProvider(
            @ForAll("passthroughHeaderMaps") Map<String, String> passthroughHeaders) {

        // --- Arrange ---

        // Mock ProviderHttpClient
        ProviderHttpClient providerClient = mock(ProviderHttpClient.class);

        // Stub sendRequest to return a minimal response envelope
        SifMessageEnvelope<?> responseEnvelope = SifMessageEnvelope.builder()
                .messageType(MessageType.RESPONSE)
                .serviceName("TestService")
                .serviceType(ServiceType.OBJECT)
                .headers(Collections.emptyMap())
                .build();
        when(providerClient.sendRequest(anyString(), any(SifMessageEnvelope.class), any(Map.class)))
                .thenReturn(responseEnvelope);

        // Real MessageBuilder and HeaderSanitizer (with default blacklist)
        HeaderBlacklistProperties props = new HeaderBlacklistProperties();
        HeaderSanitizer headerSanitizer = new HeaderSanitizer(props);
        MessageBuilder messageBuilder = new MessageBuilder();

        ImmediateRequestHandler handler = new ImmediateRequestHandler(
                providerClient, messageBuilder, headerSanitizer, new NoOpTracingUtils(), java.util.List.of());

        // Build a minimal ImmediateRequestDTO
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("TestService");
        request.setServiceType(ServiceType.OBJECT.name());
        request.setMessageType(MessageType.REQUEST.name());
        request.setPayload("test-payload");

        // Provider registration
        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "TestService",
                "defaultZone",
                "defaultContext",
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://localhost:9999/provider"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))))),
                Instant.now()
        );

        // --- Act ---
        handler.handleRequest(
                UUID.randomUUID(),
                "test-env",
                "test-domain",
                "test-solution",
                provider,
                request,
                passthroughHeaders);

        // --- Assert ---

        // Capture the passthrough headers argument passed to sendRequest
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(providerClient).sendRequest(
                eq(ProtocolDTO.locationOf(provider.endPoint())),
                any(SifMessageEnvelope.class),
                headersCaptor.capture());

        Map<String, String> capturedHeaders = headersCaptor.getValue();

        // Every passthrough header should be present in the captured headers
        for (Map.Entry<String, String> entry : passthroughHeaders.entrySet()) {
            assertThat(capturedHeaders)
                    .as("Passthrough header '%s' should be forwarded to provider", entry.getKey())
                    .containsEntry(entry.getKey(), entry.getValue());
        }
    }
}
