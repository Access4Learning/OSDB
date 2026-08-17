// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.servicesconnector.exception.GlobalExceptionHandler;
import org.sifunity.servicesconnector.exception.NoProviderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for GlobalExceptionHandler.
 * 
 * <p>Tests universal properties that should hold for all exception handling scenarios.</p>
 * 
 * <p><strong>Requirements:</strong> 11.5, 11.6</p>
 */
@PropertyDefaults(tries = 100)
class GlobalExceptionHandlerPropertiesTest {
    
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    
    /**
     * Property 20: Unexpected Errors Return 500
     * 
     * <p>For any unexpected exception (not SifBrokerException or WebClientResponseException),
     * the handler should return HTTP 500 Internal Server Error.</p>
     * 
     * <p><strong>Validates: Requirements 11.5</strong></p>
     */
    @Property
    @Label("Feature: services-connector, Property 20: Unexpected Errors Return 500")
    void unexpectedErrorsReturn500(@ForAll("unexpectedExceptions") Exception ex) {
        // When: Handle unexpected exception
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleGenericException(ex);
        
        // Then: Should return 500 status
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("SYS_001");
        assertThat(response.getBody().error().message()).isEqualTo("Internal server error");
        assertThat(response.getBody().error().scope()).isEqualTo("BROKER");
        assertThat(response.getBody().error().timestamp()).isNotNull();
    }
    
    /**
     * Property 21: Error Responses Conform To SIF Format
     * 
     * <p>For any exception handled by the GlobalExceptionHandler, the error response
     * should conform to SIF 3.7 format with required fields: code, message, scope, timestamp.</p>
     * 
     * <p><strong>Validates: Requirements 11.6</strong></p>
     */
    @Property
    @Label("Feature: services-connector, Property 21: Error Responses Conform To SIF Format")
    void errorResponsesConformToSifFormat(@ForAll("allExceptions") Exception ex) {
        // When: Handle any exception
        ResponseEntity<SifErrorResponseEnvelope> response = handleException(ex);
        
        // Then: Response should conform to SIF format
        assertThat(response.getBody()).isNotNull();
        SifErrorResponse error = response.getBody().error();
        
        // Required fields must be present and non-null
        assertThat(error.subCode())
            .as("Error code must be present")
            .isNotNull()
            .isNotBlank();
        
        assertThat(error.message())
            .as("Error message must be present")
            .isNotNull()
            .isNotBlank();
        
        assertThat(error.scope())
            .as("Error scope must be BROKER")
            .isEqualTo("BROKER");
        
        assertThat(error.timestamp())
            .as("Error timestamp must be present")
            .isNotNull();
        
        // HTTP status should be appropriate
        assertThat(response.getStatusCode().value())
            .as("HTTP status should be a valid error status")
            .isGreaterThanOrEqualTo(400)
            .isLessThan(600);
    }
    
    /**
     * Property: SifBrokerException Status Codes Are Preserved
     * 
     * <p>For any SifBrokerException, the handler should preserve the HTTP status code
     * from the exception.</p>
     */
    @Property
    @Label("Feature: services-connector, SifBrokerException Status Codes Are Preserved")
    void sifBrokerExceptionStatusCodesArePreserved(
            @ForAll("errorCodes") String errorCode,
            @ForAll("httpStatuses") HttpStatus status,
            @ForAll("errorMessages") String message) {
        
        // Given: A SifBrokerException with specific status
        SifBrokerException ex = new SifBrokerException(errorCode, status, message);
        
        // When: Handle the exception
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleSifBrokerException(ex);
        
        // Then: Status code should be preserved
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo(errorCode);
        assertThat(response.getBody().error().message()).isEqualTo(message);
    }
    
    /**
     * Property: WebClientResponseException Status Codes Are Forwarded
     * 
     * <p>For any WebClientResponseException, the handler should forward the HTTP status code
     * from the downstream service.</p>
     */
    @Property
    @Label("Feature: services-connector, WebClientResponseException Status Codes Are Forwarded")
    void webClientResponseExceptionStatusCodesAreForwarded(
            @ForAll("httpErrorStatuses") int statusCode,
            @ForAll("errorMessages") String message) {
        
        // Given: A WebClientResponseException with specific status
        WebClientResponseException ex = WebClientResponseException.create(
            statusCode,
            HttpStatus.valueOf(statusCode).getReasonPhrase(),
            null,
            message.getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8
        );
        
        // When: Handle the exception
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleWebClientResponseException(ex);
        
        // Then: Status code should be forwarded
        assertThat(response.getStatusCode().value()).isEqualTo(statusCode);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("DOWNSTREAM_ERROR");
    }
    
    /**
     * Property: NoProviderException Always Returns 404
     * 
     * <p>For any NoProviderException, the handler should always return HTTP 404 Not Found.</p>
     */
    @Property
    @Label("Feature: services-connector, NoProviderException Always Returns 404")
    void noProviderExceptionAlwaysReturns404(
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId) {
        
        // Given: A NoProviderException
        NoProviderException ex = new NoProviderException(serviceName, zoneId, contextId);
        
        // When: Handle the exception
        ResponseEntity<SifErrorResponseEnvelope> response = handler.handleNoProviderException(ex);
        
        // Then: Should return 404 status
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().subCode()).isEqualTo("ROUTE_001");
        assertThat(response.getBody().error().message()).contains(serviceName, zoneId);
    }
    
    // ========== Arbitraries ==========
    
    @Provide
    Arbitrary<Exception> unexpectedExceptions() {
        return Arbitraries.oneOf(
            Arbitraries.just(new RuntimeException("Unexpected runtime error")),
            Arbitraries.just(new NullPointerException("Null pointer encountered")),
            Arbitraries.just(new IllegalStateException("Illegal state")),
            Arbitraries.just(new ArrayIndexOutOfBoundsException("Index out of bounds")),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100)
                .map(msg -> new Exception(msg))
        );
    }
    
    @Provide
    Arbitrary<Exception> allExceptions() {
        return Arbitraries.oneOf(
            // SifBrokerExceptions
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
                .flatMap(code -> Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100)
                    .map(msg -> new SifBrokerException(code, HttpStatus.BAD_REQUEST, msg))),
            
            // NoProviderExceptions
            Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
            ).as(NoProviderException::new),
            
            // WebClientResponseExceptions - use only valid HTTP status codes
            httpErrorStatuses()
                .flatMap(status -> Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100)
                    .map(msg -> WebClientResponseException.create(
                        status,
                        HttpStatus.valueOf(status).getReasonPhrase(),
                        null,
                        msg.getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8
                    ))),
            
            // Generic exceptions
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100)
                .map(RuntimeException::new)
        );
    }
    
    @Provide
    Arbitrary<String> errorCodes() {
        return Arbitraries.oneOf(
            Arbitraries.just("VAL_001"),
            Arbitraries.just("SVC_001"),
            Arbitraries.just("AUTH_001"),
            Arbitraries.just("ROUTE_001"),
            Arbitraries.strings().alpha().ofLength(3)
                .map(prefix -> prefix.toUpperCase() + "_001")
        );
    }
    
    @Provide
    Arbitrary<HttpStatus> httpStatuses() {
        return Arbitraries.of(
            HttpStatus.BAD_REQUEST,
            HttpStatus.UNAUTHORIZED,
            HttpStatus.FORBIDDEN,
            HttpStatus.NOT_FOUND,
            HttpStatus.CONFLICT,
            HttpStatus.INTERNAL_SERVER_ERROR,
            HttpStatus.SERVICE_UNAVAILABLE
        );
    }
    
    @Provide
    Arbitrary<Integer> httpErrorStatuses() {
        return Arbitraries.oneOf(
            Arbitraries.just(400),
            Arbitraries.just(404),
            Arbitraries.just(409),
            Arbitraries.just(500),
            Arbitraries.just(503)
        );
    }
    
    @Provide
    Arbitrary<String> errorMessages() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(200);
    }
    
    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.oneOf(
            Arbitraries.just("DataExport"),
            Arbitraries.just("BulkImport"),
            Arbitraries.just("StudentSync"),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
        );
    }
    
    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.oneOf(
            Arbitraries.just("default"),
            Arbitraries.just("zone1"),
            Arbitraries.just("zone2"),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
        );
    }
    
    @Provide
    Arbitrary<String> contextIds() {
        return Arbitraries.oneOf(
            Arbitraries.just("production"),
            Arbitraries.just("staging"),
            Arbitraries.just("development"),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50),
            Arbitraries.just(null)
        );
    }
    
    // ========== Helper Methods ==========
    
    private ResponseEntity<SifErrorResponseEnvelope> handleException(Exception ex) {
        if (ex instanceof SifBrokerException) {
            return handler.handleSifBrokerException((SifBrokerException) ex);
        } else if (ex instanceof NoProviderException) {
            return handler.handleNoProviderException((NoProviderException) ex);
        } else if (ex instanceof WebClientResponseException) {
            return handler.handleWebClientResponseException((WebClientResponseException) ex);
        } else {
            return handler.handleGenericException(ex);
        }
    }
}
