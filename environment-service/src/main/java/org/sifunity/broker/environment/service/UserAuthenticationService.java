// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.sifunity.broker.environment.domain.AdminUser;
import org.sifunity.broker.environment.domain.UserEnvironmentAccess;
import org.sifunity.broker.environment.dto.AdminSessionContext;
import org.sifunity.broker.environment.dto.EnvironmentSummaryDTO;
import org.sifunity.broker.environment.dto.UserLoginRequest;
import org.sifunity.broker.environment.dto.UserLoginResponse;
import org.sifunity.broker.environment.exception.AccountLockedException;
import org.sifunity.broker.environment.exception.InvalidAdminSessionException;
import org.sifunity.broker.environment.repository.AdminUserRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.UserEnvironmentAccessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.common.util.CredentialMasker;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service responsible for admin user authentication, session management, and lockout handling.
 *
 * <p>Manages the complete authentication lifecycle:
 * <ul>
 *   <li>Login with username/password verification (BCrypt)</li>
 *   <li>Session token generation and storage in Redis</li>
 *   <li>Environment selection within an active session</li>
 *   <li>Session validation for protected endpoints</li>
 *   <li>Logout (session invalidation)</li>
 *   <li>Account lockout after consecutive failed attempts</li>
 * </ul>
 *
 * <p>Redis key patterns follow the existing convention:
 * <ul>
 *   <li>Session: {@code domain:{domainId}:solution:{solutionId}:function:admin_session:token:{sessionToken}}</li>
 *   <li>Lockout: {@code domain:{domainId}:solution:{solutionId}:function:admin_lockout:user:{username}}</li>
 * </ul>
 *
 * <p><strong>Security:</strong> All authentication failures return the same generic error message
 * ("Invalid credentials") to prevent information leakage about valid usernames or lockout state.</p>
 *
 * @see AdminSessionContext
 * @see AccountLockedException
 * @see InvalidAdminSessionException
 */
@Service
@Transactional(readOnly = true)
public class UserAuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(UserAuthenticationService.class);

    private static final String SESSION_KEY_TEMPLATE =
            "domain:%s:solution:%s:function:admin_session:token:%s";
    private static final String LOCKOUT_KEY_TEMPLATE =
            "domain:%s:solution:%s:function:admin_lockout:user:%s";

    private static final Duration SESSION_TTL = Duration.ofMinutes(60);
    private static final Duration LOCKOUT_TTL = Duration.ofMinutes(15);
    private static final int MAX_FAILED_ATTEMPTS = 5;

    // Session hash field names
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_DOMAIN_UUID = "domainUuid";
    private static final String FIELD_SOLUTION_UUID = "solutionUuid";
    private static final String FIELD_SELECTED_ENVIRONMENT_ID = "selectedEnvironmentId";
    private static final String FIELD_CREATED_AT = "createdAt";

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";

    private final StringRedisTemplate redisTemplate;
    private final AdminUserRepository adminUserRepository;
    private final UserEnvironmentAccessRepository accessRepository;
    private final DomainRepository domainRepository;
    private final SolutionRepository solutionRepository;

    public UserAuthenticationService(StringRedisTemplate redisTemplate,
                                     AdminUserRepository adminUserRepository,
                                     UserEnvironmentAccessRepository accessRepository,
                                     DomainRepository domainRepository,
                                     SolutionRepository solutionRepository) {
        this.redisTemplate = redisTemplate;
        this.adminUserRepository = adminUserRepository;
        this.accessRepository = accessRepository;
        this.domainRepository = domainRepository;
        this.solutionRepository = solutionRepository;
    }

    /**
     * Authenticates an admin user with username and password.
     *
     * <p>Flow:
     * <ol>
     *   <li>Validate input length constraints (username ≤ 128, password ≤ 128)</li>
     *   <li>Check lockout counter in Redis — reject if ≥ 5 failures</li>
     *   <li>Look up user by username + domain + solution</li>
     *   <li>Verify password with BCrypt.checkpw()</li>
     *   <li>On success: reset lockout counter, generate session token, store in Redis, return response</li>
     *   <li>On failure: increment lockout counter</li>
     * </ol>
     *
     * @param request the login request containing username, password, domainUuid, solutionUuid
     * @return UserLoginResponse with session token, expiry, and environment list
     * @throws AccountLockedException if account is locked (message: "Invalid credentials")
     * @throws SifBrokerException with 401 if credentials are invalid
     */
    public UserLoginResponse login(UserLoginRequest request) {
        String username = request.username();

        // Resolve domain and solution from string IDs to UUIDs
        UUID domainUuid = domainRepository.findByDomainId(request.domainId())
                .map(org.sifunity.broker.environment.domain.Domain::getId)
                .orElseThrow(() -> new SifBrokerException("ADMIN_USER_007", HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE));
        UUID solutionUuid = solutionRepository.findByDomainUuidAndSolutionId(domainUuid, request.solutionId())
                .map(org.sifunity.broker.environment.domain.Solution::getId)
                .orElseThrow(() -> new SifBrokerException("ADMIN_USER_007", HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE));

        // Requirement 3.8: reject oversized inputs with same error as invalid credentials
        if (username == null || username.isBlank()
                || request.password() == null || request.password().isBlank()) {
            log.debug("Login rejected: empty username or password for domain={}, solution={}",
                    domainUuid, solutionUuid);
            throw new SifBrokerException("ADMIN_USER_007", HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
        }

        if (username.length() > 128 || request.password().length() > 128) {
            log.debug("Login rejected: username or password exceeds 128 chars for domain={}, solution={}",
                    domainUuid, solutionUuid);
            throw new SifBrokerException("ADMIN_USER_007", HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
        }

        // Check lockout counter
        String lockoutKey = buildLockoutKey(domainUuid, solutionUuid, username);
        if (isAccountLocked(lockoutKey)) {
            log.info("Login rejected: account locked for user={} in domain={}, solution={}",
                    username, domainUuid, solutionUuid);
            throw new AccountLockedException();
        }

        // Look up user
        Optional<AdminUser> userOpt = adminUserRepository.findByUsernameAndDomain_IdAndSolution_Id(
                username, domainUuid, solutionUuid);

        if (userOpt.isEmpty()) {
            // User not found — increment lockout and reject
            incrementLockoutCounter(lockoutKey);
            log.debug("Login failed: user not found for username={}, domain={}, solution={}",
                    username, domainUuid, solutionUuid);
            throw new SifBrokerException("ADMIN_USER_007", HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
        }

        AdminUser user = userOpt.get();

        // Verify password with BCrypt
        if (!BCrypt.checkpw(request.password(), user.getPasswordHash())) {
            incrementLockoutCounter(lockoutKey);
            log.debug("Login failed: invalid password for username={}, domain={}, solution={}",
                    username, domainUuid, solutionUuid);
            throw new SifBrokerException("ADMIN_USER_007", HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
        }

        // Authentication successful — reset lockout counter
        resetLockoutCounter(lockoutKey);

        // Generate session token (UUID v4 = 128 bits entropy)
        String sessionToken = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plus(SESSION_TTL);

        // Store session in Redis
        String sessionKey = buildSessionKey(domainUuid, solutionUuid, sessionToken);
        Map<String, String> sessionData = Map.of(
                FIELD_USER_ID, user.getId().toString(),
                FIELD_USERNAME, user.getUsername(),
                FIELD_DOMAIN_UUID, domainUuid.toString(),
                FIELD_SOLUTION_UUID, solutionUuid.toString(),
                FIELD_CREATED_AT, createdAt.toString()
        );
        redisTemplate.opsForHash().putAll(sessionKey, sessionData);
        redisTemplate.expire(sessionKey, SESSION_TTL);

        // Store reverse-lookup key for token-based session retrieval
        String reverseKey = buildReverseSessionKey(sessionToken);
        redisTemplate.opsForValue().set(reverseKey, sessionKey, SESSION_TTL);

        // Fetch user's environment associations
        List<UserEnvironmentAccess> accessList = accessRepository.findAllByUser_Id(user.getId());
        List<EnvironmentSummaryDTO> environments = accessList.stream()
                .map(access -> new EnvironmentSummaryDTO(
                        access.getEnvironment().getId(),
                        access.getEnvironment().getApplicationName()))
                .toList();

        log.info("Login successful for user={} in domain={}, solution={}, environments={}",
                username, domainUuid, solutionUuid, environments.size());

        return new UserLoginResponse(sessionToken, expiresAt, environments);
    }

    /**
     * Selects an environment for the current admin session.
     *
     * <p>Validates that the session is active and the user has access to the specified environment,
     * then stores the selected environment ID in the Redis session hash.</p>
     *
     * @param sessionToken the active session token
     * @param environmentId the environment to select
     * @throws InvalidAdminSessionException if session is invalid or expired
     * @throws SifBrokerException with 403 if user is not authorized for the environment
     */
    public void selectEnvironment(String sessionToken, UUID environmentId) {
        AdminSessionContext session = validateSession(sessionToken);

        // Verify user has access to this environment
        boolean hasAccess = accessRepository.existsByUser_IdAndEnvironment_Id(
                session.userId(), environmentId);

        if (!hasAccess) {
            log.warn("Environment selection rejected: user={} not authorized for environment={}",
                    session.username(), environmentId);
            throw new SifBrokerException("ADMIN_USER_008", HttpStatus.FORBIDDEN,
                    "Not authorized to access this environment");
        }

        // Store selected environment in session
        String sessionKey = buildSessionKey(session.domainUuid(), session.solutionUuid(), sessionToken);
        redisTemplate.opsForHash().put(sessionKey, FIELD_SELECTED_ENVIRONMENT_ID, environmentId.toString());

        log.info("Environment selected: user={}, environment={}", session.username(), environmentId);
    }

    /**
     * Validates an admin session token by retrieving the session from Redis.
     *
     * @param sessionToken the session token to validate
     * @return AdminSessionContext with session details
     * @throws InvalidAdminSessionException if session is invalid, expired, or not found
     */
    public AdminSessionContext validateSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new InvalidAdminSessionException();
        }

        // Use reverse-lookup key to find the full namespaced session key by token alone
        String reverseKey = buildReverseSessionKey(sessionToken);
        String sessionKey = redisTemplate.opsForValue().get(reverseKey);

        if (sessionKey == null) {
            log.debug("Session not found for token (reverse lookup miss): {}", CredentialMasker.mask(sessionToken));
            throw new InvalidAdminSessionException();
        }

        Map<Object, Object> entries = redisTemplate.opsForHash().entries(sessionKey);

        if (entries == null || entries.isEmpty()) {
            // Session expired or was deleted
            log.debug("Session hash empty/expired for token: {}", CredentialMasker.mask(sessionToken));
            // Clean up stale reverse key
            redisTemplate.delete(reverseKey);
            throw new InvalidAdminSessionException();
        }

        String userId = (String) entries.get(FIELD_USER_ID);
        String username = (String) entries.get(FIELD_USERNAME);
        String domainUuid = (String) entries.get(FIELD_DOMAIN_UUID);
        String solutionUuid = (String) entries.get(FIELD_SOLUTION_UUID);
        String selectedEnvId = (String) entries.get(FIELD_SELECTED_ENVIRONMENT_ID);

        if (userId == null || username == null || domainUuid == null || solutionUuid == null) {
            log.warn("Session hash missing required fields for token: {}", CredentialMasker.mask(sessionToken));
            throw new InvalidAdminSessionException();
        }

        return new AdminSessionContext(
                UUID.fromString(userId),
                username,
                UUID.fromString(domainUuid),
                UUID.fromString(solutionUuid),
                selectedEnvId != null ? UUID.fromString(selectedEnvId) : null
        );
    }

    /**
     * Logs out an admin user by deleting their session from Redis.
     *
     * @param sessionToken the session token to invalidate
     */
    public void logout(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return;
        }

        String reverseKey = buildReverseSessionKey(sessionToken);
        String sessionKey = redisTemplate.opsForValue().get(reverseKey);

        if (sessionKey != null) {
            redisTemplate.delete(sessionKey);
            log.info("Session invalidated for token: {}", CredentialMasker.mask(sessionToken));
        }

        redisTemplate.delete(reverseKey);
    }

    // ---- Private helpers ----

    private boolean isAccountLocked(String lockoutKey) {
        String countStr = redisTemplate.opsForValue().get(lockoutKey);
        if (countStr == null) {
            return false;
        }
        try {
            int count = Integer.parseInt(countStr);
            return count >= MAX_FAILED_ATTEMPTS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void incrementLockoutCounter(String lockoutKey) {
        Long newCount = redisTemplate.opsForValue().increment(lockoutKey);
        // Set TTL on first increment or refresh it
        redisTemplate.expire(lockoutKey, LOCKOUT_TTL);
        log.debug("Lockout counter incremented to {} for key: {}", newCount, lockoutKey);
    }

    private void resetLockoutCounter(String lockoutKey) {
        redisTemplate.delete(lockoutKey);
    }

    private String buildSessionKey(UUID domainUuid, UUID solutionUuid, String sessionToken) {
        return String.format(SESSION_KEY_TEMPLATE,
                domainUuid.toString(), solutionUuid.toString(), sessionToken);
    }

    private String buildLockoutKey(UUID domainUuid, UUID solutionUuid, String username) {
        return String.format(LOCKOUT_KEY_TEMPLATE,
                domainUuid.toString(), solutionUuid.toString(), username);
    }

    /**
     * Builds a reverse-lookup key that maps a session token to its full namespaced key.
     * This enables session validation without knowing the domain+solution upfront.
     */
    private String buildReverseSessionKey(String sessionToken) {
        return "admin_session:reverse:" + sessionToken;
    }
}
