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
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.dto.SifCollection;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.AdminUserCreateRequest;
import org.sifunity.broker.environment.dto.AdminUserDTO;
import org.sifunity.broker.environment.dto.PasswordResetRequest;
import org.sifunity.broker.common.exception.InsufficientPermissionsException;
import org.sifunity.broker.environment.exception.DomainNotFoundException;
import org.sifunity.broker.environment.exception.SolutionNotFoundException;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.AdminUserService;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for admin user management operations.
 *
 * <p>All endpoints are HMAC-authenticated (handled by the existing AuthorizationFilter).
 * Authorization scoping determines what the caller can manage:</p>
 * <ul>
 *   <li>Global admin environment → any domain+solution (system-wide)</li>
 *   <li>{@code broker-services} environment → any environment in same domain+solution</li>
 *   <li>Regular environment → same environment only</li>
 * </ul>
 *
 * <p><strong>Requirements:</strong> 1.1, 1.2, 1.3, 1.5, 1.6, 1.7, 1.8, 1.9</p>
 *
 * @see AdminUserService
 */
@Tag(name = "Internal API", description = "Internal management endpoints")
@RestController
@RequestMapping("/admin-users")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    private static final String BROKER_SERVICES_FINGERPRINT = "broker-services";

    private final AdminUserService adminUserService;
    private final SolutionRepository solutionRepository;
    private final DomainRepository domainRepository;

    public AdminUserController(AdminUserService adminUserService,
                               SolutionRepository solutionRepository,
                               DomainRepository domainRepository) {
        this.adminUserService = adminUserService;
        this.solutionRepository = solutionRepository;
        this.domainRepository = domainRepository;
    }

    /**
     * Creates a new admin user.
     *
     * <p>The caller must have management scope over the target domain+solution:</p>
     * <ul>
     *   <li>Global admin → can create users in any domain+solution</li>
     *   <li>broker-services → can create users in same domain+solution</li>
     *   <li>Regular environment → can create users in same domain+solution as itself</li>
     * </ul>
     *
     * @param request the admin user creation request
     * @param httpRequest the HTTP request (for extracting requester)
     * @return ResponseEntity containing the created AdminUserDTO
     */
    @PostMapping
    public ResponseEntity<AdminUserDTO> createAdminUser(
            @Valid @RequestBody AdminUserCreateRequest request,
            HttpServletRequest httpRequest) {

        Environment requester = getRequester(httpRequest);
        log.info("Create admin user request from environment={}, username={}, domainId={}, solutionId={}",
                requester.getId(), request.username(), request.domainId(), request.solutionId());

        UUID domainUuid = domainRepository.findByDomainId(request.domainId())
                .map(org.sifunity.broker.environment.domain.Domain::getId)
                .orElseThrow(() -> new DomainNotFoundException(request.domainId()));
        UUID solutionUuid = solutionRepository.findByDomainUuidAndSolutionId(domainUuid, request.solutionId())
                .map(Solution::getId)
                .orElseThrow(() -> new SolutionNotFoundException(request.solutionId()));

        validateManagementScope(requester, domainUuid, solutionUuid);

        AdminUserDTO createdUser = adminUserService.createUser(request);

        log.info("Admin user created: id={}, username={}", createdUser.id(), createdUser.username());

        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    /**
     * Lists admin users filtered by domain and solution.
     *
     * <p>Scoping rules:</p>
     * <ul>
     *   <li>Global admin → can list users in any domain+solution</li>
     *   <li>broker-services → can list users in same domain+solution</li>
     *   <li>Regular environment → can list users in same domain+solution as itself</li>
     * </ul>
     *
     * @param domainId the human-readable domain identifier
     * @param solutionId the human-readable solution identifier
     * @param httpRequest the HTTP request (for extracting requester)
     * @return ResponseEntity containing the list of admin users
     */
    @GetMapping
    public ResponseEntity<SifCollection> listAdminUsers(
            @RequestParam String domainId,
            @RequestParam String solutionId,
            HttpServletRequest httpRequest) {

        Environment requester = getRequester(httpRequest);
        log.debug("List admin users request from environment={}, domainId={}, solutionId={}",
                requester.getId(), domainId, solutionId);

        UUID domainUuid = domainRepository.findByDomainId(domainId)
                .map(org.sifunity.broker.environment.domain.Domain::getId)
                .orElseThrow(() -> new DomainNotFoundException(domainId));
        UUID solutionUuid = solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)
                .map(Solution::getId)
                .orElseThrow(() -> new SolutionNotFoundException(solutionId));

        validateManagementScope(requester, domainUuid, solutionUuid);

        List<AdminUserDTO> users = adminUserService.listUsers(domainId, solutionId);

        log.debug("Returning {} admin users", users.size());

        return ResponseEntity.ok(SifCollection.of("adminUser", users));
    }

    /**
     * Retrieves an admin user by ID.
     *
     * <p>The caller must have visibility over the user's domain+solution scope.</p>
     *
     * @param id the admin user UUID
     * @param httpRequest the HTTP request (for extracting requester)
     * @return ResponseEntity containing the AdminUserDTO
     */
    @GetMapping("/{id}")
    public ResponseEntity<AdminUserDTO> getAdminUser(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {

        Environment requester = getRequester(httpRequest);
        log.debug("Get admin user request from environment={}, userId={}", requester.getId(), id);

        AdminUserDTO user = adminUserService.getUserById(id);

        validateManagementScope(requester, user.domainUuid(), user.solutionUuid());

        return ResponseEntity.ok(user);
    }

    /**
     * Deletes an admin user.
     *
     * <p>The caller must have management scope over the user's domain+solution.</p>
     *
     * @param id the admin user UUID to delete
     * @param httpRequest the HTTP request (for extracting requester)
     * @return ResponseEntity with no content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAdminUser(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {

        Environment requester = getRequester(httpRequest);
        log.info("Delete admin user request from environment={}, userId={}", requester.getId(), id);

        AdminUserDTO user = adminUserService.getUserById(id);

        validateManagementScope(requester, user.domainUuid(), user.solutionUuid());

        adminUserService.deleteUser(id);

        log.info("Admin user deleted: id={}", id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Resets the password for an admin user.
     *
     * <p>The caller must have management scope over the user's domain+solution.</p>
     *
     * @param id the admin user UUID
     * @param request the password reset request containing the new password
     * @param httpRequest the HTTP request (for extracting requester)
     * @return ResponseEntity with no content
     */
    @PutMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest) {

        Environment requester = getRequester(httpRequest);
        log.info("Reset password request from environment={}, userId={}", requester.getId(), id);

        AdminUserDTO user = adminUserService.getUserById(id);

        validateManagementScope(requester, user.domainUuid(), user.solutionUuid());

        adminUserService.resetPassword(id, request.password());

        log.info("Password reset completed for admin user: id={}", id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Validates that the requester has management scope over the target domain+solution.
     *
     * <p>Authorization rules:</p>
     * <ul>
     *   <li>Global admin → allowed for any domain+solution</li>
     *   <li>broker-services fingerprint → allowed if in same domain+solution</li>
     *   <li>Regular environment → allowed if in same domain+solution</li>
     * </ul>
     *
     * @param requester the calling environment
     * @param targetDomainUuid the target domain UUID
     * @param targetSolutionUuid the target solution UUID
     * @throws InsufficientPermissionsException if the caller lacks scope
     */
    private void validateManagementScope(Environment requester, UUID targetDomainUuid, UUID targetSolutionUuid) {
        // Global admin can manage any scope
        if (isGlobalAdmin(requester)) {
            return;
        }

        // For broker-services and regular environments, check same domain+solution
        UUID requesterSolutionUuid = requester.getSolutionUuid();
        if (requesterSolutionUuid == null) {
            throw new InsufficientPermissionsException(
                    "Environment " + requester.getId() + " is not authorized to manage admin users in this scope");
        }

        // Resolve requester's domain UUID from their solution
        Solution requesterSolution = solutionRepository.findById(requesterSolutionUuid).orElse(null);
        if (requesterSolution == null) {
            throw new InsufficientPermissionsException(
                    "Environment " + requester.getId() + " is not authorized to manage admin users in this scope");
        }

        UUID requesterDomainUuid = requesterSolution.getDomainUuid();

        // Check if requester is in the same domain+solution as the target
        if (requesterDomainUuid.equals(targetDomainUuid) && requesterSolutionUuid.equals(targetSolutionUuid)) {
            return;
        }

        throw new InsufficientPermissionsException(
                "Environment " + requester.getId() + " is not authorized to manage admin users in this scope");
    }

    /**
     * Checks if the requester environment is a global admin.
     */
    private boolean isGlobalAdmin(Environment requester) {
        if (requester.getSolutionUuid() == null) {
            return false;
        }
        return solutionRepository.findById(requester.getSolutionUuid())
                .map(solution -> {
                    String domainId = domainRepository.findById(solution.getDomainUuid())
                            .map(org.sifunity.broker.environment.domain.Domain::getDomainId)
                            .orElse(null);
                    return GlobalAdminConstants.isGlobalAdmin(domainId, solution.getSolutionId());
                })
                .orElse(false);
    }

    /**
     * Extracts the requester environment from the request attributes.
     * The AuthorizationFilter sets this after successful HMAC authentication.
     */
    private Environment getRequester(HttpServletRequest httpRequest) {
        Object requester = httpRequest.getAttribute("requester");
        if (requester == null) {
            throw new InsufficientPermissionsException("Authentication required");
        }
        return (Environment) requester;
    }
}
