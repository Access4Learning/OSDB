// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ServiceTypeValidationFilter.
 *
 * <p>Requirements: 10.1, 10.2, 10.3, 10.4, 5.1, 5.2, 5.3, 5.4, 5.5
 */
@DisplayName("ServiceTypeValidationFilter Tests")
class ServiceTypeValidationFilterTest {

    private ServiceTypeValidationFilter filter;
    private GatewayFilterChain mockChain;
    private ObjectMapper objectMapper;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger filterLogger;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        filter = new ServiceTypeValidationFilter(objectMapper);
        mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class)))
                .thenReturn(Mono.empty());

        // Attach log appender to capture WARN logs
        filterLogger = (Logger) LoggerFactory.getLogger(ServiceTypeValidationFilter.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        filterLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(logAppender);
    }

    // ========== Filter Ordering ==========

    @Test
    @DisplayName("filter should have order 3")
    void filter_HasOrder3() {
        assertThat(filter.getOrder()).isEqualTo(3);
    }

    // ========== Exempt Paths ==========

    @Test
    @DisplayName("actuator paths skip validation")
    void filter_SkipsValidation_ForActuatorPaths() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/actuator/health")
                .header(SifHeaders.SERVICE_TYPE, "BANANA")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("swagger paths skip validation")
    void filter_SkipsValidation_ForSwaggerPaths() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/swagger-ui/index.html")
                .header(SifHeaders.SERVICE_TYPE, "INVALID")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("v3/api-docs paths skip validation")
    void filter_SkipsValidation_ForApiDocsPaths() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/v3/api-docs")
                .header(SifHeaders.SERVICE_TYPE, "INVALID")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("admin-users paths skip validation")
    void filter_SkipsValidation_ForAdminUsersPaths() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/admin-users/login")
                .header(SifHeaders.SERVICE_TYPE, "INVALID")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("admin-proxy paths skip validation")
    void filter_SkipsValidation_ForAdminProxyPaths() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/admin-proxy/something")
                .header(SifHeaders.SERVICE_TYPE, "INVALID")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("domain management paths skip validation")
    void filter_SkipsValidation_ForDomainPaths() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/domains/some-domain")
                .header(SifHeaders.SERVICE_TYPE, "INVALID")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("solution management paths skip validation")
    void filter_SkipsValidation_ForSolutionPaths() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/iowa/solutions/prod")
                .header(SifHeaders.SERVICE_TYPE, "INVALID")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("POST environments (registration) skips validation")
    void filter_SkipsValidation_ForEnvironmentRegistration() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("http://localhost/iowa/prod/environments")
                .header(SifHeaders.SERVICE_TYPE, "INVALID")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    // ========== Valid serviceType + Matching Path ==========

    @Test
    @DisplayName("OBJECT is valid for /requests path")
    void filter_Passes_ObjectOnRequests() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/requests/Students", "OBJECT");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("SERVICEPATH is valid for /requests path")
    void filter_Passes_ServicePathOnRequests() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/requests/Students", "SERVICEPATH");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("XQUERYTEMPLATE is valid for /requests path")
    void filter_Passes_XQueryTemplateOnRequests() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/requests/Students", "XQUERYTEMPLATE");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("FUNCTIONAL is valid for /services path")
    void filter_Passes_FunctionalOnServices() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/services/StudentLocator", "FUNCTIONAL");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("All six SIF types are valid for /events path")
    void filter_Passes_AllTypesOnEvents() {
        String[] allTypes = {"OBJECT", "UTILITY", "FUNCTIONAL", "SERVICEPATH", "XQUERYTEMPLATE", "SERVICE"};
        for (String type : allTypes) {
            reset(mockChain);
            when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

            MockServerWebExchange exchange = buildExchange("/iowa/prod/events", type);
            StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();
            verify(mockChain).filter(exchange);
        }
    }

    @Test
    @DisplayName("UTILITY is valid for /zones path")
    void filter_Passes_UtilityOnZones() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/zones", "UTILITY");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("UTILITY is valid for /providers path")
    void filter_Passes_UtilityOnProviders() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/providers", "UTILITY");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("UTILITY is valid for /xquerys path")
    void filter_Passes_UtilityOnXquerys() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/xquerys", "UTILITY");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("UTILITY is valid for /alerts path")
    void filter_Passes_UtilityOnAlerts() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/alerts", "UTILITY");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("UTILITY is valid for /namespaces path")
    void filter_Passes_UtilityOnNamespaces() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/namespaces", "UTILITY");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("UTILITY is valid for /codesets path")
    void filter_Passes_UtilityOnCodesets() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/codesets", "UTILITY");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    // ========== Invalid serviceType for Path → HTTP 400 ==========

    @Test
    @DisplayName("UTILITY is invalid for /requests → HTTP 400")
    void filter_Rejects_UtilityOnRequests() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/requests/Students", "UTILITY");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mockChain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("FUNCTIONAL is invalid for /requests → HTTP 400")
    void filter_Rejects_FunctionalOnRequests() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/requests/Students", "FUNCTIONAL");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mockChain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("OBJECT is invalid for /services → HTTP 400")
    void filter_Rejects_ObjectOnServices() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/services/StudentLocator", "OBJECT");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mockChain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("OBJECT is invalid for /zones (utility path) → HTTP 400")
    void filter_Rejects_ObjectOnZones() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/zones", "OBJECT");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mockChain, never()).filter(any(ServerWebExchange.class));
    }

    // ========== Absent/Blank serviceType Header ==========

    @Test
    @DisplayName("absent serviceType header passes through for /requests")
    void filter_Passes_WhenNoServiceTypeHeader_Requests() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/iowa/prod/requests/Students")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", UUID.randomUUID());

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("absent serviceType header passes through for /services")
    void filter_Passes_WhenNoServiceTypeHeader_Services() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("http://localhost/iowa/prod/services/StudentLocator")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", UUID.randomUUID());

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("absent serviceType header passes through for /events")
    void filter_Passes_WhenNoServiceTypeHeader_Events() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("http://localhost/iowa/prod/events")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", UUID.randomUUID());

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("absent serviceType header passes through for utility paths")
    void filter_Passes_WhenNoServiceTypeHeader_Utility() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/iowa/prod/zones")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", UUID.randomUUID());

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("blank serviceType header passes through")
    void filter_Passes_WhenBlankServiceTypeHeader() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/iowa/prod/requests/Students")
                .header(SifHeaders.SERVICE_TYPE, "   ")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", UUID.randomUUID());

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    // ========== Case-Insensitive Matching ==========

    @Test
    @DisplayName("lowercase 'object' is accepted for /requests")
    void filter_Passes_LowercaseObject() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/requests/Students", "object");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("mixed case 'Object' is accepted for /requests")
    void filter_Passes_MixedCaseObject() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/requests/Students", "Object");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("uppercase 'OBJECT' is accepted for /requests")
    void filter_Passes_UppercaseObject() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/requests/Students", "OBJECT");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    @Test
    @DisplayName("mixed case 'fUnCtIoNaL' is accepted for /services")
    void filter_Passes_ScrambledCaseFunctional() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/services/StudentLocator", "fUnCtIoNaL");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockChain).filter(exchange);
    }

    // ========== Unrecognized serviceType → HTTP 400 with Full Value List ==========

    @Test
    @DisplayName("unrecognized serviceType 'BANANA' → HTTP 400 with SVC_TYPE_002")
    void filter_Rejects_UnrecognizedType_WithFullValueList() throws Exception {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/requests/Students", "BANANA");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mockChain, never()).filter(any(ServerWebExchange.class));

        // Verify error response body
        byte[] body = extractResponseBody(exchange);
        SifErrorResponse errorResponse = objectMapper.readValue(body, SifErrorResponseEnvelope.class).error();

        assertThat(errorResponse.subCode()).isEqualTo("SVC_TYPE_002");
        assertThat(errorResponse.description()).contains("BANANA");
        assertThat(errorResponse.description()).contains("OBJECT");
        assertThat(errorResponse.description()).contains("UTILITY");
        assertThat(errorResponse.description()).contains("FUNCTIONAL");
        assertThat(errorResponse.description()).contains("SERVICEPATH");
        assertThat(errorResponse.description()).contains("XQUERYTEMPLATE");
        assertThat(errorResponse.description()).contains("SERVICE");
    }

    @Test
    @DisplayName("unrecognized serviceType 'FOO_BAR' → HTTP 400")
    void filter_Rejects_UnrecognizedType_FooBar() {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/services/Something", "FOO_BAR");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mockChain, never()).filter(any(ServerWebExchange.class));
    }

    // ========== Error Response Body Structure ==========

    @Test
    @DisplayName("error response matches SifErrorResponse structure for SVC_TYPE_001")
    void filter_ErrorResponse_MatchesSifStructure_SvcType001() throws Exception {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/requests/Students", "UTILITY");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        byte[] body = extractResponseBody(exchange);
        SifErrorResponse errorResponse = objectMapper.readValue(body, SifErrorResponseEnvelope.class).error();

        assertThat(errorResponse.subCode()).isEqualTo("SVC_TYPE_001");
        assertThat(errorResponse.message()).isNotBlank();
        assertThat(errorResponse.description()).contains("UTILITY");
        assertThat(errorResponse.description()).contains("/requests");
        assertThat(errorResponse.scope()).isEqualTo("BROKER");
        // timestamp is @JsonIgnore (internal, not part of SIF 3.7 errorCommon) — absent from the wire
        assertThat(errorResponse.timestamp()).isNull();
        // 'field' is @JsonIgnore — not serialized (not part of SIF 3.7 errorCommon)
        assertThat(errorResponse.field()).isNull();
        assertThat(errorResponse.stackTrace()).isNull();
    }

    @Test
    @DisplayName("error response matches SifErrorResponse structure for SVC_TYPE_002")
    void filter_ErrorResponse_MatchesSifStructure_SvcType002() throws Exception {
        MockServerWebExchange exchange = buildExchange("/iowa/prod/requests/Students", "INVALID_VALUE");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        byte[] body = extractResponseBody(exchange);
        SifErrorResponse errorResponse = objectMapper.readValue(body, SifErrorResponseEnvelope.class).error();

        assertThat(errorResponse.subCode()).isEqualTo("SVC_TYPE_002");
        assertThat(errorResponse.message()).isNotBlank();
        assertThat(errorResponse.description()).contains("INVALID_VALUE");
        assertThat(errorResponse.scope()).isEqualTo("BROKER");
        // timestamp is @JsonIgnore (internal, not part of SIF 3.7 errorCommon) — absent from the wire
        assertThat(errorResponse.timestamp()).isNull();
        // 'field' is @JsonIgnore — not serialized (not part of SIF 3.7 errorCommon)
        assertThat(errorResponse.field()).isNull();
        assertThat(errorResponse.stackTrace()).isNull();
    }

    // ========== WARN Logging on Rejection ==========

    @Test
    @DisplayName("WARN log includes environmentId, path, invalid value, and expected values")
    void filter_LogsWarn_OnRejection_WithExpectedFields() {
        UUID envId = UUID.randomUUID();
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/iowa/prod/requests/Students")
                .header(SifHeaders.SERVICE_TYPE, "UTILITY")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", envId);

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        assertThat(logAppender.list).isNotEmpty();
        ILoggingEvent logEvent = logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .findFirst()
                .orElse(null);

        assertThat(logEvent).isNotNull();
        String logMessage = logEvent.getFormattedMessage();
        assertThat(logMessage).contains(envId.toString());
        assertThat(logMessage).contains("/iowa/prod/requests/Students");
        assertThat(logMessage).contains("UTILITY");
        // Expected values for REQUESTS category
        assertThat(logMessage).contains("OBJECT");
    }

    @Test
    @DisplayName("WARN log for unrecognized type includes environmentId and invalid value")
    void filter_LogsWarn_OnUnrecognizedType() {
        UUID envId = UUID.randomUUID();
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/iowa/prod/zones")
                .header(SifHeaders.SERVICE_TYPE, "BANANA")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", envId);

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        ILoggingEvent logEvent = logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .findFirst()
                .orElse(null);

        assertThat(logEvent).isNotNull();
        String logMessage = logEvent.getFormattedMessage();
        assertThat(logMessage).contains(envId.toString());
        assertThat(logMessage).contains("BANANA");
        assertThat(logMessage).contains("/iowa/prod/zones");
    }

    // ========== Helpers ==========

    /**
     * Builds a MockServerWebExchange with a GET request to the given path,
     * the given serviceType header, and a mock environmentId attribute.
     */
    private MockServerWebExchange buildExchange(String path, String serviceType) {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost" + path)
                .header(SifHeaders.SERVICE_TYPE, serviceType)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", UUID.randomUUID());
        return exchange;
    }

    /**
     * Extracts the response body bytes from a MockServerWebExchange.
     */
    private byte[] extractResponseBody(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString()
                .map(String::getBytes)
                .block();
    }
}
