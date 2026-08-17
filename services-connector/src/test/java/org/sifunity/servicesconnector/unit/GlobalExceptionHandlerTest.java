// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.exception.InvalidRequestException;
import org.sifunity.broker.common.exception.ServiceUnavailableException;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.servicesconnector.exception.GlobalExceptionHandler;
import org.sifunity.servicesconnector.exception.NoProviderException;
import org.sifunity.servicesconnector.exception.NoSubscriptionException;
import org.sifunity.servicesconnector.exception.ProviderTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GlobalExceptionHandler.
 * 
 * <p>Tests exception handling for all exception types and verifies that
 * error responses conform to SIF 3.7 format.</p>
 * 
 * <p><strong>Requirements:</strong> 2.4, 2.5, 3.6, 6.4, 11.1-11.6, 15.4</p>
 */
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {
    
    private GlobalExceptionHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }
    
    @Nested
    @DisplayName("SifBrokerException Handling")
    class SifBrokerExceptionHandling {
        
        @Test
        @DisplayName("Should handle InvalidRequestException with 400 status")
        void shouldHandleInvalidRequestException() {
            // Given
            InvalidRequestException ex = new InvalidRequestException(
                "Invalid request parameters"
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleSifBrokerException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("VAL_001");
            assertThat(response.getBody().error().message()).isEqualTo("Invalid request parameters");
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
        
        @Test
        @DisplayName("Should handle ServiceUnavailableException with 503 status")
        void shouldHandleServiceUnavailableException() {
            // Given
            ServiceUnavailableException ex = new ServiceUnavailableException(
                "Job Service",
                "Service is temporarily unavailable"
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleSifBrokerException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("SERVICE_002");
            assertThat(response.getBody().error().message()).contains("Job Service");
            assertThat(response.getBody().error().message()).contains("unavailable");
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
        
        @Test
        @DisplayName("Should handle generic SifBrokerException")
        void shouldHandleGenericSifBrokerException() {
            // Given
            SifBrokerException ex = new SifBrokerException(
                "TEST_001",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Test error message"
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleSifBrokerException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("TEST_001");
            assertThat(response.getBody().error().message()).isEqualTo("Test error message");
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("NoProviderException Handling")
    class NoProviderExceptionHandling {
        
        @Test
        @DisplayName("Should handle NoProviderException with 404 status")
        void shouldHandleNoProviderException() {
            // Given
            NoProviderException ex = new NoProviderException(
                "DataExport",
                "default",
                "production"
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleNoProviderException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("ROUTE_001");
            assertThat(response.getBody().error().message())
                .contains("No provider registered")
                .contains("DataExport")
                .contains("default")
                .contains("production");
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
        
        @Test
        @DisplayName("Should handle NoProviderException with null contextId")
        void shouldHandleNoProviderExceptionWithNullContext() {
            // Given
            NoProviderException ex = new NoProviderException(
                "DataExport",
                "default",
                null
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleNoProviderException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("ROUTE_001");
            assertThat(response.getBody().error().message())
                .contains("No provider registered")
                .contains("DataExport")
                .contains("default");
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
        }
    }
    
    @Nested
    @DisplayName("WebClientResponseException Handling")
    class WebClientResponseExceptionHandling {
        
        @Test
        @DisplayName("Should forward 404 error from downstream service")
        void shouldForward404Error() {
            // Given
            WebClientResponseException ex = WebClientResponseException.create(
                404,
                "Not Found",
                null,
                "Job not found".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = 
                handler.handleWebClientResponseException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("DOWNSTREAM_ERROR");
            assertThat(response.getBody().error().message()).contains("Not Found");
            assertThat(response.getBody().error().description()).contains("Job not found");
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should return 502 PROVIDER_RESPONSE_UNPROCESSABLE for a non-error (200) status")
        void shouldReturn502ForNonErrorStatus() {
            // A WebClientResponseException carrying a 200 means the downstream
            // responded OK but the body could not be read/decoded.
            WebClientResponseException ex = WebClientResponseException.create(
                200, "OK", null, new byte[0], StandardCharsets.UTF_8
            );

            ResponseEntity<SifErrorResponseEnvelope> response =
                handler.handleWebClientResponseException(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("PROVIDER_RESPONSE_UNPROCESSABLE");
            assertThat(response.getBody().error().message()).contains("could not read or decode");
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
        }

        @Test
        @DisplayName("Should return 502 PROVIDER_RESPONSE_TOO_LARGE when a 200 wraps a buffer overflow")
        void shouldReturn502TooLargeForBufferOverflow() {
            WebClientResponseException ex = WebClientResponseException.create(
                200, "OK", null, new byte[0], StandardCharsets.UTF_8
            );
            ex.initCause(new org.springframework.core.io.buffer.DataBufferLimitException(
                "Exceeded limit on max bytes to buffer : 262144"));

            ResponseEntity<SifErrorResponseEnvelope> response =
                handler.handleWebClientResponseException(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(response.getBody().error().subCode()).isEqualTo("PROVIDER_RESPONSE_TOO_LARGE");
            assertThat(response.getBody().error().message()).contains("navigationPageSize");
        }

        @Test
        @DisplayName("Should return 502 PROVIDER_RESPONSE_TOO_LARGE for a direct DataBufferLimitException")
        void shouldReturn502ForDataBufferLimit() {
            org.springframework.core.io.buffer.DataBufferLimitException ex =
                new org.springframework.core.io.buffer.DataBufferLimitException(
                    "Exceeded limit on max bytes to buffer : 262144");

            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleDataBufferLimit(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(response.getBody().error().subCode()).isEqualTo("PROVIDER_RESPONSE_TOO_LARGE");
            assertThat(response.getBody().error().message()).contains("exceeded the maximum size");
        }
        
        @Test
        @DisplayName("Should forward 400 error from downstream service")
        void shouldForward400Error() {
            // Given
            WebClientResponseException ex = WebClientResponseException.create(
                400,
                "Bad Request",
                null,
                "Invalid state transition".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = 
                handler.handleWebClientResponseException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("DOWNSTREAM_ERROR");
            assertThat(response.getBody().error().message()).contains("Bad Request");
            assertThat(response.getBody().error().description()).contains("Invalid state transition");
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
        }
        
        @Test
        @DisplayName("Should forward 409 error from downstream service")
        void shouldForward409Error() {
            // Given
            WebClientResponseException ex = WebClientResponseException.create(
                409,
                "Conflict",
                null,
                "Job is in progress".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = 
                handler.handleWebClientResponseException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("DOWNSTREAM_ERROR");
            assertThat(response.getBody().error().message()).contains("Conflict");
            assertThat(response.getBody().error().description()).contains("Job is in progress");
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
        }
        
        @Test
        @DisplayName("Should forward 503 error from downstream service")
        void shouldForward503Error() {
            // Given
            WebClientResponseException ex = WebClientResponseException.create(
                503,
                "Service Unavailable",
                null,
                "Job Service is unavailable".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = 
                handler.handleWebClientResponseException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("DOWNSTREAM_ERROR");
            assertThat(response.getBody().error().message()).contains("Service Unavailable");
            assertThat(response.getBody().error().description()).contains("Job Service is unavailable");
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
        }
        
        @Test
        @DisplayName("Should handle downstream error with empty response body")
        void shouldHandleEmptyResponseBody() {
            // Given
            WebClientResponseException ex = WebClientResponseException.create(
                500,
                "Internal Server Error",
                null,
                new byte[0],
                StandardCharsets.UTF_8
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = 
                handler.handleWebClientResponseException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("DOWNSTREAM_ERROR");
            assertThat(response.getBody().error().message()).contains("Internal Server Error");
            assertThat(response.getBody().error().description()).isNull();
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
        }
    }
    
    @Nested
    @DisplayName("Validation Exception Handling")
    class ValidationExceptionHandling {
        
        @Test
        @DisplayName("Should handle MethodArgumentNotValidException with field errors")
        void shouldHandleValidationException() {
            // Given
            BindingResult bindingResult = mock(BindingResult.class);
            FieldError error1 = new FieldError("requestBody", "serviceName", "must not be blank");
            FieldError error2 = new FieldError("requestBody", "zoneId", "must not be blank");
            when(bindingResult.getFieldErrors()).thenReturn(List.of(error1, error2));
            
            // Create a real MethodParameter using reflection
            java.lang.reflect.Method method;
            try {
                method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
            org.springframework.core.MethodParameter methodParameter = 
                new org.springframework.core.MethodParameter(method, 0);
            
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                methodParameter, bindingResult
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleValidationException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("VAL_001");
            assertThat(response.getBody().error().message()).isEqualTo("Validation failed");
            assertThat(response.getBody().error().description())
                .contains("serviceName: must not be blank")
                .contains("zoneId: must not be blank");
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
        
        @Test
        @DisplayName("Should handle validation exception with single field error")
        void shouldHandleSingleFieldError() {
            // Given
            BindingResult bindingResult = mock(BindingResult.class);
            FieldError error = new FieldError("requestBody", "serviceName", "must not be blank");
            when(bindingResult.getFieldErrors()).thenReturn(List.of(error));
            
            // Create a real MethodParameter using reflection
            java.lang.reflect.Method method;
            try {
                method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
            org.springframework.core.MethodParameter methodParameter = 
                new org.springframework.core.MethodParameter(method, 0);
            
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                methodParameter, bindingResult
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleValidationException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("VAL_001");
            assertThat(response.getBody().error().description()).isEqualTo("serviceName: must not be blank");
        }
    }
    
    // Dummy method for creating MethodParameter in tests
    @SuppressWarnings("unused")
    private static void dummyMethod(String param) {
        // Used only for reflection in tests
    }
    
    @Nested
    @DisplayName("IllegalArgumentException Handling")
    class IllegalArgumentExceptionHandling {
        
        @Test
        @DisplayName("Should handle IllegalArgumentException with 400 status")
        void shouldHandleIllegalArgumentException() {
            // Given
            IllegalArgumentException ex = new IllegalArgumentException(
                "Invalid parameter value"
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = 
                handler.handleIllegalArgumentException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("VAL_002");
            assertThat(response.getBody().error().message()).isEqualTo("Invalid argument");
            assertThat(response.getBody().error().description()).isEqualTo("Invalid parameter value");
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Generic Exception Handling")
    class GenericExceptionHandling {
        
        @Test
        @DisplayName("Should handle unexpected Exception with 500 status")
        void shouldHandleUnexpectedException() {
            // Given
            Exception ex = new RuntimeException("Unexpected error occurred");
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleGenericException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("SYS_001");
            assertThat(response.getBody().error().message()).isEqualTo("Internal server error");
            assertThat(response.getBody().error().description())
                .contains("An unexpected error occurred");
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
        
        @Test
        @DisplayName("Should handle NullPointerException with 500 status")
        void shouldHandleNullPointerException() {
            // Given
            Exception ex = new NullPointerException("Null value encountered");
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleGenericException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("SYS_001");
            assertThat(response.getBody().error().message()).isEqualTo("Internal server error");
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
        }
    }
    
    @Nested
    @DisplayName("NoSubscriptionException Handling")
    class NoSubscriptionExceptionHandling {
        
        @Test
        @DisplayName("Should handle NoSubscriptionException with 404 status and ROUTE_002 code")
        void shouldHandleNoSubscriptionException() {
            // Given
            NoSubscriptionException ex = new NoSubscriptionException(
                "No DELAYED_REQUEST subscription found for service=DataExport"
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleNoSubscriptionException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("ROUTE_002");
            assertThat(response.getBody().error().message()).contains("DataExport");
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("ProviderTimeoutException Handling")
    class ProviderTimeoutExceptionHandling {
        
        @Test
        @DisplayName("Should handle ProviderTimeoutException with 504 status")
        void shouldHandleProviderTimeoutException() {
            // Given
            ProviderTimeoutException ex = new ProviderTimeoutException(
                "Provider at http://provider.example.com timed out after 30s"
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleProviderTimeout(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("PROVIDER_TIMEOUT");
            assertThat(response.getBody().error().message()).contains("Provider request timed out");
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("MissingRequestHeaderException Handling")
    class MissingRequestHeaderExceptionHandling {
        
        @Test
        @DisplayName("Should handle MissingRequestHeaderException with 400 status and VAL_003 code")
        void shouldHandleMissingRequestHeaderException() throws NoSuchMethodException {
            // Given - create a real MissingRequestHeaderException
            java.lang.reflect.Method method = GlobalExceptionHandlerTest.class
                    .getDeclaredMethod("dummyMethod", String.class);
            org.springframework.core.MethodParameter methodParameter =
                    new org.springframework.core.MethodParameter(method, 0);
            
            MissingRequestHeaderException ex = new MissingRequestHeaderException(
                "X-Environment-Id", methodParameter
            );
            
            // When
            ResponseEntity<SifErrorResponseEnvelope> response = handler.handleMissingRequestHeaderException(ex);
            
            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().subCode()).isEqualTo("VAL_003");
            assertThat(response.getBody().error().message()).isEqualTo("Missing required header");
            assertThat(response.getBody().error().description()).contains("X-Environment-Id");
            assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
            assertThat(response.getBody().error().timestamp()).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Error Response Format Validation")
    class ErrorResponseFormatValidation {
        
        @Test
        @DisplayName("All error responses should conform to SIF 3.7 format")
        void allErrorResponsesShouldConformToSifFormat() {
            // Test various exception types and verify SIF format
            
            // 1. SifBrokerException
            SifBrokerException sifEx = new SifBrokerException(
                "TEST_001", HttpStatus.BAD_REQUEST, "Test error"
            );
            ResponseEntity<SifErrorResponseEnvelope> response1 = handler.handleSifBrokerException(sifEx);
            verifySifFormat(response1.getBody().error());
            
            // 2. NoProviderException
            NoProviderException noProviderEx = new NoProviderException(
                "TestService", "zone1", "context1"
            );
            ResponseEntity<SifErrorResponseEnvelope> response2 = handler.handleNoProviderException(noProviderEx);
            verifySifFormat(response2.getBody().error());
            
            // 3. WebClientResponseException
            WebClientResponseException webEx = WebClientResponseException.create(
                404, "Not Found", null, new byte[0], StandardCharsets.UTF_8
            );
            ResponseEntity<SifErrorResponseEnvelope> response3 = handler.handleWebClientResponseException(webEx);
            verifySifFormat(response3.getBody().error());
            
            // 4. IllegalArgumentException
            IllegalArgumentException illegalEx = new IllegalArgumentException("Invalid arg");
            ResponseEntity<SifErrorResponseEnvelope> response4 = handler.handleIllegalArgumentException(illegalEx);
            verifySifFormat(response4.getBody().error());
            
            // 5. Generic Exception
            Exception genericEx = new RuntimeException("Unexpected");
            ResponseEntity<SifErrorResponseEnvelope> response5 = handler.handleGenericException(genericEx);
            verifySifFormat(response5.getBody().error());
        }
        
        private void verifySifFormat(SifErrorResponse error) {
            assertThat(error).isNotNull();
            assertThat(error.subCode()).isNotNull().isNotBlank();
            assertThat(error.message()).isNotNull().isNotBlank();
            assertThat(error.scope()).isEqualTo("BROKER");
            assertThat(error.timestamp()).isNotNull();
        }
    }
}
