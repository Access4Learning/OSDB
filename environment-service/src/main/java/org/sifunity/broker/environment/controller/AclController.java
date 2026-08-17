// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.AclEntryCreateRequest;
import org.sifunity.broker.environment.dto.AclEntryUpdateRequest;
import org.sifunity.broker.environment.exception.AdminEnvironmentIdRequiredException;
import org.sifunity.broker.environment.exception.InsufficientAclPermissionsException;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.AuthorizationService;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Access Control List (ACL) entry management operations.
 * 
 * <p>This controller handles HTTP requests for ACL lifecycle operations:
 * <ul>
 *   <li>ACL entry creation (POST /acl-entries)</li>
 *   <li>ACL entry listing by environment (GET /acl-entries?environmentId=...)</li>
 *   <li>ACL entry access level updates (PUT /acl-entries/{id})</li>
 *   <li>ACL entry deletion (DELETE /acl-entries/{id})</li>
 * </ul>
 * 
 * <p>All endpoints require authentication through the API Gateway.
 * ACL entries control which services an environment can access and with
 * what permissions (PROVIDE, CONSUME, SUBSCRIBE).</p>
 * 
 * <p>Request validation is performed using Bean Validation annotations on DTOs.
 * Business logic validation and error handling is delegated to the service layer.</p>
 * 
 * @see AclService
 * @see AclEntryCreateRequest
 * @see AclEntryUpdateRequest
 * @see AclEntryDTO
 */
@Tag(name = "Internal API", description = "Internal management endpoints")
@RestController
@RequestMapping("/{domainId}/{solutionId}/acl-entries")
public class AclController {
    
    private static final Logger log = LoggerFactory.getLogger(AclController.class);
    
    private final AclService aclService;
    private final AuthorizationService authorizationService;
    
    public AclController(AclService aclService, AuthorizationService authorizationService) {
        this.aclService = aclService;
        this.authorizationService = authorizationService;
    }
    
    /**
     * Creates a new ACL entry.
     * 
     * <p>Creates an access control entry that defines what permissions an environment
     * has for accessing a specific service in a given context. The combination
     * of environment, service, contextUuid, and access type must be unique.</p>
     * 
     * <p>The environment must exist in the system before creating ACL entries for it.
     * Service names, zones, and access types are validated according to SIF 3.7 standards.</p>
     * 
     * @param request the ACL entry creation request containing all required parameters
     * @return ResponseEntity containing the created ACL entry details
     * 
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>201 Created - ACL entry successfully created</li>
     *   <li>400 Bad Request - Invalid request data (validation errors)</li>
     *   <li>404 Not Found - Environment does not exist</li>
     *   <li>409 Conflict - ACL entry with same parameters already exists</li>
     *   <li>500 Internal Server Error - Unexpected server error</li>
     * </ul>
     * 
     * <p><strong>Requirements:</strong> 4.1, 4.2, 4.3, 4.7</p>
     */
    @PostMapping
    public ResponseEntity<AclEntryDTO> createAclEntry(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @Valid @RequestBody AclEntryCreateRequest request,
            HttpServletRequest httpRequest) {
        
        Environment requester = (Environment) httpRequest.getAttribute("requester");
        boolean isAdmin = authorizationService.isAdmin(requester);
        
        log.info("Creating ACL entry: environmentId={}, serviceName={}, zoneId={}, contextId={}, accessType={}, accessLevel={}, isAdmin={}", 
                request.environmentId(), request.serviceName(), request.zoneId(), request.contextId(),
                request.accessType(), request.accessLevel(), isAdmin);
        
        AclEntryDTO aclEntry = aclService.createAclEntry(domainId, solutionId, request, requester, isAdmin);
        
        log.info("ACL entry created successfully: id={}, environmentId={}, serviceName={}, accessType={}, accessLevel={}", 
                aclEntry.id(), aclEntry.environmentId(), aclEntry.serviceName(), 
                aclEntry.accessType(), aclEntry.accessLevel());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(aclEntry);
    }
    
    /**
     * Lists all ACL entries for a specific environment.
     * 
     * <p>Returns all access control entries associated with the specified environment,
     * regardless of their access level or type. This is used for management and
     * auditing purposes.</p>
     * 
     * <p>The environmentId parameter is required and must be a valid UUID.</p>
     * 
     * @param environmentId the environment ID to retrieve ACL entries for
     * @return ResponseEntity containing a list of ACL entries (empty list if none found)
     * 
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>200 OK - ACL entries returned (may be empty list)</li>
     *   <li>400 Bad Request - Invalid environment ID format</li>
     *   <li>500 Internal Server Error - Unexpected server error</li>
     * </ul>
     * 
     * <p><strong>Requirements:</strong> 4.4</p>
     */
    @GetMapping
    public ResponseEntity<List<AclEntryDTO>> listAclEntries(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestParam(required = false) UUID environmentId,
            HttpServletRequest request) {
        
        Environment requester = (Environment) request.getAttribute("requester");
        boolean isAdmin = authorizationService.isAdmin(requester);
        
        if (isAdmin) {
            if (environmentId == null) {
                throw new AdminEnvironmentIdRequiredException();
            }
            log.debug("Admin listing ACL entries for environment: environmentId={}", environmentId);
            List<AclEntryDTO> aclEntries = aclService.listAclEntries(environmentId);
            log.debug("ACL entries retrieved: environmentId={}, count={}", environmentId, aclEntries.size());
            return ResponseEntity.ok(aclEntries);
        } else {
            if (environmentId != null && !environmentId.equals(requester.getId())) {
                throw new InsufficientAclPermissionsException(requester.getId(), environmentId);
            }
            UUID effectiveId = requester.getId();
            log.debug("Non-admin listing ACL entries for own environment: environmentId={}", effectiveId);
            List<AclEntryDTO> aclEntries = aclService.listAclEntries(effectiveId);
            log.debug("ACL entries retrieved: environmentId={}, count={}", effectiveId, aclEntries.size());
            return ResponseEntity.ok(aclEntries);
        }
    }
    
    /**
     * Updates an ACL entry's access level.
     * 
     * <p>Allows changing the access level (APPROVED, REJECTED, PENDING) of an
     * existing ACL entry. All other fields (environment, service, contextUuid,
     * access type) remain unchanged to maintain the unique constraint integrity.</p>
     * 
     * <p>This operation is commonly used to approve or reject pending ACL requests,
     * or to revoke previously granted permissions.</p>
     * 
     * @param id the unique identifier of the ACL entry to update
     * @param request the update request containing the new access level
     * @return ResponseEntity containing the updated ACL entry details
     * 
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>200 OK - ACL entry successfully updated</li>
     *   <li>400 Bad Request - Invalid request data (validation errors)</li>
     *   <li>404 Not Found - ACL entry does not exist</li>
     *   <li>500 Internal Server Error - Unexpected server error</li>
     * </ul>
     * 
     * <p><strong>Requirements:</strong> 4.5</p>
     */
    @PutMapping("/{id}")
    public ResponseEntity<AclEntryDTO> updateAclEntry(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID id,
            @Valid @RequestBody AclEntryUpdateRequest request,
            HttpServletRequest httpRequest) {
        
        Environment requester = (Environment) httpRequest.getAttribute("requester");
        if (!authorizationService.isAdmin(requester)) {
            throw new SifBrokerException("ACL_008", HttpStatus.FORBIDDEN,
                "Only admin environments can update ACL entries.");
        }
        
        log.info("Updating ACL entry: id={}, newAccessLevel={}", id, request.accessLevel());
        
        AclEntryDTO updatedAclEntry = aclService.updateAclEntry(id, request);
        
        log.info("ACL entry updated successfully: id={}, accessLevel={}", id, updatedAclEntry.accessLevel());
        
        return ResponseEntity.ok(updatedAclEntry);
    }
    
    /**
     * Deletes an ACL entry.
     * 
     * <p>Removes the specified access control entry from the system. This operation
     * cannot be undone and will immediately revoke the associated permissions.</p>
     * 
     * <p>Use with caution as deleting ACL entries may prevent environments from
     * accessing services they previously had permission to use.</p>
     * 
     * @param id the unique identifier of the ACL entry to delete
     * @return ResponseEntity with no content
     * 
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>204 No Content - ACL entry successfully deleted</li>
     *   <li>404 Not Found - ACL entry does not exist</li>
     *   <li>500 Internal Server Error - Unexpected server error</li>
     * </ul>
     * 
     * <p><strong>Requirements:</strong> 4.6</p>
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAclEntry(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        
        Environment requester = (Environment) httpRequest.getAttribute("requester");
        if (!authorizationService.isAdmin(requester)) {
            throw new SifBrokerException("ACL_009", HttpStatus.FORBIDDEN,
                "Only admin environments can delete ACL entries.");
        }
        
        log.info("Deleting ACL entry: id={}", id);
        
        aclService.deleteAclEntry(id);
        
        log.info("ACL entry deleted successfully: id={}", id);
        
        return ResponseEntity.noContent().build();
    }
}