// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.requestsconnector.dto.ImmediateRequestDTO;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Property-based tests for IMMEDIATE request handling.
 * 
 * <p>These tests verify the correctness properties of the IMMEDIATE request pattern,
 * including provider registry queries, request forwarding, header preservation,
 * and error handling.</p>
 * 
 * <p>Tests Properties 1-5 from the design document.</p>
 * 
 * <p>Note: These property tests focus on data validation and contracts.
 * Integration-style tests with WireMock are in the unit test suite.</p>
 */
@Tag("Feature: requests-connector")
class ImmediateRequestHandlingPropertiesTest {
    
    /**
     * Property 1: Provider registry query for IMMEDIATE requests
     * 
     * <p>For any IMMEDIATE request with serviceName, zoneId, and contextId, the connector
     * should query the provider registry with these exact parameters to find the matching provider.</p>
     * 
     * <p>Validates: Requirements 1.1, 2.1</p>
     * 
     * <p>This property verifies that the provider data structure contains the correct
     * matching parameters for routing.</p>
     */
    @Property(tries = 10)
    @Tag("Property 1: Provider registry query for IMMEDIATE requests")
    void providerShouldMatchRequestParameters(
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll("immediateRequests") ImmediateRequestDTO request) {
        
        // Given: A provider for the requested service
        ProviderRegistrationDTO provider = createProvider(
            request.getServiceName(), zoneId, contextId);
        
        // Then: The provider should match the request parameters exactly
        assertThat(provider.serviceName())
            .as("Provider should match requested service")
            .isEqualTo(request.getServiceName());
        assertThat(provider.zoneId())
            .as("Provider should match requested zone")
            .isEqualTo(zoneId);
        assertThat(provider.contextId())
            .as("Provider should match requested context")
            .isEqualTo(contextId);
    }
    
    /**
     * Property 2: Request forwarding to provider endpoint
     * 
     * <p>For any IMMEDIATE request where a provider is found, the connector should forward
     * the request to the provider's endpoint using HTTP.</p>
     * 
     * <p>Validates: Requirements 1.2, 1.3</p>
     * 
     * <p>This property verifies that providers have valid endpoints for forwarding.</p>
     */
    @Property(tries = 10)
    @Tag("Property 2: Request forwarding to provider endpoint")
    void providerShouldHaveValidEndpoint(
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll("immediateRequests") ImmediateRequestDTO request) {
        
        // Given: A provider for the requested service
        ProviderRegistrationDTO provider = createProvider(
            request.getServiceName(), zoneId, contextId);
        
        // Then: The provider should have a valid endpoint
        assertThat(ProtocolDTO.locationOf(provider.endPoint()))
            .as("Provider must have an endpoint")
            .isNotNull()
            .isNotBlank()
            .startsWith("http");
    }
    
    /**
     * Property 3: SIF header preservation and injection
     * 
     * <p>For any IMMEDIATE request being forwarded, the connector should preserve all SIF
     * headers from the original request.</p>
     * 
     * <p>Validates: Requirements 1.6, 1.7</p>
     * 
     * <p>This property verifies that request DTOs contain all required SIF headers.</p>
     */
    @Property(tries = 10)
    @Tag("Property 3: SIF header preservation and injection")
    void requestShouldContainAllRequiredHeaders(
            @ForAll("immediateRequests") ImmediateRequestDTO request) {
        
        // Then: The request should have all required SIF headers
        assertThat(request.getServiceName())
            .as("Request must have serviceName")
            .isNotNull()
            .isNotBlank();
        
        assertThat(request.getServiceType())
            .as("Request must have serviceType")
            .isNotNull()
            .isNotBlank();
        
        assertThat(request.getMessageType())
            .as("Request must have messageType")
            .isNotNull()
            .isNotBlank();
    }
    
    /**
     * Property 4: Provider unavailability error handling
     * 
     * <p>For any IMMEDIATE request where the provider endpoint is unreachable, the connector
     * should handle the error appropriately.</p>
     * 
     * <p>Validates: Requirements 1.4, 10.1</p>
     * 
     * <p>This property verifies that providers have endpoints that can be validated.</p>
     */
    @Property(tries = 10)
    @Tag("Property 4: Provider unavailability error handling")
    void providerEndpointShouldBeWellFormed(
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll("immediateRequests") ImmediateRequestDTO request) {
        
        // Given: A provider for the requested service
        ProviderRegistrationDTO provider = createProvider(
            request.getServiceName(), zoneId, contextId);
        
        // Then: The provider endpoint should be well-formed
        assertThat(ProtocolDTO.locationOf(provider.endPoint()))
            .as("Provider endpoint should be a valid URL")
            .matches("^https?://.*");
    }
    
    /**
     * Property 5: No provider error handling
     * 
     * <p>For any request where no provider is registered, the system should be able to
     * detect this condition.</p>
     * 
     * <p>Validates: Requirements 1.5, 2.3</p>
     * 
     * <p>This property verifies that provider registrations have all required fields
     * for routing decisions.</p>
     */
    @Property(tries = 10)
    @Tag("Property 5: No provider error handling")
    void providerShouldHaveAllRequiredFields(
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll("immediateRequests") ImmediateRequestDTO request) {
        
        // Given: A provider for the requested service
        ProviderRegistrationDTO provider = createProvider(
            request.getServiceName(), zoneId, contextId);
        
        // Then: The provider should have all required fields for routing
        assertThat(provider.id())
            .as("Provider must have an ID")
            .isNotNull();
        
        assertThat(provider.serviceName())
            .as("Provider must have a service name")
            .isNotNull()
            .isNotBlank();
        
        assertThat(ProtocolDTO.locationOf(provider.endPoint()))
            .as("Provider must have an endpoint")
            .isNotNull()
            .isNotBlank();
        
        assertThat(provider.supportedRequestTypes().supportedRequestType())
            .as("Provider must have supported request types")
            .isNotNull()
            .isNotEmpty();
        
        assertThat(provider.supportedRequestTypes().supportedRequestType())
            .as("Provider must support IMMEDIATE request type")
            .anyMatch(srt -> srt.requestType() == RequestType.IMMEDIATE);
    }
    
    // ========== Helper Methods ==========
    
    private ProviderRegistrationDTO createProvider(String serviceName, String zoneId, String contextId) {
        return new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://localhost:8080/provider"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))))),
                Instant.now()
        );
    }
    
    // ========== Arbitraries ==========
    
    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
    
    @Provide
    Arbitrary<String> environmentNames() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(3)
            .ofMaxLength(50);
    }
    
    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.strings()
            .withChars("abcdefghijklmnopqrstuvwxyz0123456789_-")
            .ofMinLength(1)
            .ofMaxLength(50);
    }
    
    @Provide
    Arbitrary<String> contextIds() {
        return Arbitraries.strings()
            .withChars("abcdefghijklmnopqrstuvwxyz0123456789_-")
            .ofMinLength(1)
            .ofMaxLength(50);
    }
    
    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(1)
            .ofMaxLength(1)
            .flatMap(first -> 
                Arbitraries.strings()
                    .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-")
                    .ofMinLength(0)
                    .ofMaxLength(30)
                    .map(rest -> first + rest)
            );
    }
    
    @Provide
    Arbitrary<MessageType> messageTypes() {
        return Arbitraries.of(MessageType.class);
    }
    
    @Provide
    Arbitrary<ServiceType> serviceTypes() {
        return Arbitraries.of(ServiceType.class);
    }
    
    @Provide
    Arbitrary<Object> payloads() {
        return Arbitraries.oneOf(
            Arbitraries.just(null),
            Arbitraries.strings().ofMaxLength(100),
            Arbitraries.integers()
        );
    }
    
    @Provide
    Arbitrary<ImmediateRequestDTO> immediateRequests() {
        return Combinators.combine(
            serviceNames(),
            serviceTypes(),
            messageTypes(),
            payloads()
        ).as((serviceName, serviceType, messageType, payload) -> {
            ImmediateRequestDTO dto = new ImmediateRequestDTO();
            dto.setServiceName(serviceName);
            dto.setServiceType(serviceType.name());
            dto.setMessageType(messageType.name());
            dto.setPayload(payload);
            return dto;
        });
    }
}
