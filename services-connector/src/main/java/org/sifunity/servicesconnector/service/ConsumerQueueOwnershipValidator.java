// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.service;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.servicesconnector.cache.QueueOwnerCache;
import org.sifunity.servicesconnector.exception.UnknownQueueException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Validates that the consumer-supplied queueId is owned by the authenticated caller.
 *
 * <p>Identical semantics to requests-connector's validator. See that class for
 * detailed documentation on the no-bypass rule and fail-closed strategy.</p>
 *
 * <p><strong>Requirements:</strong> 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.10</p>
 */
@Component
@Slf4j
public class ConsumerQueueOwnershipValidator {

    private final QueueOwnerCache queueOwnerCache;

    public ConsumerQueueOwnershipValidator(QueueOwnerCache queueOwnerCache) {
        this.queueOwnerCache = queueOwnerCache;
    }

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
