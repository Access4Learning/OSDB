// SPDX-License-Identifier: MIT
package org.sifunity.integration.generator;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.integration.dto.AclEntryCreateRequest;
import org.sifunity.integration.dto.AclEntryUpdateRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Generator for creating test ACL entries during integration tests.
 * 
 * <p>This class provides utilities for:
 * <ul>
 *   <li>Creating ACL entries with various permission combinations</li>
 *   <li>Supporting QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE access types</li>
 *   <li>Supporting APPROVED, SUPPORTED, REJECTED, UNSUPPORTED, REQUESTED access levels</li>
 *   <li>Managing ACL entry lifecycle</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>
 * AclGenerator generator = new AclGenerator(environmentServiceUrl, environmentGenerator);
 * 
 * // Create an APPROVED CONSUME permission with a specific zone/context
 * AclEntryDTO consumerAcl = generator.createAclEntry(
 *     environmentId,
 *     "StudentPersonals",
 *     "DEFAULT",
 *     "DEFAULT",
 *     AccessType.QUERY,
 *     AccessLevel.APPROVED,
 *     sessionToken,
 *     sharedSecret
 * );
 * 
 * // Create an APPROVED PROVIDE permission (wildcard - null zone/context)
 * AclEntryDTO providerAcl = generator.createProvidePermission(
 *     environmentId,
 *     "StudentPersonals",
 *     null,
 *     null,
 *     sessionToken,
 *     sharedSecret
 * );
 * 
 * // Delete ACL entry after test
 * generator.deleteAclEntry(consumerAcl.id(), sessionToken, sharedSecret);
 * </pre>
 * 
 * @since 1.0.0
 */
@Slf4j
public class AclGenerator {
    
    private final String environmentServiceUrl;
    private final RestTemplate restTemplate;
    private final EnvironmentGenerator environmentGenerator;
    
    /**
     * Creates a new ACL generator.
     * 
     * @param environmentServiceUrl the base URL of the Environment Service
     * @param environmentGenerator the environment generator for creating auth headers
     */
    public AclGenerator(String environmentServiceUrl, EnvironmentGenerator environmentGenerator) {
        this.environmentServiceUrl = environmentServiceUrl;
        this.restTemplate = new RestTemplate();
        this.environmentGenerator = environmentGenerator;
    }
    
    /**
     * Creates a new ACL entry with the specified parameters.
     * 
     * @param environmentId the environment ID
     * @param serviceName the service name
     * @param zoneId the zone ID string (e.g., "DEFAULT")
     * @param contextId the context ID string (e.g., "DEFAULT")
     * @param accessType the access type (QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE)
     * @param accessLevel the access level (APPROVED, SUPPORTED, REJECTED, UNSUPPORTED, REQUESTED, PENDING)
     * @param sessionToken the session token for authentication
     * @param sharedSecret the shared secret for authentication
     * @return the created ACL entry
     */
    public AclEntryDTO createAclEntry(
        UUID environmentId,
        String serviceName,
        String zoneId,
        String contextId,
        AccessType accessType,
        AccessLevel accessLevel,
        String sessionToken,
        String sharedSecret
    ) {
        log.info("Creating ACL entry: environmentId={}, serviceName={}, zoneId={}, contextId={}, accessType={}, accessLevel={}",
            environmentId, serviceName, zoneId, contextId, accessType, accessLevel);
        
        AclEntryCreateRequest request = new AclEntryCreateRequest(
            environmentId.toString(),
            serviceName,
            zoneId,
            contextId,
            accessType,
            accessLevel
        );
        
        String url = environmentServiceUrl + "/" + GlobalAdminConstants.GLOBAL_DOMAIN 
            + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/acl-entries";
        
        // Create headers with authentication
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", environmentGenerator.generateAuthHeader(sessionToken, sharedSecret));
        headers.set("Content-Type", "application/json");
        
        HttpEntity<AclEntryCreateRequest> entity = new HttpEntity<>(request, headers);
        
        ResponseEntity<AclEntryDTO> response = restTemplate.exchange(
            url,
            HttpMethod.POST,
            entity,
            AclEntryDTO.class
        );
        
        AclEntryDTO aclEntry = response.getBody();
        
        log.info("ACL entry created: id={}, environmentId={}, serviceName={}, accessType={}, accessLevel={}",
            aclEntry.id(), aclEntry.environmentId(), aclEntry.serviceName(), 
            aclEntry.accessType(), aclEntry.accessLevel());
        
        return aclEntry;
    }
    
    /**
     * Creates an APPROVED PROVIDE permission for the specified service.
     * 
     * @param environmentId the environment ID
     * @param serviceName the service name
     * @param zoneId the zone ID string (null for wildcard ACL)
     * @param contextId the context ID string (null for wildcard ACL)
     * @param sessionToken the session token for authentication
     * @param sharedSecret the shared secret for authentication
     * @return the created ACL entry
     */
    public AclEntryDTO createProvidePermission(
        UUID environmentId,
        String serviceName,
        String zoneId,
        String contextId,
        String sessionToken,
        String sharedSecret
    ) {
        return createAclEntry(
            environmentId,
            serviceName,
            zoneId,
            contextId,
            AccessType.PROVIDE,
            AccessLevel.APPROVED,
            sessionToken,
            sharedSecret
        );
    }
    
    /**
     * Creates an APPROVED CONSUME permission for the specified service.
     * 
     * @param environmentId the environment ID
     * @param serviceName the service name
     * @param zoneId the zone ID string (null for wildcard ACL)
     * @param contextId the context ID string (null for wildcard ACL)
     * @param sessionToken the session token for authentication
     * @param sharedSecret the shared secret for authentication
     * @return the created ACL entry
     */
    public AclEntryDTO createConsumePermission(
        UUID environmentId,
        String serviceName,
        String zoneId,
        String contextId,
        String sessionToken,
        String sharedSecret
    ) {
        return createAclEntry(
            environmentId,
            serviceName,
            zoneId,
            contextId,
            AccessType.QUERY,
            AccessLevel.APPROVED,
            sessionToken,
            sharedSecret
        );
    }
    
    /**
     * Creates an APPROVED SUBSCRIBE permission for the specified service.
     * 
     * @param environmentId the environment ID
     * @param serviceName the service name
     * @param zoneId the zone ID string (null for wildcard ACL)
     * @param contextId the context ID string (null for wildcard ACL)
     * @param sessionToken the session token for authentication
     * @param sharedSecret the shared secret for authentication
     * @return the created ACL entry
     */
    public AclEntryDTO createSubscribePermission(
        UUID environmentId,
        String serviceName,
        String zoneId,
        String contextId,
        String sessionToken,
        String sharedSecret
    ) {
        return createAclEntry(
            environmentId,
            serviceName,
            zoneId,
            contextId,
            AccessType.SUBSCRIBE,
            AccessLevel.APPROVED,
            sessionToken,
            sharedSecret
        );
    }
    
    /**
     * Creates a REJECTED permission for the specified service and access type.
     * 
     * @param environmentId the environment ID
     * @param serviceName the service name
     * @param zoneId the zone ID string (null for wildcard ACL)
     * @param contextId the context ID string (null for wildcard ACL)
     * @param accessType the access type
     * @param sessionToken the session token for authentication
     * @param sharedSecret the shared secret for authentication
     * @return the created ACL entry
     */
    public AclEntryDTO createRejectedPermission(
        UUID environmentId,
        String serviceName,
        String zoneId,
        String contextId,
        AccessType accessType,
        String sessionToken,
        String sharedSecret
    ) {
        return createAclEntry(
            environmentId,
            serviceName,
            zoneId,
            contextId,
            accessType,
            AccessLevel.REJECTED,
            sessionToken,
            sharedSecret
        );
    }
    
    /**
     * Creates a PENDING permission for the specified service and access type.
     * 
     * @param environmentId the environment ID
     * @param serviceName the service name
     * @param zoneId the zone ID string (null for wildcard ACL)
     * @param contextId the context ID string (null for wildcard ACL)
     * @param accessType the access type
     * @param sessionToken the session token for authentication
     * @param sharedSecret the shared secret for authentication
     * @return the created ACL entry
     */
    public AclEntryDTO createPendingPermission(
        UUID environmentId,
        String serviceName,
        String zoneId,
        String contextId,
        AccessType accessType,
        String sessionToken,
        String sharedSecret
    ) {
        return createAclEntry(
            environmentId,
            serviceName,
            zoneId,
            contextId,
            accessType,
            AccessLevel.REQUESTED,
            sessionToken,
            sharedSecret
        );
    }
    
    /**
     * Lists all ACL entries for the specified environment.
     * 
     * @param environmentId the environment ID
     * @param sessionToken the session token for authentication
     * @param sharedSecret the shared secret for authentication
     * @return list of ACL entries
     */
    public List<AclEntryDTO> listAclEntries(UUID environmentId, String sessionToken, String sharedSecret) {
        log.debug("Listing ACL entries for environment: {}", environmentId);
        
        String url = environmentServiceUrl + "/" + GlobalAdminConstants.GLOBAL_DOMAIN 
            + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/acl-entries?environmentId=" + environmentId;
        
        // Create headers with authentication
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", environmentGenerator.generateAuthHeader(sessionToken, sharedSecret));
        
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        ResponseEntity<AclEntryDTO[]> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            entity,
            AclEntryDTO[].class
        );
        
        AclEntryDTO[] responseBody = response.getBody();
        List<AclEntryDTO> aclEntries = responseBody != null ? List.of(responseBody) : List.of();
        
        log.debug("ACL entries retrieved: environmentId={}, count={}", environmentId, aclEntries.size());
        
        return aclEntries;
    }
    
    /**
     * Updates an ACL entry's access level.
     * 
     * @param aclEntryId the ACL entry ID
     * @param newAccessLevel the new access level
     * @param sessionToken the session token for authentication
     * @param sharedSecret the shared secret for authentication
     * @return the updated ACL entry
     */
    public AclEntryDTO updateAccessLevel(UUID aclEntryId, AccessLevel newAccessLevel, String sessionToken, String sharedSecret) {
        log.info("Updating ACL entry: id={}, newAccessLevel={}", aclEntryId, newAccessLevel);
        
        AclEntryUpdateRequest request = new AclEntryUpdateRequest(newAccessLevel);
        
        String url = environmentServiceUrl + "/" + GlobalAdminConstants.GLOBAL_DOMAIN 
            + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/acl-entries/" + aclEntryId;
        
        // Create headers with authentication
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", environmentGenerator.generateAuthHeader(sessionToken, sharedSecret));
        headers.set("Content-Type", "application/json");
        
        HttpEntity<AclEntryUpdateRequest> entity = new HttpEntity<>(request, headers);
        
        restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
        
        // Fetch the updated entry
        HttpEntity<Void> getEntity = new HttpEntity<>(headers);
        ResponseEntity<AclEntryDTO> response = restTemplate.exchange(url, HttpMethod.GET, getEntity, AclEntryDTO.class);
        AclEntryDTO updated = response.getBody();
        
        log.info("ACL entry updated: id={}, accessLevel={}", updated.id(), updated.accessLevel());
        
        return updated;
    }
    
    /**
     * Deletes an ACL entry by ID.
     * 
     * @param aclEntryId the ACL entry ID
     * @param sessionToken the session token for authentication
     * @param sharedSecret the shared secret for authentication
     */
    public void deleteAclEntry(UUID aclEntryId, String sessionToken, String sharedSecret) {
        log.info("Deleting ACL entry: {}", aclEntryId);
        
        String url = environmentServiceUrl + "/" + GlobalAdminConstants.GLOBAL_DOMAIN 
            + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/acl-entries/" + aclEntryId;
        
        // Create headers with authentication
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", environmentGenerator.generateAuthHeader(sessionToken, sharedSecret));
        
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
        
        log.info("ACL entry deleted: {}", aclEntryId);
    }
    
    /**
     * Deletes all ACL entries for the specified environment.
     * 
     * @param environmentId the environment ID
     * @param sessionToken the session token for authentication
     * @param sharedSecret the shared secret for authentication
     */
    public void deleteAllForEnvironment(UUID environmentId, String sessionToken, String sharedSecret) {
        log.info("Deleting all ACL entries for environment: {}", environmentId);
        
        List<AclEntryDTO> aclEntries = listAclEntries(environmentId, sessionToken, sharedSecret);
        
        for (AclEntryDTO aclEntry : aclEntries) {
            deleteAclEntry(aclEntry.id(), sessionToken, sharedSecret);
        }
        
        log.info("All ACL entries deleted for environment: {}", environmentId);
    }
}
