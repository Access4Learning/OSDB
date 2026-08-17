// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.domain.AdminUser;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.UserEnvironmentAccess;
import org.sifunity.broker.environment.dto.EnvironmentUserDTO;
import org.sifunity.broker.environment.dto.UserEnvironmentAccessDTO;
import org.sifunity.broker.environment.dto.UserEnvironmentAccessRequest;
import org.sifunity.broker.environment.exception.AdminUserNotFoundException;
import org.sifunity.broker.environment.exception.DomainSolutionMismatchException;
import org.sifunity.broker.environment.exception.DuplicateAccessException;
import org.sifunity.broker.environment.exception.EnvironmentNotFoundException;
import org.sifunity.broker.environment.repository.AdminUserRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.UserEnvironmentAccessRepository;

import java.util.List;
import java.util.UUID;

/**
 * Service class for managing user-environment access associations.
 *
 * <p>Handles granting and revoking environment access for admin users,
 * enforcing domain+solution scope matching between the user and the
 * target environment.</p>
 */
@Service
@Transactional(readOnly = true)
public class UserEnvironmentAccessService {

    private static final Logger log = LoggerFactory.getLogger(UserEnvironmentAccessService.class);

    private final AdminUserRepository adminUserRepository;
    private final EnvironmentRepository environmentRepository;
    private final UserEnvironmentAccessRepository accessRepository;

    public UserEnvironmentAccessService(AdminUserRepository adminUserRepository,
                                        EnvironmentRepository environmentRepository,
                                        UserEnvironmentAccessRepository accessRepository) {
        this.adminUserRepository = adminUserRepository;
        this.environmentRepository = environmentRepository;
        this.accessRepository = accessRepository;
    }

    /**
     * Grants an admin user access to an environment.
     *
     * <p>Validates that the user exists, the environment exists, the environment
     * belongs to the same domain+solution as the user, and no duplicate
     * association already exists.</p>
     *
     * @param userId  the admin user's UUID
     * @param request the access request containing the environment ID
     * @return DTO representing the created association
     * @throws AdminUserNotFoundException      if the user does not exist
     * @throws EnvironmentNotFoundException    if the environment does not exist
     * @throws DomainSolutionMismatchException if the environment does not belong to the user's domain+solution
     * @throws DuplicateAccessException        if the user already has access to the environment
     */
    @Transactional
    public UserEnvironmentAccessDTO grantAccess(UUID userId, UserEnvironmentAccessRequest request) {
        log.debug("Granting user {} access to environment {}", userId, request.environmentId());

        AdminUser user = adminUserRepository.findById(userId)
                .orElseThrow(() -> new AdminUserNotFoundException(userId.toString()));

        Environment environment = environmentRepository.findById(request.environmentId())
                .orElseThrow(() -> new EnvironmentNotFoundException(request.environmentId()));

        // Verify domain+solution scope: environment's solution must match user's solution
        UUID userSolutionId = user.getSolution().getId();
        UUID environmentSolutionId = environment.getSolutionUuid();
        if (!userSolutionId.equals(environmentSolutionId)) {
            log.warn("Domain+solution mismatch: user {} (solution={}) cannot access environment {} (solution={})",
                    userId, userSolutionId, request.environmentId(), environmentSolutionId);
            throw new DomainSolutionMismatchException(request.environmentId().toString());
        }

        // Check for duplicate association
        if (accessRepository.existsByUser_IdAndEnvironment_Id(userId, request.environmentId())) {
            log.warn("Duplicate access: user {} already has access to environment {}", userId, request.environmentId());
            throw new DuplicateAccessException(userId.toString(), request.environmentId().toString());
        }

        UserEnvironmentAccess access = new UserEnvironmentAccess();
        access.setUser(user);
        access.setEnvironment(environment);

        UserEnvironmentAccess saved = accessRepository.save(access);
        log.info("Granted user {} access to environment {} (association={})",
                userId, request.environmentId(), saved.getId());

        return mapToDTO(saved, environment.getApplicationName());
    }

    /**
     * Revokes an admin user's access to an environment.
     *
     * @param userId        the admin user's UUID
     * @param environmentId the environment's UUID
     * @throws AdminUserNotFoundException if no association exists for the user+environment pair
     */
    @Transactional
    public void revokeAccess(UUID userId, UUID environmentId) {
        log.debug("Revoking user {} access to environment {}", userId, environmentId);

        UserEnvironmentAccess access = accessRepository.findByUser_IdAndEnvironment_Id(userId, environmentId)
                .orElseThrow(() -> new AdminUserNotFoundException(userId.toString()));

        accessRepository.delete(access);
        log.info("Revoked user {} access to environment {} (association={})",
                userId, environmentId, access.getId());
    }

    /**
     * Lists all environment access associations for a user.
     *
     * @param userId the admin user's UUID
     * @return list of access DTOs with environment application names
     */
    public List<UserEnvironmentAccessDTO> listAccessForUser(UUID userId) {
        log.debug("Listing environment access for user {}", userId);

        List<UserEnvironmentAccess> associations = accessRepository.findAllByUser_Id(userId);

        return associations.stream()
                .map(access -> mapToDTO(access, access.getEnvironment().getApplicationName()))
                .toList();
    }

    /**
     * Lists all admin users with access to a specific environment.
     *
     * @param environmentId the environment's UUID
     * @return list of environment user DTOs containing user ID, username, and grant timestamp
     */
    public List<EnvironmentUserDTO> listUsersForEnvironment(UUID environmentId) {
        log.debug("Listing users for environment {}", environmentId);

        List<UserEnvironmentAccess> associations = accessRepository.findAllByEnvironment_Id(environmentId);

        return associations.stream()
                .map(access -> new EnvironmentUserDTO(
                        access.getUser().getId(),
                        access.getUser().getUsername(),
                        access.getGrantedAt()
                ))
                .toList();
    }

    private UserEnvironmentAccessDTO mapToDTO(UserEnvironmentAccess access, String applicationName) {
        return new UserEnvironmentAccessDTO(
                access.getId(),
                access.getUser().getId(),
                access.getEnvironment().getId(),
                applicationName,
                access.getGrantedAt()
        );
    }
}
