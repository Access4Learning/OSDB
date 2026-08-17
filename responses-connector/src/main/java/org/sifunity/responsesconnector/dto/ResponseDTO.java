// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Data Transfer Object for provider responses.
 * <p>
 * This DTO represents a response from a provider application that needs to be
 * routed to a consumer queue. It contains the service metadata and the response payload.
 * </p>
 * <p>
 * The DTO uses Bean Validation annotations to ensure all required fields are present
 * before processing the response.
 * </p>
 * 
 * <p>Example usage:
 * <pre>{@code
 * ResponseDTO response = ResponseDTO.builder()
 *     .serviceName("StudentPersonal")
 *     .serviceType("OBJECT")
 *     .payload(studentData)
 *     .build();
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseDTO {
    
    /**
     * Name of the service this response is for (e.g., "StudentPersonal").
     * This field is required and cannot be blank.
     */
    @NotBlank(message = "Service name is required")
    private String serviceName;
    
    /**
     * Type of service (e.g., "OBJECT", "FUNCTION", "UTILITY").
     * This field is required and cannot be blank.
     */
    @NotBlank(message = "Service type is required")
    private String serviceType;
    
    /**
     * The response payload containing the actual data.
     * This field is required and cannot be null.
     * The payload can be any valid JSON object.
     */
    @NotNull(message = "Payload is required")
    private Object payload;
    
    /**
     * Optional passthrough headers from the provider's response.
     * Used to carry SIF navigation headers (navigationPage, navigationLastPage, etc.)
     * through the response routing pipeline to the consumer's queue.
     */
    private java.util.Map<String, String> headers;
}
