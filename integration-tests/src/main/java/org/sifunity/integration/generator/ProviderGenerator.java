// SPDX-License-Identifier: MIT
package org.sifunity.integration.generator;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.constant.MessageExchangePattern;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.integration.dto.ProviderRegistrationRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.integration.dto.ProviderRegistrationBody;
import org.sifunity.integration.dto.ProviderResponse;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.integration.dto.ProviderCollectionResponse;

/**
 * Generator for creating test provider registrations during integration tests.
 *
 * @since 1.0.0
 */
@Slf4j
public class ProviderGenerator {

    private static final Set<Operation> ALL_OPERATIONS = EnumSet.allOf(Operation.class);

    private final String environmentServiceUrl;
    private final RestTemplate restTemplate;

    public ProviderGenerator(String environmentServiceUrl) {
        this.environmentServiceUrl = environmentServiceUrl;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Registers a new provider with SIF 3.7 aligned fields.
     */
    public ProviderRegistrationDTO registerProvider(
        UUID environmentId,
        String serviceName,
        String zoneId,
        String contextId,
        String endpoint,
        ServiceType serviceType,
        String providerName,
        List<SupportedRequestType> supportedRequestTypes,
        String sessionToken,
        String sharedSecret
    ) {
        log.info("Registering provider: environmentId={}, serviceName={}, zoneId={}, contextId={}, endpoint={}, serviceType={}, providerName={}, supportedRequestTypes={}",
            environmentId, serviceName, zoneId, contextId, endpoint, serviceType, providerName, supportedRequestTypes);

        // D1: wrapped under `provider`, with endPoint/supportedRequestTypes nested. providerName is
        // NOT sent -- the broker has no column for it and derives it from the environment's
        // consumerName; sending it would be rejected as an unknown property.
        ProviderRegistrationRequest request = new ProviderRegistrationRequest(
            new ProviderRegistrationBody(
                environmentId,
                serviceName,
                serviceType,
                zoneId,
                contextId,
                ProtocolDTO.of(endpoint),
                SupportedRequestsDTO.of(supportedRequestTypes)
            )
        );

        String url = environmentServiceUrl + "/" + GlobalAdminConstants.GLOBAL_DOMAIN
            + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/providers/provider";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", generateAuthHeader(sessionToken, sharedSecret));
        headers.set("Content-Type", "application/json");

        HttpEntity<ProviderRegistrationRequest> entity = new HttpEntity<>(request, headers);

        // D1: the endpoint returns the `provider` envelope, not a bare DTO.
        ResponseEntity<ProviderResponse> responseEntity = restTemplate.exchange(
            url, HttpMethod.POST, entity, ProviderResponse.class);

        ProviderRegistrationDTO response = responseEntity.getBody() == null
            ? null
            : responseEntity.getBody().getProvider();

        log.info("Provider registered: id={}, environmentId={}, serviceName={}, endpoint={}",
            response.id(), response.environmentId(), response.serviceName(), ProtocolDTO.locationOf(response.endPoint()));

        return response;
    }

    /**
     * Legacy-compatible overload that converts MessageExchangePattern set to SIF 3.7 fields.
     * Each pattern becomes a request type entry with all four operations.
     * EVENT patterns are skipped (not a request type in SIF 3.7).
     */
    public ProviderRegistrationDTO registerProvider(
        UUID environmentId,
        String serviceName,
        String zoneId,
        String contextId,
        String endpoint,
        Set<MessageExchangePattern> supportedPatterns,
        String sessionToken,
        String sharedSecret
    ) {
        List<SupportedRequestType> supportedRequestTypes = new ArrayList<>();
        for (MessageExchangePattern pattern : supportedPatterns) {
            if (pattern == MessageExchangePattern.IMMEDIATE) {
                supportedRequestTypes.add(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(ALL_OPERATIONS)));
            } else if (pattern == MessageExchangePattern.DELAYED) {
                supportedRequestTypes.add(new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(ALL_OPERATIONS)));
            }
            // EVENT is not a request type — skip
        }
        if (supportedRequestTypes.isEmpty()) {
            supportedRequestTypes.add(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(ALL_OPERATIONS)));
        }

        return registerProvider(
            environmentId, serviceName, zoneId, contextId, endpoint,
            ServiceType.OBJECT, "TestProvider",
            supportedRequestTypes,
            sessionToken, sharedSecret
        );
    }

    private String generateAuthHeader(String sessionToken, String sharedSecret) {
        try {
            String timestamp = java.time.Instant.now().toString();
            String toSign = sessionToken + ":" + timestamp;
            byte[] secretBytes = sharedSecret.getBytes(StandardCharsets.UTF_8);

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretBytes, "HmacSHA256");
            mac.init(secretKeySpec);

            byte[] signatureBytes = mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(signatureBytes);

            return "SIF_HMACSHA256 " + Base64.getEncoder().encodeToString(
                (sessionToken + ":" + signature).getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate authentication header", e);
        }
    }

    public ProviderRegistrationDTO registerImmediateProvider(
        UUID environmentId, String serviceName, String zoneId, String contextId,
        String endpoint, String sessionToken, String sharedSecret
    ) {
        return registerProvider(environmentId, serviceName, zoneId, contextId, endpoint,
            Set.of(MessageExchangePattern.IMMEDIATE), sessionToken, sharedSecret);
    }

    public ProviderRegistrationDTO registerDelayedProvider(
        UUID environmentId, String serviceName, String zoneId, String contextId,
        String endpoint, String sessionToken, String sharedSecret
    ) {
        return registerProvider(environmentId, serviceName, zoneId, contextId, endpoint,
            Set.of(MessageExchangePattern.DELAYED), sessionToken, sharedSecret);
    }

    public ProviderRegistrationDTO registerEventProvider(
        UUID environmentId, String serviceName, String zoneId, String contextId,
        String endpoint, String sessionToken, String sharedSecret
    ) {
        return registerProvider(environmentId, serviceName, zoneId, contextId, endpoint,
            Set.of(MessageExchangePattern.EVENT), sessionToken, sharedSecret);
    }

    public ProviderRegistrationDTO registerImmediateAndDelayedProvider(
        UUID environmentId, String serviceName, String zoneId, String contextId,
        String endpoint, String sessionToken, String sharedSecret
    ) {
        return registerProvider(environmentId, serviceName, zoneId, contextId, endpoint,
            Set.of(MessageExchangePattern.IMMEDIATE, MessageExchangePattern.DELAYED),
            sessionToken, sharedSecret);
    }

    public ProviderRegistrationDTO registerProviderAllPatterns(
        UUID environmentId, String serviceName, String zoneId, String contextId,
        String endpoint, String sessionToken, String sharedSecret
    ) {
        return registerProvider(environmentId, serviceName, zoneId, contextId, endpoint,
            Set.of(MessageExchangePattern.IMMEDIATE, MessageExchangePattern.DELAYED, MessageExchangePattern.EVENT),
            sessionToken, sharedSecret);
    }

    public Optional<ProviderRegistrationDTO> lookupProvider(
        String serviceName, String zoneId, String contextId
    ) {
        String url = environmentServiceUrl + "/" + GlobalAdminConstants.GLOBAL_DOMAIN
            + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/providers/lookup"
            + "?serviceName=" + serviceName + "&zoneId=" + zoneId;
        if (contextId != null) {
            url += "&contextId=" + contextId;
        }
        try {
            // D1: /providers/lookup returns the `provider` envelope, not a bare DTO.
            ProviderResponse response = restTemplate.getForObject(url, ProviderResponse.class);
            return Optional.ofNullable(response).map(ProviderResponse::getProvider);
        } catch (Exception e) {
            // NB: this also swallows deserialization failures, so a shape mismatch here looks like
            // "no provider found" rather than an error. Keep the type above in step with the broker.
            return Optional.empty();
        }
    }

    public List<ProviderRegistrationDTO> listProviders(UUID environmentId) {
        String url = environmentServiceUrl + "/" + GlobalAdminConstants.GLOBAL_DOMAIN
            + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/providers?environmentId=" + environmentId;
        // D1: the collection comes back as {"providers":{"provider":[...]}}, not a bare array.
        ProviderCollectionResponse response = restTemplate.getForObject(url, ProviderCollectionResponse.class);
        return response != null ? response.providerList() : List.of();
    }

    public void deleteProvider(UUID providerId, String sessionToken, String sharedSecret) {
        String url = environmentServiceUrl + "/" + GlobalAdminConstants.GLOBAL_DOMAIN
            + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/providers/" + providerId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", generateAuthHeader(sessionToken, sharedSecret));
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
    }

    public void deleteAllForEnvironment(UUID environmentId, String sessionToken, String sharedSecret) {
        List<ProviderRegistrationDTO> providers = listProviders(environmentId);
        for (ProviderRegistrationDTO provider : providers) {
            deleteProvider(provider.id(), sessionToken, sharedSecret);
        }
    }
}
