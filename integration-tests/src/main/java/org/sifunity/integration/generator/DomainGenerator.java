// SPDX-License-Identifier: MIT
package org.sifunity.integration.generator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.integration.config.AdminCredentialsConfig;
import org.sifunity.integration.dto.DomainCreateRequest;
import org.sifunity.integration.dto.DomainResponse;
import org.sifunity.integration.dto.SolutionCreateRequest;
import org.sifunity.integration.dto.SolutionResponse;
import org.sifunity.integration.dto.ZoneCreateRequest;
import org.sifunity.integration.dto.ZoneResponse;
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
import java.util.List;
import java.util.Map;

/**
 * Generator for creating test domains, solutions, and zones during integration tests.
 *
 * <p>This class provides CRUD operations for domains, solutions, and zones
 * directly against the Environment Service REST API (not through the Gateway).
 * Uses ADMIN credentials for authentication.</p>
 *
 * <p>Example usage:
 * <pre>
 * DomainGenerator generator = new DomainGenerator(environmentServiceUrl);
 *
 * // Create a domain
 * DomainResponse domain = generator.createDomain("test-domain", "Test domain");
 *
 * // Create a solution under the domain
 * SolutionResponse solution = generator.createSolution("test-domain", "test-solution", "Test solution");
 *
 * // Create a zone under the solution
 * ZoneResponse zone = generator.createZone("test-domain", "test-solution", "test-zone", "Test zone");
 *
 * // Clean up
 * generator.deleteDomain("test-domain");
 * </pre>
 *
 * @since 1.0.0
 */
@Slf4j
public class DomainGenerator {

    private final String environmentServiceUrl;
    private final RestTemplate restTemplate;
    private final AdminCredentialsConfig adminConfig;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new domain generator.
     *
     * @param environmentServiceUrl the base URL of the Environment Service
     */
    public DomainGenerator(String environmentServiceUrl) {
        this.environmentServiceUrl = environmentServiceUrl;
        this.restTemplate = new RestTemplate();
        this.adminConfig = new AdminCredentialsConfig();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    // ── Domain CRUD ─────────────────────────────────────────────────────

    /**
     * Creates a new domain.
     * Uses ADMIN credentials for authentication.
     *
     * @param domainId    the domain ID (must be unique)
     * @param description optional description
     * @return the created domain response
     */
    public DomainResponse createDomain(String domainId, String description) {
        log.info("Creating domain: {} (description: {})", domainId, description);

        DomainCreateRequest request = new DomainCreateRequest(domainId, description);

        String url = environmentServiceUrl + "/domains";

        HttpEntity<DomainCreateRequest> entity = new HttpEntity<>(request, buildAdminHeaders());

        ResponseEntity<DomainResponse> response = restTemplate.exchange(
            url, HttpMethod.POST, entity, DomainResponse.class
        );

        DomainResponse body = response.getBody();
        log.info("Domain created: id={}, domainId={}", body.id(), body.domainId());
        return body;
    }

    /**
     * Retrieves a domain by its domain ID.
     *
     * @param domainId the domain ID
     * @return the domain response
     */
    public DomainResponse getDomain(String domainId) {
        log.debug("Getting domain: {}", domainId);

        String url = environmentServiceUrl + "/domains/" + domainId;

        HttpEntity<Void> entity = new HttpEntity<>(buildAdminHeaders());

        ResponseEntity<DomainResponse> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, DomainResponse.class
        );

        return response.getBody();
    }

    /**
     * Deletes a domain by its domain ID.
     * Cascade deletes all child solutions, zones, contexts, and environments.
     *
     * @param domainId the domain ID
     */
    public void deleteDomain(String domainId) {
        log.info("Deleting domain: {}", domainId);

        String url = environmentServiceUrl + "/domains/" + domainId;

        HttpEntity<Void> entity = new HttpEntity<>(buildAdminHeaders());

        restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);

        log.info("Domain deleted: {}", domainId);
    }

    /**
     * Lists all domains.
     * Parses the SIF collection wrapper to extract the domain list.
     *
     * @return list of domain responses
     */
    @SuppressWarnings("unchecked")
    public List<DomainResponse> listDomains() {
        log.debug("Listing all domains");

        String url = environmentServiceUrl + "/domains";

        HttpEntity<Void> entity = new HttpEntity<>(buildAdminHeaders());

        ResponseEntity<Map> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, Map.class
        );

        // SifCollection format: { "domains": { "domain": [ ... ] } }
        Map<String, Object> body = response.getBody();
        Map<String, Object> wrapper = (Map<String, Object>) body.get("domains");
        List<Object> rawList = (List<Object>) wrapper.get("domain");

        List<DomainResponse> domains = objectMapper.convertValue(
            rawList, new TypeReference<List<DomainResponse>>() {}
        );

        log.debug("Listed {} domains", domains.size());
        return domains;
    }

    // ── Solution CRUD ───────────────────────────────────────────────────

    /**
     * Creates a new solution under the specified domain.
     * Uses ADMIN credentials for authentication.
     *
     * @param domainId    the parent domain ID
     * @param solutionId  the solution ID (must be unique within domain)
     * @param description optional description
     * @return the created solution response
     */
    public SolutionResponse createSolution(String domainId, String solutionId, String description) {
        log.info("Creating solution: {} in domain: {} (description: {})", solutionId, domainId, description);

        SolutionCreateRequest request = new SolutionCreateRequest(domainId, solutionId, description);

        String url = environmentServiceUrl + "/" + domainId + "/solutions";

        HttpEntity<SolutionCreateRequest> entity = new HttpEntity<>(request, buildAdminHeaders());

        ResponseEntity<SolutionResponse> response = restTemplate.exchange(
            url, HttpMethod.POST, entity, SolutionResponse.class
        );

        SolutionResponse body = response.getBody();
        log.info("Solution created: id={}, solutionId={}, domainId={}", body.id(), body.solutionId(), body.domainId());
        return body;
    }

    /**
     * Retrieves a solution by its solution ID.
     *
     * @param domainId   the parent domain ID
     * @param solutionId the solution ID
     * @return the solution response
     */
    public SolutionResponse getSolution(String domainId, String solutionId) {
        log.debug("Getting solution: {} in domain: {}", solutionId, domainId);

        String url = environmentServiceUrl + "/" + domainId + "/solutions/" + solutionId;

        HttpEntity<Void> entity = new HttpEntity<>(buildAdminHeaders());

        ResponseEntity<SolutionResponse> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, SolutionResponse.class
        );

        return response.getBody();
    }

    /**
     * Deletes a solution by its solution ID.
     * Cascade deletes all child zones, contexts, and environments.
     *
     * @param domainId   the parent domain ID
     * @param solutionId the solution ID
     */
    public void deleteSolution(String domainId, String solutionId) {
        log.info("Deleting solution: {} in domain: {}", solutionId, domainId);

        String url = environmentServiceUrl + "/" + domainId + "/solutions/" + solutionId;

        HttpEntity<Void> entity = new HttpEntity<>(buildAdminHeaders());

        restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);

        log.info("Solution deleted: {} in domain: {}", solutionId, domainId);
    }

    /**
     * Lists all solutions under the specified domain.
     * Parses the SIF collection wrapper to extract the solution list.
     *
     * @param domainId the parent domain ID
     * @return list of solution responses
     */
    @SuppressWarnings("unchecked")
    public List<SolutionResponse> listSolutions(String domainId) {
        log.debug("Listing solutions for domain: {}", domainId);

        String url = environmentServiceUrl + "/" + domainId + "/solutions";

        HttpEntity<Void> entity = new HttpEntity<>(buildAdminHeaders());

        ResponseEntity<Map> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, Map.class
        );

        // SifCollection format: { "solutions": { "solution": [ ... ] } }
        Map<String, Object> body = response.getBody();
        Map<String, Object> wrapper = (Map<String, Object>) body.get("solutions");
        List<Object> rawList = (List<Object>) wrapper.get("solution");

        List<SolutionResponse> solutions = objectMapper.convertValue(
            rawList, new TypeReference<List<SolutionResponse>>() {}
        );

        log.debug("Listed {} solutions for domain: {}", solutions.size(), domainId);
        return solutions;
    }

    // ── Zone Operations ─────────────────────────────────────────────────

    /**
     * Creates a new zone under the specified domain and solution.
     * Uses ADMIN credentials for authentication.
     *
     * @param domainId    the parent domain ID
     * @param solutionId  the parent solution ID
     * @param zoneId      the zone ID (must be unique within solution)
     * @param description optional description
     * @return the created zone response
     */
    public ZoneResponse createZone(String domainId, String solutionId, String zoneId, String description) {
        log.info("Creating zone: {} in solution: {} / domain: {} (description: {})",
            zoneId, solutionId, domainId, description);

        ZoneCreateRequest request = new ZoneCreateRequest(solutionId, zoneId, description);

        String url = environmentServiceUrl + "/" + domainId + "/" + solutionId + "/zones";

        HttpEntity<ZoneCreateRequest> entity = new HttpEntity<>(request, buildAdminHeaders());

        ResponseEntity<ZoneResponse> response = restTemplate.exchange(
            url, HttpMethod.POST, entity, ZoneResponse.class
        );

        ZoneResponse body = response.getBody();
        log.info("Zone created: id={}, zoneId={}, solutionId={}", body.id(), body.zoneId(), body.solutionId());
        return body;
    }

    /**
     * Lists all zones under the specified domain and solution.
     * Parses the SIF collection wrapper to extract the zone list.
     *
     * @param domainId   the parent domain ID
     * @param solutionId the parent solution ID
     * @return list of zone responses
     */
    @SuppressWarnings("unchecked")
    public List<ZoneResponse> listZones(String domainId, String solutionId) {
        log.debug("Listing zones for domain: {} / solution: {}", domainId, solutionId);

        String url = environmentServiceUrl + "/" + domainId + "/" + solutionId + "/zones";

        HttpEntity<Void> entity = new HttpEntity<>(buildAdminHeaders());

        ResponseEntity<Map> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, Map.class
        );

        // SifCollection format: { "zones": { "zone": [ ... ] } }
        Map<String, Object> body = response.getBody();
        Map<String, Object> wrapper = (Map<String, Object>) body.get("zones");
        List<Object> rawList = (List<Object>) wrapper.get("zone");

        List<ZoneResponse> zones = objectMapper.convertValue(
            rawList, new TypeReference<List<ZoneResponse>>() {}
        );

        log.debug("Listed {} zones for domain: {} / solution: {}", zones.size(), domainId, solutionId);
        return zones;
    }

    // ── Internal Helpers ────────────────────────────────────────────────

    /**
     * Builds HTTP headers with ADMIN authentication.
     *
     * @return headers with Authorization, timestamp, and Content-Type
     */
    private HttpHeaders buildAdminHeaders() {
        Instant timestamp = Instant.now();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", generateAdminAuthHeader(timestamp));
        headers.set("timestamp", timestamp.toString());
        headers.set("Content-Type", "application/json");
        return headers;
    }

    /**
     * Generates a SIFHMACSHA256 authentication header using ADMIN credentials.
     *
     * @param timestamp the request timestamp
     * @return the Authorization header value
     */
    private String generateAdminAuthHeader(Instant timestamp) {
        try {
            String timestampStr = timestamp.toString();
            String toSign = adminConfig.getSessionToken() + ":" + timestampStr;
            String hmac = computeHmacSha256(toSign, adminConfig.getSharedSecret());

            return "SIF_HMACSHA256 " + Base64.getEncoder().encodeToString(
                (adminConfig.getSessionToken() + ":" + hmac).getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Failed to generate admin auth header", e);
            throw new RuntimeException("Failed to generate admin auth header", e);
        }
    }

    /**
     * Computes HMAC-SHA256 of the input using the secret.
     *
     * @param input  the input to sign
     * @param secret the secret key
     * @return the HMAC as a Base64 encoded string
     */
    private String computeHmacSha256(String input, String secret) {
        try {
            byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretBytes, "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256", e);
        }
    }
}
