// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import com.fasterxml.jackson.databind.JsonNode;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.config.BootstrapProperties;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;

/**
 * Integration tests for ADMIN role authorization enforcement.
 * 
 * <p>These tests validate the complete authorization flow with the AuthorizationFilter:
 * <ul>
 *   <li>ADMIN can create environments</li>
 *   <li>ADMIN can create ACL entries</li>
 *   <li>ADMIN can read all environments</li>
 *   <li>ADMIN can update any environment</li>
 *   <li>ADMIN can delete environments</li>
 *   <li>Non-ADMIN receives 403 when creating ACL</li>
 *   <li>Non-ADMIN receives 403 when creating environment</li>
 *   <li>Non-ADMIN receives 403 when deleting environment</li>
 *   <li>Non-ADMIN receives 403 when accessing other environment data</li>
 * </ul>
 * 
 * <p><strong>Requirements:</strong> 5.7, 6.3, 6.4, 6.5</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
class AdminAuthorizationIntegrationTest {

    private String envPath;
    private String aclPath;
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private BootstrapProperties bootstrapProperties;
    
    @Autowired
    private EnvironmentRepository environmentRepository;
    
    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private ZoneService zoneService;
    
    private String baseUrl;
    private String adminSessionToken;
    private String adminSharedSecret;
    private Environment adminEnvironment;
    private TestEntityHierarchy hierarchy;
    
    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        
        // Create hierarchy for test environments
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
        envPath = "/" + hierarchy.getDomainId() + "/" + hierarchy.getSolutionId() + "/environments";
        aclPath = "/" + hierarchy.getDomainId() + "/" + hierarchy.getSolutionId() + "/acl-entries";
        
        // Get ADMIN credentials from bootstrap configuration
        adminSessionToken = bootstrapProperties.getAdmin().getSessionToken();
        adminSharedSecret = bootstrapProperties.getAdmin().getSharedSecret();
        
        // Find the ADMIN environment created by bootstrap
        adminEnvironment = environmentRepository.findBySessionToken(adminSessionToken)
            .orElseThrow(() -> new IllegalStateException("ADMIN environment not found"));
        
        assertThat(adminEnvironment.getType()).isEqualTo(EnvironmentType.BROKERED);
        assertThat(adminEnvironment.getSolutionUuid()).isNotNull();
    }
    
    @org.junit.jupiter.api.AfterEach
    void cleanupTestData() {
        // Delete all non-admin environments created during tests
        environmentRepository.findAll().stream()
            .filter(env -> !("ADMIN".equals(env.getApplicationName())))
            .forEach(env -> {
                try {
                    environmentRepository.delete(env);
                } catch (Exception e) {
                    // Ignore deletion errors
                }
            });
        environmentRepository.flush();
    }
    
    // ========== Tests for ADMIN operations ==========
    
    @Test
    void adminCanCreateEnvironment() {
        // Given
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestConsumer-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        
        HttpHeaders headers = createAuthHeaders(adminSessionToken, adminSharedSecret);
        HttpEntity<EnvironmentRegistrationRequest> entity = new HttpEntity<>(request, headers);
        
        // When - deserialize as JsonNode because @JsonUnwrapped on the record
        // flattens environment fields into the top level, which Jackson cannot
        // round-trip back into SifEnvironmentRegistrationResponse
        ResponseEntity<JsonNode> response = restTemplate.exchange(
            baseUrl + envPath + "/environment",
            HttpMethod.POST,
            entity,
            JsonNode.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        // Response is wrapped under "environment" key
        JsonNode envNode = body.has("environment") ? body.get("environment") : body;
        assertThat(envNode.has("id")).isTrue();
        assertThat(envNode.get("id").asText()).isNotEmpty();
        assertThat(envNode.get("type").asText()).isEqualTo("BROKERED");
    }
    
    @Test
    void adminCanReadAnyEnvironment() {
        // Given: Create a consumer environment
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestConsumer-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse created = environmentService.registerEnvironment(hierarchy.getDomainId(), request);
        
        HttpHeaders headers = createAuthHeaders(adminSessionToken, adminSharedSecret);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        // When: ADMIN reads the consumer environment
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + envPath + "/" + created.environment().id(),
            HttpMethod.GET,
            entity,
            String.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(created.environment().id().toString());
    }
    
    @Test
    void adminCanUpdateAnyEnvironment() {
        // Given: Create a consumer environment
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestConsumer-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse created = environmentService.registerEnvironment(hierarchy.getDomainId(), request);
        
        HttpHeaders headers = createAuthHeaders(adminSessionToken, adminSharedSecret);
        String updatePayload = "{\"consumerName\":\"UpdatedName\"}";
        HttpEntity<String> entity = new HttpEntity<>(updatePayload, headers);
        
        // When: ADMIN updates the consumer environment
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + envPath + "/" + created.environment().id(),
            HttpMethod.PUT,
            entity,
            String.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NO_CONTENT);
    }
    
    @Test
    void adminCanDeleteEnvironment() {
        // Given: Create a consumer environment
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestConsumer-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse created = environmentService.registerEnvironment(hierarchy.getDomainId(), request);
        
        HttpHeaders headers = createAuthHeaders(adminSessionToken, adminSharedSecret);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        // When: ADMIN deletes the consumer environment
        ResponseEntity<Void> response = restTemplate.exchange(
            baseUrl + envPath + "/" + created.environment().id(),
            HttpMethod.DELETE,
            entity,
            Void.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NO_CONTENT);
    }
    
    @Test
    void adminCanCreateAclEntry() {
        // Given: Create a consumer environment first
        EnvironmentRegistrationRequest envRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestConsumer-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse consumer = environmentService.registerEnvironment(hierarchy.getDomainId(), envRequest);
        
        String aclPayload = String.format(
            "{\"environmentId\":\"%s\",\"serviceName\":\"TestService\",\"zoneId\":\"%s\",\"contextId\":\"DEFAULT\",\"accessType\":\"QUERY\",\"accessLevel\":\"APPROVED\"}",
            consumer.environment().id(), hierarchy.getZoneId()
        );
        
        HttpHeaders headers = createAuthHeaders(adminSessionToken, adminSharedSecret);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(aclPayload, headers);
        
        // When: ADMIN creates ACL entry
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + aclPath,
            HttpMethod.POST,
            entity,
            String.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        assertThat(response.getBody()).contains("TestService");
    }
    
    // ========== Tests for non-ADMIN restrictions ==========
    
    @Test
    void consumerCannotCreateEnvironment() {
        // Given: Create a consumer environment
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestConsumer-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse consumer = environmentService.registerEnvironment(hierarchy.getDomainId(), request);
        
        // And: Another environment creation request
        EnvironmentRegistrationRequest newRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AnotherConsumer-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        
        HttpHeaders headers = createAuthHeaders(consumer.environment().sessionToken(), consumer.sharedSecret());
        HttpEntity<EnvironmentRegistrationRequest> entity = new HttpEntity<>(newRequest, headers);
        
        // When: Consumer tries to create environment
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + envPath + "/environment",
            HttpMethod.POST,
            entity,
            String.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Insufficient permissions");
    }
    
    @Test
    void consumerCannotCreateAclEntry() {
        // Given: Create a consumer environment
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestConsumer-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse consumer = environmentService.registerEnvironment(hierarchy.getDomainId(), request);
        
        String aclPayload = String.format(
            "{\"environmentId\":\"%s\",\"serviceName\":\"TestService\",\"zoneId\":\"%s\",\"contextId\":\"DEFAULT\",\"accessType\":\"QUERY\",\"accessLevel\":\"APPROVED\"}",
            consumer.environment().id(), hierarchy.getZoneId()
        );
        
        HttpHeaders headers = createAuthHeaders(consumer.environment().sessionToken(), consumer.sharedSecret());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(aclPayload, headers);
        
        // When: Consumer tries to create ACL entry
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + aclPath,
            HttpMethod.POST,
            entity,
            String.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Insufficient permissions");
    }
    
    @Test
    void consumerCannotDeleteEnvironment() {
        // Given: Create two consumer environments
        EnvironmentRegistrationRequest request1 = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestConsumer1-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse consumer1 = environmentService.registerEnvironment(hierarchy.getDomainId(), request1);
        
        EnvironmentRegistrationRequest request2 = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestConsumer2-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse consumer2 = environmentService.registerEnvironment(hierarchy.getDomainId(), request2);
        
        HttpHeaders headers = createAuthHeaders(consumer1.environment().sessionToken(), consumer1.sharedSecret());
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        // When: Consumer1 tries to delete Consumer2
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + envPath + "/" + consumer2.environment().id(),
            HttpMethod.DELETE,
            entity,
            String.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Insufficient permissions");
    }
    
    @Test
    void consumerCannotReadOtherEnvironment() {
        // Given: Create two consumer environments
        EnvironmentRegistrationRequest request1 = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestConsumer1-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse consumer1 = environmentService.registerEnvironment(hierarchy.getDomainId(), request1);
        
        EnvironmentRegistrationRequest request2 = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestConsumer2-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse consumer2 = environmentService.registerEnvironment(hierarchy.getDomainId(), request2);
        
        HttpHeaders headers = createAuthHeaders(consumer1.environment().sessionToken(), consumer1.sharedSecret());
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        // When: Consumer1 tries to read Consumer2
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + envPath + "/" + consumer2.environment().id(),
            HttpMethod.GET,
            entity,
            String.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Insufficient permissions");
    }
    
    @Test
    void consumerCanReadOwnEnvironment() {
        // Given: Create a consumer environment
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestConsumer-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse consumer = environmentService.registerEnvironment(hierarchy.getDomainId(), request);
        
        HttpHeaders headers = createAuthHeaders(consumer.environment().sessionToken(), consumer.sharedSecret());
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        // When: Consumer reads own environment
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + envPath + "/" + consumer.environment().id(),
            HttpMethod.GET,
            entity,
            String.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(consumer.environment().id().toString());
    }
    
    @Test
    void consumerCanUpdateOwnEnvironment() {
        // Given: Create a consumer environment
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestConsumer-" + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        SifEnvironmentRegistrationResponse consumer = environmentService.registerEnvironment(hierarchy.getDomainId(), request);
        
        HttpHeaders headers = createAuthHeaders(consumer.environment().sessionToken(), consumer.sharedSecret());
        String updatePayload = "{\"consumerName\":\"UpdatedName\"}";
        HttpEntity<String> entity = new HttpEntity<>(updatePayload, headers);
        
        // When: Consumer updates own environment
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + envPath + "/" + consumer.environment().id(),
            HttpMethod.PUT,
            entity,
            String.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NO_CONTENT);
    }
    
    // ========== Tests for authentication errors ==========
    
    @Test
    void requestWithoutAuthorizationHeader_Returns401() {
        // Given
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        // When
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + envPath,
            HttpMethod.GET,
            entity,
            String.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Authentication required");
    }
    
    @Test
    void requestWithInvalidSessionToken_Returns401() {
        // Given
        HttpHeaders headers = createAuthHeaders("invalid-token", "invalid-secret");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        // When
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + envPath,
            HttpMethod.GET,
            entity,
            String.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid credentials");
    }
    
    // ========== Helper methods ==========
    
    private HttpHeaders createAuthHeaders(String sessionToken, String sharedSecret) {
        HttpHeaders headers = new HttpHeaders();
        String credentials = sessionToken + ":dummy-signature";
        String encoded = java.util.Base64.getEncoder().encodeToString(
                credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        headers.set("Authorization", "SIF_HMACSHA256 " + encoded);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}



