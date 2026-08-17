// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;

import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.broker.common.util.CredentialMasker;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.TokenValidationRequest;
import org.sifunity.broker.environment.dto.TokenValidationResponse;

/**
 * Service class for handling authentication operations.
 * 
 * <p>This service provides internal authentication APIs used by the API Gateway
 * to validate sessionTokens and HMAC signatures. It performs the following operations:
 * <ul>
 *   <li>Lookup environments by sessionToken</li>
 *   <li>Validate timestamp freshness using configurable tolerance</li>
 *   <li>Compute and verify HMAC signatures using shared secrets</li>
 *   <li>Return environment details for successful authentications</li>
 * </ul>
 * 
 * <p>The service uses {@link SifAuthenticationUtils} for HMAC computation and
 * timestamp validation according to SIF 3.7 specification.</p>
 * 
 * <p><strong>Security Note:</strong> This service has access to shared secrets
 * for HMAC validation. All operations are logged for security auditing.</p>
 * 
 * @see TokenValidationRequest
 * @see TokenValidationResponse
 * @see SifAuthenticationUtils
 * @see EnvironmentService
 */
@Service
@Transactional(readOnly = true)
public class AuthenticationService {
    
    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    
    private final EnvironmentService environmentService;
    private final SolutionService solutionService;
    private final DomainService domainService;
    
    public AuthenticationService(EnvironmentService environmentService, SolutionService solutionService,
                                 DomainService domainService) {
        this.environmentService = environmentService;
        this.solutionService = solutionService;
        this.domainService = domainService;
    }
    
    /**
     * Validates a sessionToken and HMAC signature for authentication.
     * 
     * <p>This method performs the complete authentication flow:
     * <ol>
     *   <li>Parse sessionToken, timestamp, and hash from the request</li>
     *   <li>Lookup the environment by sessionToken</li>
     *   <li>Validate timestamp freshness using SifAuthenticationUtils</li>
     *   <li>Retrieve shared secret and reject internal-only environments (sharedSecret = "not-used")</li>
     *   <li>Compute expected HMAC using the environment's sharedSecret</li>
     *   <li>Compare the computed hash with the provided hash</li>
     *   <li>Return environment details if validation succeeds</li>
     * </ol>
     * 
     * <p>The method logs all authentication attempts for security auditing,
     * including both successful and failed attempts with relevant details.</p>
     * 
     * @param request the token validation request containing sessionToken, timestamp, and hash
     * @return TokenValidationResponse containing environment details
     * @throws InvalidTokenException if the sessionToken does not exist, 
     *                              HMAC validation fails, or timestamp is outside tolerance
     * 
     * <p><strong>Requirements:</strong> 7.1, 7.2, 7.3, 7.4, 7.5, 7.6</p>
     */
    public TokenValidationResponse validateToken(TokenValidationRequest request) {
        String sessionToken = request.sessionToken();
        String timestamp = request.timestamp();
        String providedHash = request.hash();
        
        log.debug("Validating token for sessionToken: {}", CredentialMasker.mask(sessionToken));
        
        try {
            // 1. Lookup environment by sessionToken (Requirement 7.1)
            Environment environment = environmentService.findBySessionToken(sessionToken);
            if (environment == null) {
                log.warn("Authentication failed: sessionToken not found: {}", CredentialMasker.mask(sessionToken));
                throw new InvalidTokenException("SessionToken does not exist");
            }
            
            // 2. Validate timestamp freshness using SifAuthenticationUtils (Requirement 7.6)
            try {
                SifAuthenticationUtils.validateTimestamp(timestamp);
                log.debug("Timestamp validation passed for sessionToken: {}", CredentialMasker.mask(sessionToken));
            } catch (Exception e) {
                log.warn("Authentication failed: timestamp validation failed for sessionToken: {} - {}", 
                        CredentialMasker.mask(sessionToken), e.getMessage());
                throw new InvalidTokenException("Timestamp is outside tolerance window");
            }
            
            // 3. Retrieve shared secret (Requirement 12.2)
            String sharedSecret = environmentService.getSharedSecret(environment.getId());
            
            // 3b. Reject only environments that were never provisioned for external auth
            // (placeholder shared secret "not-used"). broker-services environments ARE the
            // solution-admin credential and DO authenticate externally — the admin console
            // detects solution-admin scope from the broker-services fingerprint returned in
            // the TokenValidationResponse (see solution admin auth feature).
            if ("not-used".equals(sharedSecret)) {
                log.warn("Authentication rejected: internal environment cannot authenticate externally. " +
                        "sessionToken: {}, reason=shared secret is placeholder 'not-used'", CredentialMasker.mask(sessionToken));
                throw new InvalidTokenException("Internal environment cannot authenticate externally");
            }
            
            // 4. Compute expected HMAC using SifAuthenticationUtils (Requirement 7.2)
            String expectedHash = SifAuthenticationUtils.computeHmac(
                    sessionToken, 
                    timestamp, 
                    sharedSecret
            );
            
            // 5. Compare hashes (Requirement 7.3, 7.4)
            if (!MessageDigest.isEqual(expectedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8), providedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                log.warn("Authentication failed: HMAC validation failed for sessionToken: {}", CredentialMasker.mask(sessionToken));
                throw new InvalidTokenException("HMAC signature validation failed");
            }
            
            log.info("Authentication successful for environment: {} (id: {})", 
                    environment.getApplicationName(), environment.getId());
            
            // 6. Resolve domainId and solutionId for the response (Requirement 4.6)
            String resolvedDomainId = null;
            String resolvedSolutionId = null;
            
            if (environment.getSolutionUuid() != null) {
                Solution solution = solutionService.findByIdCached(environment.getSolutionUuid())
                        .orElse(null);
                if (solution != null) {
                    resolvedSolutionId = solution.getSolutionId();
                    if (solution.getDomainUuid() != null) {
                        resolvedDomainId = domainService.findByIdCached(solution.getDomainUuid())
                                .map(Domain::getDomainId)
                                .orElse(null);
                    }
                    log.debug("Resolved solutionId '{}' and domainId '{}' for environment '{}'",
                            resolvedSolutionId, resolvedDomainId, environment.getApplicationName());
                }
            }
            
            // 7. Return TokenValidationResponse with environment details (Requirement 7.3)
            return new TokenValidationResponse(
                    environment.getId(),
                    environment.getApplicationName(),
                    environment.getType(),
                    environment.getDefaultZoneId(),
                    environment.getFingerprint(),
                    resolvedDomainId,
                    resolvedSolutionId,
                    environment.getSolutionUuid()
            );
            
        } catch (InvalidTokenException e) {
            // Re-throw authentication exceptions as-is
            throw e;
        } catch (Exception e) {
            // Log unexpected errors and convert to authentication failure
            log.error("Unexpected error during token validation for sessionToken: {}", CredentialMasker.mask(sessionToken), e);
            throw new InvalidTokenException("Token validation failed due to internal error");
        }
    }
}