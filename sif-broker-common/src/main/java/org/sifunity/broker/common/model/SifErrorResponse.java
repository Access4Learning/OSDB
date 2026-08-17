// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.sifunity.broker.common.exception.SifBrokerException;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a standardized error response conforming to SIF 3.7 {@code errorCommon} schema.
 * <p>
 * This class provides a consistent error format across all SIF Unity Broker microservices.
 * It includes required fields ({@code id}, {@code code}, {@code subCode}, {@code message},
 * {@code scope}) and optional fields ({@code description}, {@code field},
 * {@code stackTrace}) for additional context.
 * </p>
 * <p>
 * The class uses Jackson annotations to ensure proper JSON serialization that matches
 * the SIF 3.7 error response format. Null fields are excluded from the JSON output.
 * </p>
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Create error response from exception
 * try {
 *     // ... some operation
 * } catch (SifBrokerException ex) {
 *     SifErrorResponse errorResponse = SifErrorResponse.fromException(ex);
 *     return ResponseEntity
 *         .status(ex.getHttpStatus())
 *         .body(errorResponse);
 * }
 * 
 * // Create custom error response
 * SifErrorResponse error = new SifErrorResponse(
 *     UUID.randomUUID().toString(),
 *     400,
 *     "VAL_001",
 *     "Invalid request format",
 *     "The request body must be valid JSON",
 *     "BROKER",
 *     Instant.now(),  // internal only, not serialized to wire
 *     null,
 *     null
 * );
 * }</pre>
 *
 * @see SifBrokerException
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SifErrorResponse(
    /**
     * UUID uniquely identifying this error instance.
     */
    String id,
    
    /**
     * The HTTP status code integer (e.g., 400, 404, 500).
     */
    int code,
    
    /**
     * The internal error code identifying the specific error type (e.g., "AUTH_001", "VAL_002").
     */
    String subCode,
    
    /**
     * A brief, human-readable message describing the error.
     */
    String message,
    
    /**
     * A detailed description of the error (optional).
     */
    String description,
    
    /**
     * The scope of the error, typically "BROKER" for broker-generated errors.
     */
    String scope,
    
    /**
     * The timestamp when the error occurred.
     * Internal implementation detail — not part of the SIF 3.7 errorCommon schema.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    Instant timestamp,
    
    /**
     * The specific field that caused the error, used for validation errors (optional).
     * Internal implementation detail — not part of the SIF 3.7 errorCommon schema.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    String field,
    
    /**
     * The stack trace for debugging purposes, typically only included in development mode (optional).
     */
    String stackTrace
) {
    /** Ensure timestamp is not serialized in API responses (not part of SIF 3.7 errorCommon). */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Override
    public Instant timestamp() { return timestamp; }

    /** Ensure field is not serialized in API responses (not part of SIF 3.7 errorCommon). */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Override
    public String field() { return field; }

    /**
     * Creates a SifErrorResponse from a SifBrokerException.
     * <p>
     * This factory method extracts the HTTP status code, internal error code, and message
     * from the exception and creates a properly formatted error response conforming to
     * SIF 3.7 {@code errorCommon}. A UUID is generated for the {@code id} field, the scope
     * is set to "BROKER", and the timestamp is set to the current time.
     * </p>
     *
     * @param ex the SifBrokerException to convert
     * @return a SifErrorResponse containing the exception details
     */
    public static SifErrorResponse fromException(SifBrokerException ex) {
        return new SifErrorResponse(
            UUID.randomUUID().toString(),   // id
            ex.getHttpStatus().value(),     // code (int)
            ex.getErrorCode(),             // subCode (String)
            ex.getMessage(),
            null,  // description
            "BROKER",
            Instant.now(),
            null,  // field
            null   // stackTrace
        );
    }
}
