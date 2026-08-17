// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.service;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.requestsconnector.cache.QueueOwnerCache;
import org.sifunity.requestsconnector.exception.UnknownQueueException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Validates that the consumer-supplied queueId is owned by the authenticated caller.
 *
 * <p>This is the single ownership gate for the DELAYED request path. It resolves the
 * queue's owner from queue-service and compares it to the caller's environment ID
 * (from {@code X-Environment-Id}, as resolved by the gateway post-impersonation).</p>
 *
 * <p>There is NO bypass. The rule is uniform for all callers including admins.
 * {@code X-Environment-Fingerprint} and {@code X-Internal-Service-Token} are NOT
 * consulted for this authorization decision.</p>
 *
 * <p><strong>Fail-closed:</strong> if the owner cannot be resolved (queue not found,
 * queue-service unreachable, malformed response), the request is rejected.</p>
 *
 * <p><strong>Requirements:</strong> 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.10</p>
 */
@Component
@Slf4j
public class ConsumerQueueOwnershipValidator {

    private final QueueOwnerCache queueOwnerCache;

    public ConsumerQueueOwnershipValidator(QueueOwnerCache queueOwnerCache) {
        this.queueOwnerCache = queueOwnerCache;
    }

    /**
     * Validates that the specified queue is owned by the caller. Returns silently on success;
     * throws {@link UnknownQueueException} on any failure (mismatch, not found, error, null caller).
     *
     * @param domainId              the domain ID from the request path
     * @param solutionId            the solution ID from the request path
     * @param queueId               the consumer-supplied queue ID
     * @param callerEnvironmentId   the caller's environment ID from X-Environment-Id
     * @throws UnknownQueueException if ownership cannot be confirmed
     */
    public void requireOwnership(String domainId, String solutionId,
                                 UUID queueId, UUID callerEnvironmentId) {
        if (callerEnvironmentId == null) {
            log.warn("Ownership violation: caller environment ID is null, queueId={}", queueId);
            throw new UnknownQueueException("The specified queue does not exist");
        }

        Optional<UUID> owner = queueOwnerCache.getQueueOwner(domainId, solutionId, queueId);

        if (owner.isEmpty()) {
            log.warn("Ownership violation: environment {} attempted to access queue {} (owner unresolvable)",
                    callerEnvironmentId, queueId);
            throw new UnknownQueueException("The specified queue does not exist");
        }

        if (!owner.get().equals(callerEnvironmentId)) {
            log.warn("Ownership violation: environment {} attempted to access queue {} (owned by {})",
                    callerEnvironmentId, queueId, owner.get());
            throw new UnknownQueueException("The specified queue does not exist");
        }
    }
}
