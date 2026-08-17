// SPDX-License-Identifier: MIT
package org.sifunity.integration.generator;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.integration.config.AdminCredentialsConfig;
import org.sifunity.integration.dto.EnvironmentRegistrationRequest;
import org.sifunity.integration.dto.EnvironmentRegistrationResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.sifunity.integration.dto.EnvironmentRegistrationBody;
import org.sifunity.integration.dto.EnvironmentRegistrationEnvelope;

/**
 * Generator for creating test environments during integration tests.
 * 
 * <p>This class provides utilities for:
 * <ul>
 *   <li>Creating environments via the Environment Service API</li>
 *   <li>Generating SIFHMACSHA256 authentication headers</li>
 *   <li>Managing test environment lifecycle</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>
 * EnvironmentGenerator generator = new EnvironmentGenerator(environmentServiceUrl);
 * 
 * // Create a consumer environment
 * EnvironmentRegistrationResponse consumer = generator.createEnvironment(
 *     "test-consumer",
 *     EnvironmentType.BROKERED,
 *     "test-zone"
 * );
 * 
 * // Generate auth header for requests
 * String authHeader = generator.generateAuthHeader(
 *     consumer.sessionToken(),
 *     consumer.sharedSecret(),
 *     Instant.now()
 * );
 * 
 * // Delete environment after test
 * generator.deleteEnvironment(consumer.id());
 * </pre>
 * 
 * @since 1.0.0
 */
@Slf4j
public class EnvironmentGenerator {
    
    private final String environmentServiceUrl;
    private final RestTemplate restTemplate;
    private final AdminCredentialsConfig adminConfig;
    
    /**
     * Creates a new environment generator.
     * 
     * @param environmentServiceUrl the base URL of the Environment Service
     */
    public EnvironmentGenerator(String environmentServiceUrl) {
        this.environmentServiceUrl = environmentServiceUrl;
        this.restTemplate = new RestTemplate();
        this.adminConfig = new AdminCredentialsConfig();
    }
    
    /**
     * Creates a new environment with the specified parameters.
     * Uses ADMIN credentials for authentication as only ADMIN can create environments.
     * 
     * @param applicationName the application name (must be unique)
     * @param type the environment type
     * @param defaultZoneId the default zone ID
     * @return the registration response with credentials
     */
    public EnvironmentRegistrationResponse createEnvironment(
        String applicationName,
        EnvironmentType type,
        String defaultZoneId
    ) {
        log.info("Creating environment: {} (type: {}, zone: {})",
            applicationName, type, defaultZoneId);
        
        // D2: wrapped under `environment`, applicationName -> consumerName, and defaultZoneId ->
        // defaultZone{id}. DefaultZone.of() keeps a null zone as an ABSENT key rather than
        // {"id": null}, which is D3.
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(
            new EnvironmentRegistrationBody(
                applicationName,
                type,
                EnvironmentRegistrationBody.DefaultZone.of(defaultZoneId),
                null
            )
        );
        
        String url = environmentServiceUrl + "/" + GlobalAdminConstants.GLOBAL_DOMAIN 
            + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/environments/environment";
        
        // Create headers with ADMIN authentication
        Instant timestamp = Instant.now();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", generateAuthHeader(
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret(),
            timestamp
        ));
        headers.set("timestamp", timestamp.toString());
        headers.set("Content-Type", "application/json");
        
        HttpEntity<EnvironmentRegistrationRequest> entity = new HttpEntity<>(request, headers);
        
        // D2: the endpoint returns the `environment` envelope, not a flat body.
        ResponseEntity<EnvironmentRegistrationEnvelope> response = restTemplate.exchange(
            url,
            HttpMethod.POST,
            entity,
            EnvironmentRegistrationEnvelope.class
        );
        
        EnvironmentRegistrationResponse responseBody = response.getBody() == null
            ? null
            : response.getBody().getEnvironment();
        
        log.info("Environment created: id={}, sessionToken={}", 
            responseBody.getId(), responseBody.getSessionToken());
        
        return responseBody;
    }
    
    /**
     * Creates a consumer environment with default settings.
     * 
     * @param applicationName the application name
     * @return the registration response
     */
    public EnvironmentRegistrationResponse createConsumer(String applicationName) {
        return createEnvironment(
            applicationName,
            EnvironmentType.BROKERED,
            "test-zone"
        );
    }
    
    /**
     * Creates a provider environment with default settings.
     * 
     * @param applicationName the application name
     * @return the registration response
     */
    public EnvironmentRegistrationResponse createProvider(String applicationName) {
        return createEnvironment(
            applicationName,
            EnvironmentType.BROKERED,
            "test-zone"
        );
    }
    
    /**
     * Creates a broker environment with default settings.
     * 
     * @param applicationName the application name
     * @return the registration response
     */
    public EnvironmentRegistrationResponse createBroker(String applicationName) {
        return createEnvironment(
            applicationName,
            EnvironmentType.BROKERED,
            "test-zone"
        );
    }
    
    /**
     * Deletes an environment by ID.
     * Uses ADMIN credentials for authentication as only ADMIN can delete environments.
     * 
     * @param environmentId the environment ID
     */
    public void deleteEnvironment(UUID environmentId) {
        log.info("Deleting environment: {}", environmentId);
        
        String url = environmentServiceUrl + "/" + GlobalAdminConstants.GLOBAL_DOMAIN 
            + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/environments/" + environmentId;
        
        // Create headers with ADMIN authentication
        Instant timestamp = Instant.now();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", generateAuthHeader(
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret(),
            timestamp
        ));
        headers.set("timestamp", timestamp.toString());
        
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
        
        log.info("Environment deleted: {}", environmentId);
    }
    
    /**
     * Generates a SIFHMACSHA256 authentication header.
     * 
     * <p>The header format is:
     * <pre>SIF_HMACSHA256 {sessionToken}:{timestamp}:{hmac}</pre>
     * 
     * <p>The HMAC is computed as:
     * <pre>HMAC-SHA256(sessionToken + ":" + timestamp, sharedSecret)</pre>
     * 
     * @param sessionToken the session token
     * @param sharedSecret the shared secret (Base64 encoded)
     * @param timestamp the request timestamp
     * @return the Authorization header value
     */
    public String generateAuthHeader(String sessionToken, String sharedSecret, Instant timestamp) {
        try {
            // Format timestamp as ISO-8601
            String timestampStr = timestamp.toString();
            
            // Compute HMAC
            String toSign = sessionToken + ":" + timestampStr;
            String hmac = computeHmacSha256(toSign, sharedSecret);
            
            // Build header in Base64-encoded format expected by AuthorizationFilter/SifAuthenticationUtils
            String header = "SIF_HMACSHA256 " + Base64.getEncoder().encodeToString((sessionToken + ":" + hmac).getBytes(StandardCharsets.UTF_8));
            
            log.debug("Generated auth header for sessionToken: {}", sessionToken);
            
            return header;
            
        } catch (Exception e) {
            log.error("Failed to generate auth header", e);
            throw new RuntimeException("Failed to generate auth header", e);
        }
    }
    
    /**
     * Generates a SIFHMACSHA256 authentication header using the current time.
     * 
     * @param sessionToken the session token
     * @param sharedSecret the shared secret
     * @return the Authorization header value
     */
    public String generateAuthHeader(String sessionToken, String sharedSecret) {
        return generateAuthHeader(sessionToken, sharedSecret, Instant.now());
    }
    
    /**
     * Generates a SIFHMACSHA256 authentication header from a registration response.
     * 
     * @param environment the environment registration response
     * @return the Authorization header value
     */
    public String generateAuthHeader(EnvironmentRegistrationResponse environment) {
        return generateAuthHeader(
            environment.getSessionToken(),
            environment.getSharedSecret(),
            Instant.now()
        );
    }
    
    /**
     * Generates a SIFHMACSHA256 authentication header from a registration response
     * with a specific timestamp.
     * 
     * @param environment the environment registration response
     * @param timestamp the request timestamp
     * @return the Authorization header value
     */
    public String generateAuthHeader(EnvironmentRegistrationResponse environment, Instant timestamp) {
        return generateAuthHeader(
            environment.getSessionToken(),
            environment.getSharedSecret(),
            timestamp
        );
    }
    
    /**
     * Computes HMAC-SHA256 of the input using the secret.
     * 
     * @param input the input to sign
     * @param secret the secret key (Base64 encoded)
     * @return the HMAC as a Base64 encoded string
     */
    private String computeHmacSha256(String input, String secret) {
        try {
            // Use raw UTF-8 bytes of the secret (matches SifAuthenticationUtils.computeHmac())
            byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
            
            // Create HMAC-SHA256 instance
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretBytes, "HmacSHA256");
            mac.init(secretKeySpec);
            
            // Compute HMAC
            byte[] hmacBytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            
            // Encode as Base64
            return Base64.getEncoder().encodeToString(hmacBytes);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256", e);
        }
    }
    
    /**
     * Generates a unique application name for testing.
     * 
     * @param prefix the name prefix
     * @return a unique application name
     */
    public String generateUniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
