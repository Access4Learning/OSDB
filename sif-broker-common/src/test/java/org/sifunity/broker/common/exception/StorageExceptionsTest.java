// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for storage exception classes.
 * Tests StorageException and StorageUnavailableException.
 */
class StorageExceptionsTest {

    // StorageException Tests

    @Test
    void storageException_hasCorrectErrorCode() {
        StorageException exception = new StorageException("Failed to write to S3", new RuntimeException("Connection timeout"));

        assertThat(exception.getErrorCode()).isEqualTo("STORAGE_001");
    }

    @Test
    void storageException_hasInternalServerErrorStatus() {
        StorageException exception = new StorageException("Storage operation failed", new RuntimeException());

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void storageException_preservesMessage() {
        String message = "Failed to write message payload to storage backend";
        StorageException exception = new StorageException(message, new RuntimeException());

        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void storageException_preservesCause() {
        RuntimeException cause = new RuntimeException("S3 connection timeout");
        StorageException exception = new StorageException("Storage write failed", cause);

        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getCause().getMessage()).isEqualTo("S3 connection timeout");
    }

    @Test
    void storageException_extendsSifBrokerException() {
        StorageException exception = new StorageException("Test message", new RuntimeException());

        assertThat(exception).isInstanceOf(SifBrokerException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void storageException_withS3WriteFailure() {
        String message = "Failed to write message to S3 bucket: sif-broker-messages";
        Exception cause = new RuntimeException("Access denied");
        StorageException exception = new StorageException(message, cause);

        assertThat(exception.getMessage()).contains("S3 bucket");
        assertThat(exception.getCause().getMessage()).contains("Access denied");
        assertThat(exception.getErrorCode()).isEqualTo("STORAGE_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void storageException_withDatabaseFailure() {
        String message = "Failed to persist message metadata to database";
        Exception cause = new RuntimeException("Deadlock detected");
        StorageException exception = new StorageException(message, cause);

        assertThat(exception.getMessage()).contains("database");
        assertThat(exception.getCause().getMessage()).contains("Deadlock");
        assertThat(exception.getErrorCode()).isEqualTo("STORAGE_001");
    }

    @Test
    void storageException_withDataCorruption() {
        String message = "Data integrity check failed: checksum mismatch";
        Exception cause = new RuntimeException("Corrupted data");
        StorageException exception = new StorageException(message, cause);

        assertThat(exception.getMessage()).contains("integrity");
        assertThat(exception.getMessage()).contains("checksum");
    }

    // StorageUnavailableException Tests

    @Test
    void storageUnavailableException_hasCorrectErrorCode() {
        StorageUnavailableException exception = new StorageUnavailableException("S3 service is down");

        assertThat(exception.getErrorCode()).isEqualTo("STORAGE_002");
    }

    @Test
    void storageUnavailableException_hasServiceUnavailableStatus() {
        StorageUnavailableException exception = new StorageUnavailableException("Storage backend unreachable");

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void storageUnavailableException_preservesMessage() {
        String message = "Storage backend is temporarily unavailable";
        StorageUnavailableException exception = new StorageUnavailableException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void storageUnavailableException_extendsSifBrokerException() {
        StorageUnavailableException exception = new StorageUnavailableException("Test message");

        assertThat(exception).isInstanceOf(SifBrokerException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void storageUnavailableException_withS3Outage() {
        StorageUnavailableException exception = new StorageUnavailableException("S3 service is undergoing maintenance");

        assertThat(exception.getMessage()).contains("S3");
        assertThat(exception.getMessage()).contains("maintenance");
        assertThat(exception.getErrorCode()).isEqualTo("STORAGE_002");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void storageUnavailableException_withNetworkIssue() {
        StorageUnavailableException exception = new StorageUnavailableException("Cannot reach storage backend: network timeout");

        assertThat(exception.getMessage()).contains("network");
        assertThat(exception.getMessage()).contains("timeout");
    }

    @Test
    void storageUnavailableException_withQuotaExceeded() {
        StorageUnavailableException exception = new StorageUnavailableException("Storage quota exceeded: 1TB limit reached");

        assertThat(exception.getMessage()).contains("quota");
        assertThat(exception.getMessage()).contains("limit");
    }

    @Test
    void storageUnavailableException_withOverload() {
        StorageUnavailableException exception = new StorageUnavailableException("Storage backend is overloaded and rejecting requests");

        assertThat(exception.getMessage()).contains("overloaded");
        assertThat(exception.getMessage()).contains("rejecting");
    }

    // Cross-Exception Tests

    @Test
    void storageExceptions_haveDifferentHttpStatuses() {
        StorageException storageException = new StorageException("msg", new RuntimeException());
        StorageUnavailableException unavailableException = new StorageUnavailableException("msg");

        assertThat(storageException.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(unavailableException.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(storageException.getHttpStatus()).isNotEqualTo(unavailableException.getHttpStatus());
    }

    @Test
    void storageExceptions_haveUniqueErrorCodes() {
        StorageException storageException = new StorageException("msg", new RuntimeException());
        StorageUnavailableException unavailableException = new StorageUnavailableException("msg");

        assertThat(storageException.getErrorCode()).isEqualTo("STORAGE_001");
        assertThat(unavailableException.getErrorCode()).isEqualTo("STORAGE_002");
        assertThat(storageException.getErrorCode()).isNotEqualTo(unavailableException.getErrorCode());
    }

    @Test
    void allStorageExceptions_extendSifBrokerException() {
        StorageException storageException = new StorageException("msg", new RuntimeException());
        StorageUnavailableException unavailableException = new StorageUnavailableException("msg");

        assertThat(storageException).isInstanceOf(SifBrokerException.class);
        assertThat(unavailableException).isInstanceOf(SifBrokerException.class);
    }

    @Test
    void allStorageExceptions_areRuntimeExceptions() {
        StorageException storageException = new StorageException("msg", new RuntimeException());
        StorageUnavailableException unavailableException = new StorageUnavailableException("msg");

        assertThat(storageException).isInstanceOf(RuntimeException.class);
        assertThat(unavailableException).isInstanceOf(RuntimeException.class);
    }

    @Test
    void storageExceptions_canBeDistinguishedByErrorCode() {
        // Simulate catching a generic SifBrokerException and identifying the specific type
        SifBrokerException storageException = new StorageException("Storage error", new RuntimeException());
        SifBrokerException unavailableException = new StorageUnavailableException("Storage unavailable");

        // Can identify exception type by error code
        assertThat(storageException.getErrorCode()).startsWith("STORAGE_");
        assertThat(unavailableException.getErrorCode()).startsWith("STORAGE_");

        // Can distinguish between specific storage errors
        if (storageException.getErrorCode().equals("STORAGE_001")) {
            assertThat(storageException).isInstanceOf(StorageException.class);
        }
        if (unavailableException.getErrorCode().equals("STORAGE_002")) {
            assertThat(unavailableException).isInstanceOf(StorageUnavailableException.class);
        }
    }

    @Test
    void storageExceptions_distinguishBetweenPermanentAndTemporary() {
        // StorageException (500) indicates a permanent failure requiring investigation
        StorageException permanentFailure = new StorageException("Data corruption detected", new RuntimeException());

        // StorageUnavailableException (503) indicates temporary unavailability
        StorageUnavailableException temporaryFailure = new StorageUnavailableException("Storage backend is down");

        // Different HTTP status codes indicate retry strategy
        assertThat(permanentFailure.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(temporaryFailure.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // 500 errors should not be retried automatically
        // 503 errors may be retried after a delay
    }

    @Test
    void storageException_requiresCause() {
        // StorageException always includes a cause for debugging
        RuntimeException cause = new RuntimeException("Underlying storage error");
        StorageException exception = new StorageException("Storage operation failed", cause);

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void storageExceptions_provideContextForDebugging() {
        // Test that exceptions provide enough context to understand what went wrong
        StorageException storageException = new StorageException(
            "Failed to write message abc-123 to S3 bucket sif-broker-messages",
            new RuntimeException("Connection timeout after 30 seconds")
        );
        StorageUnavailableException unavailableException = new StorageUnavailableException(
            "S3 service is unavailable in region us-east-1"
        );

        // Storage exception provides operation details and cause
        assertThat(storageException.getMessage()).contains("abc-123");
        assertThat(storageException.getMessage()).contains("S3 bucket");
        assertThat(storageException.getCause().getMessage()).contains("timeout");

        // Unavailable exception provides service and location details
        assertThat(unavailableException.getMessage()).contains("S3");
        assertThat(unavailableException.getMessage()).contains("region");
    }

    @Test
    void storageExceptions_supportDetailedMessages() {
        String detailedStorageMessage = "Failed to write message payload to S3: bucket=sif-broker-messages, key=env-123/zone-default/context-default/StudentPersonal/msg-456.json, error=AccessDenied";
        String detailedUnavailableMessage = "Storage backend unavailable: S3 service returned 503 Service Unavailable, retry after 60 seconds";

        StorageException storageException = new StorageException(detailedStorageMessage, new RuntimeException());
        StorageUnavailableException unavailableException = new StorageUnavailableException(detailedUnavailableMessage);

        assertThat(storageException.getMessage()).contains("bucket=");
        assertThat(storageException.getMessage()).contains("key=");
        assertThat(storageException.getMessage()).contains("error=");

        assertThat(unavailableException.getMessage()).contains("503");
        assertThat(unavailableException.getMessage()).contains("retry after");
    }
}
