// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.domain.AdminUser;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.AdminUserCreateRequest;
import org.sifunity.broker.environment.dto.AdminUserDTO;
import org.sifunity.broker.environment.exception.AdminUserNotFoundException;
import org.sifunity.broker.environment.exception.DuplicateAdminUserException;
import org.sifunity.broker.environment.exception.DomainNotFoundException;
import org.sifunity.broker.environment.exception.SolutionNotFoundException;
import org.sifunity.broker.environment.repository.AdminUserRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service class for managing admin users within the SIF Unity Broker.
 *
 * <p>Handles CRUD operations for admin user accounts scoped to a domain+solution
 * combination. Passwords are hashed with BCrypt (cost factor 10) before storage.</p>
 */
@Service
@Transactional(readOnly = true)
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,128}$");
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 72;
    private static final int BCRYPT_STRENGTH = 10;

    private final AdminUserRepository adminUserRepository;
    private final DomainRepository domainRepository;
    private final SolutionRepository solutionRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public AdminUserService(AdminUserRepository adminUserRepository,
                            DomainRepository domainRepository,
                            SolutionRepository solutionRepository) {
        this.adminUserRepository = adminUserRepository;
        this.domainRepository = domainRepository;
        this.solutionRepository = solutionRepository;
        this.passwordEncoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    /**
     * Creates a new admin user with the specified details.
     *
     * <p>Validates input (username regex, password length), verifies domain+solution
     * existence, checks username uniqueness within scope, hashes password with BCrypt,
     * and persists the user.</p>
     *
     * @param request the admin user creation request
     * @return AdminUserDTO containing the created user information
     * @throws IllegalArgumentException if username or password fails validation
     * @throws DomainNotFoundException if the referenced domain does not exist
     * @throws SolutionNotFoundException if the referenced solution does not exist
     * @throws DuplicateAdminUserException if username already exists in the domain+solution scope
     */
    @Transactional
    public AdminUserDTO createUser(AdminUserCreateRequest request) {
        log.debug("Creating admin user with username: {} in domain: {}, solution: {}",
                request.username(), request.domainId(), request.solutionId());

        validateUsername(request.username());
        validatePassword(request.password());

        Domain domain = domainRepository.findByDomainId(request.domainId())
                .orElseThrow(() -> new DomainNotFoundException(request.domainId()));

        Solution solution = solutionRepository.findByDomainUuidAndSolutionId(domain.getId(), request.solutionId())
                .orElseThrow(() -> new SolutionNotFoundException(request.solutionId()));

        if (adminUserRepository.existsByUsernameAndDomain_IdAndSolution_Id(
                request.username(), domain.getId(), solution.getId())) {
            log.warn("Attempted to create duplicate admin user: {} in domain: {}, solution: {}",
                    request.username(), request.domainId(), request.solutionId());
            throw new DuplicateAdminUserException(request.username());
        }

        AdminUser adminUser = new AdminUser();
        adminUser.setUsername(request.username());
        adminUser.setPasswordHash(passwordEncoder.encode(request.password()));
        adminUser.setDomain(domain);
        adminUser.setSolution(solution);

        AdminUser savedUser = adminUserRepository.save(adminUser);
        log.info("Successfully created admin user: {} (UUID: {})", savedUser.getUsername(), savedUser.getId());

        return mapToDTO(savedUser);
    }

    /**
     * Resets the password for an existing admin user.
     *
     * <p>Validates the new password and updates the BCrypt hash in the database.</p>
     *
     * @param userId the UUID of the admin user
     * @param newPassword the new plaintext password (8-72 characters)
     * @throws AdminUserNotFoundException if the user does not exist
     * @throws IllegalArgumentException if the password fails validation
     */
    @Transactional
    public void resetPassword(UUID userId, String newPassword) {
        validatePassword(newPassword);
        AdminUser user = adminUserRepository.findById(userId)
                .orElseThrow(() -> new AdminUserNotFoundException(userId.toString()));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        adminUserRepository.save(user);
        log.info("Password reset for admin user: {} (UUID: {})", user.getUsername(), userId);
    }

    /**
     * Deletes an admin user by their UUID.
     *
     * <p>Database CASCADE constraints handle deletion of associated
     * user_environment_access records.</p>
     *
     * @param userId the UUID of the admin user to delete
     * @throws AdminUserNotFoundException if the user does not exist
     */
    @Transactional
    public void deleteUser(UUID userId) {
        log.debug("Deleting admin user with ID: {}", userId);

        AdminUser adminUser = adminUserRepository.findById(userId)
                .orElseThrow(() -> new AdminUserNotFoundException(userId.toString()));

        adminUserRepository.delete(adminUser);
        log.info("Successfully deleted admin user: {} (UUID: {})", adminUser.getUsername(), userId);
    }

    /**
     * Retrieves an admin user by their UUID.
     *
     * @param userId the UUID of the admin user to retrieve
     * @return AdminUserDTO containing the user information
     * @throws AdminUserNotFoundException if the user does not exist
     */
    public AdminUserDTO getUserById(UUID userId) {
        log.debug("Retrieving admin user with ID: {}", userId);

        AdminUser adminUser = adminUserRepository.findById(userId)
                .orElseThrow(() -> new AdminUserNotFoundException(userId.toString()));

        return mapToDTO(adminUser);
    }

    /**
     * Lists all admin users within a specific domain+solution scope.
     *
     * @param domainId the human-readable domain identifier
     * @param solutionId the human-readable solution identifier
     * @return List of AdminUserDTO objects for users in the scope
     */
    public List<AdminUserDTO> listUsers(String domainId, String solutionId) {
        log.debug("Listing admin users for domain: {}, solution: {}", domainId, solutionId);

        Domain domain = domainRepository.findByDomainId(domainId)
                .orElseThrow(() -> new DomainNotFoundException(domainId));

        Solution solution = solutionRepository.findByDomainUuidAndSolutionId(domain.getId(), solutionId)
                .orElseThrow(() -> new SolutionNotFoundException(solutionId));

        List<AdminUser> users = adminUserRepository.findAllByDomain_IdAndSolution_Id(domain.getId(), solution.getId());
        log.debug("Found {} admin users in scope", users.size());

        return users.stream()
                .map(this::mapToDTO)
                .toList();
    }

    private void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "Username must be 3-128 characters, alphanumeric/underscore/hyphen only");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            throw new IllegalArgumentException("Password must be 8-72 characters");
        }
    }

    private AdminUserDTO mapToDTO(AdminUser adminUser) {
        return new AdminUserDTO(
                adminUser.getId(),
                adminUser.getUsername(),
                adminUser.getDomain().getId(),
                adminUser.getSolution().getId(),
                adminUser.getCreatedAt(),
                adminUser.getUpdatedAt()
        );
    }
}
