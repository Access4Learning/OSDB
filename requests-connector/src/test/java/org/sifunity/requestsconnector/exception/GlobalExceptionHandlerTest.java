// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the requests-connector GlobalExceptionHandler.
 *
 * <p>Tests all exception handler methods via direct invocation (no Spring context).
 * Verifies response status, body code, and message fields.</p>
 *
 * <p><strong>Requirements:</strong> 10.1–10.6</p>
 */
@DisplayName("GlobalExceptionHandler (requests-connector)")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Nested
    @DisplayName("handleServiceUnavailable")
    class HandleServiceUnavailable {

        @Test
        @DisplayName("Should return 503 with SERVICE_UNAVAILABLE code naming the service")
        void shouldReturn503NamingTheService() {
            ServiceUnavailableException ex = new ServiceUnavailableException(
                "subscription-service", new ConnectException("Connection refused"));

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleServiceUnavailable(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(response.getBody().error().code()).isEqualTo(503);
            assertThat(response.getBody().error().message()).contains("subscription-service");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("handleNoProvider")
    class HandleNoProvider {

        @Test
        @DisplayName("Should return 404 with NO_PROVIDER code")
        void shouldReturn404WithNoProviderCode() {
            NoProviderException ex = new NoProviderException("StudentPersonal", "default", "ctx1");

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleNoProvider(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("NO_PROVIDER");
            assertThat(response.getBody().error().code()).isEqualTo(404);
            assertThat(response.getBody().error().message()).contains("StudentPersonal");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should include zone and context in message")
        void shouldIncludeZoneAndContextInMessage() {
            NoProviderException ex = new NoProviderException("DataExport", "zone1", null);

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleNoProvider(ex);

            assertThat(response.getBody().error().subCode()).isEqualTo("NO_PROVIDER");
            assertThat(response.getBody().error().message())
                    .contains("DataExport")
                    .contains("zone1");
        }
    }

    @Nested
    @DisplayName("handleNoSubscription")
    class HandleNoSubscription {

        @Test
        @DisplayName("Should return 404 with NO_SUBSCRIPTION code")
        void shouldReturn404WithNoSubscriptionCode() {
            NoSubscriptionException ex = new NoSubscriptionException(
                    "Provider has no request subscription for service=DataExport");

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleNoSubscription(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("NO_SUBSCRIPTION");
            assertThat(response.getBody().error().code()).isEqualTo(404);
            assertThat(response.getBody().error().message()).contains("DataExport");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("handleMissingHeader")
    class HandleMissingHeader {

        @Test
        @DisplayName("Should return 400 with MISSING_HEADER code")
        void shouldReturn400WithMissingHeaderCode() {
            MissingHeaderException ex = new MissingHeaderException(
                    "Required header 'X-Environment-Id' is missing");

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleMissingHeader(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("MISSING_HEADER");
            assertThat(response.getBody().error().code()).isEqualTo(400);
            assertThat(response.getBody().error().message()).contains("X-Environment-Id");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("handleUnsupportedPattern")
    class HandleUnsupportedPattern {

        @Test
        @DisplayName("Should return 400 with UNSUPPORTED_PATTERN code")
        void shouldReturn400WithUnsupportedPatternCode() {
            UnsupportedPatternException ex = new UnsupportedPatternException(
                    "Provider does not support DELAYED pattern for service=DataExport");

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleUnsupportedPattern(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("UNSUPPORTED_PATTERN");
            assertThat(response.getBody().error().code()).isEqualTo(400);
            assertThat(response.getBody().error().message()).contains("DELAYED");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("handleValidation")
    class HandleValidation {

        @Test
        @DisplayName("Should return 400 with VALIDATION_ERROR code and field details")
        void shouldReturn400WithValidationErrorCode() {
            BindingResult bindingResult = mock(BindingResult.class);
            FieldError error1 = new FieldError("body", "serviceName", "must not be blank");
            FieldError error2 = new FieldError("body", "zoneId", "must not be null");
            when(bindingResult.getFieldErrors()).thenReturn(List.of(error1, error2));

            java.lang.reflect.Method method;
            try {
                method = HandleValidation.class.getDeclaredMethod("dummyMethod", String.class);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
            org.springframework.core.MethodParameter methodParameter =
                    new org.springframework.core.MethodParameter(method, 0);

            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                    methodParameter, bindingResult);

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleValidation(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("VALIDATION_ERROR");
            assertThat(response.getBody().error().code()).isEqualTo(400);
            assertThat(response.getBody().error().message())
                    .contains("serviceName")
                    .contains("must not be blank")
                    .contains("zoneId");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }

        // Dummy method for creating MethodParameter in tests
        @SuppressWarnings("unused")
        private static void dummyMethod(String param) {
        }
    }

    @Nested
    @DisplayName("handleProviderTimeout")
    class HandleProviderTimeout {

        @Test
        @DisplayName("Should return 504 with PROVIDER_TIMEOUT code")
        void shouldReturn504WithProviderTimeoutCode() {
            ProviderTimeoutException ex = new ProviderTimeoutException(
                    "Provider at http://provider.example.com timed out after 30s");

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleProviderTimeout(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("PROVIDER_TIMEOUT");
            assertThat(response.getBody().error().code()).isEqualTo(504);
            assertThat(response.getBody().error().message()).contains("timed out");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("handleWebClientResponse")
    class HandleWebClientResponse {

        @Test
        @DisplayName("Should return 503 with SERVICE_UNAVAILABLE code for 5xx errors")
        void shouldReturn503ForServerErrors() {
            WebClientResponseException ex = WebClientResponseException.create(
                    500, "Internal Server Error", null,
                    "backend failure".getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleWebClientResponse(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(response.getBody().error().code()).isEqualTo(503);
            assertThat(response.getBody().error().message())
                    .contains("service is currently unavailable");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should return 503 for 502 Bad Gateway")
        void shouldReturn503For502() {
            WebClientResponseException ex = WebClientResponseException.create(
                    502, "Bad Gateway", null,
                    "upstream error".getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleWebClientResponse(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().error().subCode()).isEqualTo("SERVICE_UNAVAILABLE");
        }

        @Test
        @DisplayName("Should forward 4xx status with PROVIDER_ERROR code")
        void shouldForward4xxWithProviderErrorCode() {
            WebClientResponseException ex = WebClientResponseException.create(
                    404, "Not Found", null,
                    "resource not found".getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleWebClientResponse(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("PROVIDER_ERROR");
            assertThat(response.getBody().error().code()).isEqualTo(404);
            assertThat(response.getBody().error().message()).contains("resource not found");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should forward 400 Bad Request with PROVIDER_ERROR code")
        void shouldForward400WithProviderErrorCode() {
            WebClientResponseException ex = WebClientResponseException.create(
                    400, "Bad Request", null,
                    "invalid payload".getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleWebClientResponse(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().error().subCode()).isEqualTo("PROVIDER_ERROR");
            assertThat(response.getBody().error().code()).isEqualTo(400);
            assertThat(response.getBody().error().message()).contains("invalid payload");
        }

        @Test
        @DisplayName("Should forward 409 Conflict with PROVIDER_ERROR code")
        void shouldForward409WithProviderErrorCode() {
            WebClientResponseException ex = WebClientResponseException.create(
                    409, "Conflict", null,
                    "duplicate resource".getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleWebClientResponse(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().error().subCode()).isEqualTo("PROVIDER_ERROR");
            assertThat(response.getBody().error().code()).isEqualTo(409);
        }

        @Test
        @DisplayName("Should return 502 PROVIDER_RESPONSE_UNPROCESSABLE for a non-error (200) status")
        void shouldReturn502ForNonErrorStatus() {
            // A WebClientResponseException carrying a 200 means the provider
            // responded OK but the body could not be read/decoded.
            WebClientResponseException ex = WebClientResponseException.create(
                    200, "OK", null,
                    new byte[0],
                    StandardCharsets.UTF_8);

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleWebClientResponse(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("PROVIDER_RESPONSE_UNPROCESSABLE");
            assertThat(response.getBody().error().code()).isEqualTo(502);
            assertThat(response.getBody().error().message()).contains("could not read or decode");
        }

        @Test
        @DisplayName("Should return 502 PROVIDER_RESPONSE_TOO_LARGE when a 200 wraps a buffer overflow")
        void shouldReturn502TooLargeWhenBufferOverflowCause() {
            WebClientResponseException ex = WebClientResponseException.create(
                    200, "OK", null, new byte[0], StandardCharsets.UTF_8);
            ex.initCause(new org.springframework.core.io.buffer.DataBufferLimitException(
                    "Exceeded limit on max bytes to buffer : 262144"));

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleWebClientResponse(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(response.getBody().error().subCode()).isEqualTo("PROVIDER_RESPONSE_TOO_LARGE");
            assertThat(response.getBody().error().code()).isEqualTo(502);
            assertThat(response.getBody().error().message()).contains("navigationPageSize");
        }
    }

    @Nested
    @DisplayName("handleDataBufferLimit")
    class HandleDataBufferLimit {

        @Test
        @DisplayName("Should return 502 PROVIDER_RESPONSE_TOO_LARGE with an actionable message")
        void shouldReturn502TooLarge() {
            org.springframework.core.io.buffer.DataBufferLimitException ex =
                    new org.springframework.core.io.buffer.DataBufferLimitException(
                            "Exceeded limit on max bytes to buffer : 262144");

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleDataBufferLimit(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("PROVIDER_RESPONSE_TOO_LARGE");
            assertThat(response.getBody().error().code()).isEqualTo(502);
            assertThat(response.getBody().error().message())
                    .contains("exceeded the maximum size")
                    .contains("navigationPageSize");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("handleUnexpected")
    class HandleUnexpected {

        @Test
        @DisplayName("Should return 500 with INTERNAL_ERROR code")
        void shouldReturn500WithInternalErrorCode() {
            Exception ex = new RuntimeException("Something went wrong");

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleUnexpected(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("INTERNAL_ERROR");
            assertThat(response.getBody().error().code()).isEqualTo(500);
            assertThat(response.getBody().error().message())
                    .isEqualTo("An unexpected error occurred");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should return 500 for NullPointerException")
        void shouldReturn500ForNpe() {
            Exception ex = new NullPointerException("null ref");

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleUnexpected(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().error().subCode()).isEqualTo("INTERNAL_ERROR");
        }
    }

    @Nested
    @DisplayName("handleWebClientRequest")
    class HandleWebClientRequest {

        @Test
        @DisplayName("Should return 502 PROVIDER_UNAVAILABLE when provider is unreachable")
        void shouldReturn502WhenProviderUnreachable() {
            WebClientRequestException ex = new WebClientRequestException(
                    new ConnectException("Connection refused"),
                    org.springframework.http.HttpMethod.GET,
                    URI.create("http://provider.example/StudentSnapshot"),
                    org.springframework.http.HttpHeaders.EMPTY);

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleWebClientRequest(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("PROVIDER_UNAVAILABLE");
            assertThat(response.getBody().error().code()).isEqualTo(502);
            assertThat(response.getBody().error().message())
                    .contains("could not be reached");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Error response format")
    class ErrorResponseFormat {

        @Test
        @DisplayName("All responses should contain code, message, status, and timestamp")
        void allResponsesShouldHaveRequiredFields() {
            // Test a representative handler
            NoProviderException ex = new NoProviderException("Svc", "z", "c");
            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleNoProvider(ex);

            SifErrorResponse error = response.getBody().error();
            assertThat(error.subCode()).isNotNull();
            assertThat(error.message()).isNotNull();
            assertThat(error.code()).isEqualTo(404);
            assertThat(error.timestamp()).isNotNull();
        }
    }
}
