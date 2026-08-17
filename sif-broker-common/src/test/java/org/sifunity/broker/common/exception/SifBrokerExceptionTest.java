// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SifBrokerException.
 * Tests exception construction, field access, and inheritance behavior.
 */
class SifBrokerExceptionTest {

    @Test
    void testConstructor_withErrorCodeHttpStatusAndMessage() {
        String errorCode = "TEST_001";
        HttpStatus httpStatus = HttpStatus.BAD_REQUEST;
        String message = "Test error message";

        SifBrokerException exception = new SifBrokerException(errorCode, httpStatus, message);

        assertThat(exception.getErrorCode()).isEqualTo(errorCode);
        assertThat(exception.getHttpStatus()).isEqualTo(httpStatus);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getRequestId()).isNull();
        assertThat(exception.getEnvironmentId()).isNull();
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void testConstructor_withErrorCodeHttpStatusMessageAndCause() {
        String errorCode = "TEST_002";
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "Test error with cause";
        Throwable cause = new RuntimeException("Root cause");

        SifBrokerException exception = new SifBrokerException(errorCode, httpStatus, message, cause);

        assertThat(exception.getErrorCode()).isEqualTo(errorCode);
        assertThat(exception.getHttpStatus()).isEqualTo(httpStatus);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getRequestId()).isNull();
        assertThat(exception.getEnvironmentId()).isNull();
    }

    @Test
    void testConstructor_withContextInformation() {
        String errorCode = "TEST_003";
        HttpStatus httpStatus = HttpStatus.FORBIDDEN;
        String message = "Test error with context";
        String requestId = "req-12345";
        String environmentId = "env-67890";

        SifBrokerException exception = new SifBrokerException(
            errorCode, httpStatus, message, requestId, environmentId
        );

        assertThat(exception.getErrorCode()).isEqualTo(errorCode);
        assertThat(exception.getHttpStatus()).isEqualTo(httpStatus);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getRequestId()).isEqualTo(requestId);
        assertThat(exception.getEnvironmentId()).isEqualTo(environmentId);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void testConstructor_withNullRequestIdAndEnvironmentId() {
        String errorCode = "TEST_004";
        HttpStatus httpStatus = HttpStatus.NOT_FOUND;
        String message = "Test error with null context";

        SifBrokerException exception = new SifBrokerException(
            errorCode, httpStatus, message, null, null
        );

        assertThat(exception.getErrorCode()).isEqualTo(errorCode);
        assertThat(exception.getHttpStatus()).isEqualTo(httpStatus);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getRequestId()).isNull();
        assertThat(exception.getEnvironmentId()).isNull();
    }

    @Test
    void testInheritance_extendsRuntimeException() {
        SifBrokerException exception = new SifBrokerException(
            "TEST_005", HttpStatus.BAD_REQUEST, "Test message"
        );

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testErrorCodeMapping_authentication() {
        SifBrokerException exception = new SifBrokerException(
            "AUTH_001", HttpStatus.UNAUTHORIZED, "Invalid token"
        );

        assertThat(exception.getErrorCode()).isEqualTo("AUTH_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testErrorCodeMapping_authorization() {
        SifBrokerException exception = new SifBrokerException(
            "AUTHZ_001", HttpStatus.FORBIDDEN, "Insufficient permissions"
        );

        assertThat(exception.getErrorCode()).isEqualTo("AUTHZ_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void testErrorCodeMapping_routing() {
        SifBrokerException exception = new SifBrokerException(
            "ROUTE_001", HttpStatus.NOT_FOUND, "Provider not found"
        );

        assertThat(exception.getErrorCode()).isEqualTo("ROUTE_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testErrorCodeMapping_validation() {
        SifBrokerException exception = new SifBrokerException(
            "VAL_001", HttpStatus.BAD_REQUEST, "Invalid request"
        );

        assertThat(exception.getErrorCode()).isEqualTo("VAL_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testErrorCodeMapping_storage() {
        SifBrokerException exception = new SifBrokerException(
            "STORAGE_001", HttpStatus.INTERNAL_SERVER_ERROR, "Storage error"
        );

        assertThat(exception.getErrorCode()).isEqualTo("STORAGE_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void testErrorCodeMapping_serviceUnavailable() {
        SifBrokerException exception = new SifBrokerException(
            "SERVICE_001", HttpStatus.SERVICE_UNAVAILABLE, "Provider unavailable"
        );

        assertThat(exception.getErrorCode()).isEqualTo("SERVICE_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void testExceptionMessage_isPreserved() {
        String message = "This is a detailed error message with context";
        SifBrokerException exception = new SifBrokerException(
            "TEST_006", HttpStatus.BAD_REQUEST, message
        );

        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void testCauseChain_isPreserved() {
        Throwable rootCause = new IllegalArgumentException("Root cause");
        Throwable intermediateCause = new RuntimeException("Intermediate cause", rootCause);
        
        SifBrokerException exception = new SifBrokerException(
            "TEST_007", HttpStatus.INTERNAL_SERVER_ERROR, "Top level error", intermediateCause
        );

        assertThat(exception.getCause()).isEqualTo(intermediateCause);
        assertThat(exception.getCause().getCause()).isEqualTo(rootCause);
    }

    @Test
    void testContextInformation_forDebugging() {
        String requestId = "req-abc-123";
        String environmentId = "env-xyz-789";
        
        SifBrokerException exception = new SifBrokerException(
            "TEST_008", 
            HttpStatus.FORBIDDEN, 
            "Access denied for environment",
            requestId,
            environmentId
        );

        // Verify context information is available for debugging
        assertThat(exception.getRequestId()).isEqualTo(requestId);
        assertThat(exception.getEnvironmentId()).isEqualTo(environmentId);
        
        // This information can be used in logging or error responses
        String debugInfo = String.format(
            "Error: %s, Request: %s, Environment: %s",
            exception.getErrorCode(),
            exception.getRequestId(),
            exception.getEnvironmentId()
        );
        assertThat(debugInfo).contains(requestId);
        assertThat(debugInfo).contains(environmentId);
    }

    @Test
    void testHttpStatusCodes_coverAllCategories() {
        // Test various HTTP status codes
        assertThat(new SifBrokerException("AUTH_001", HttpStatus.UNAUTHORIZED, "msg").getHttpStatus())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        
        assertThat(new SifBrokerException("AUTHZ_001", HttpStatus.FORBIDDEN, "msg").getHttpStatus())
            .isEqualTo(HttpStatus.FORBIDDEN);
        
        assertThat(new SifBrokerException("ROUTE_001", HttpStatus.NOT_FOUND, "msg").getHttpStatus())
            .isEqualTo(HttpStatus.NOT_FOUND);
        
        assertThat(new SifBrokerException("VAL_001", HttpStatus.BAD_REQUEST, "msg").getHttpStatus())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        
        assertThat(new SifBrokerException("STORAGE_001", HttpStatus.INTERNAL_SERVER_ERROR, "msg").getHttpStatus())
            .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        
        assertThat(new SifBrokerException("SERVICE_001", HttpStatus.SERVICE_UNAVAILABLE, "msg").getHttpStatus())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void testGetters_lombokGenerated() {
        String errorCode = "TEST_009";
        HttpStatus httpStatus = HttpStatus.BAD_REQUEST;
        String message = "Test message";
        String requestId = "req-123";
        String environmentId = "env-456";

        SifBrokerException exception = new SifBrokerException(
            errorCode, httpStatus, message, requestId, environmentId
        );

        // Verify all getters work correctly (Lombok @Getter)
        assertThat(exception.getErrorCode()).isNotNull();
        assertThat(exception.getHttpStatus()).isNotNull();
        assertThat(exception.getMessage()).isNotNull();
        assertThat(exception.getRequestId()).isNotNull();
        assertThat(exception.getEnvironmentId()).isNotNull();
    }
}
