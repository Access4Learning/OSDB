// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.service;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.responsesconnector.cache.DestinationEntitlementCache;
import org.sifunity.responsesconnector.cache.QueueOwnerCache;
import org.sifunity.responsesconnector.exception.ResponseDestinationDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Verifies that a caller-supplied destination queue is a legitimate target for a response.
 *
 * <p>The public response endpoints accept the destination {@code queueId} from the caller and the
 * broker then enqueues with its own privileges, so without this gate a provider can deliver data
 * into a queue owned by an environment with no entitlement to the object at all.</p>
 *
 * <h2>Why the predicate is entitlement, not ownership</h2>
 *
 * <p>Reusing the request-side rule ("the queue must be owned by the caller") would reject every
 * legitimate delayed response: in the SIF brokered flow a provider posts a response naming the
 * <em>consumer's</em> queue, which the provider does not and should not own. The question here is
 * therefore about the queue's <strong>owner</strong>, not the caller — is that owner entitled to
 * this object's data at all?</p>
 *
 * <h2>Why "any grant" rather than a specific access type</h2>
 *
 * <p>A delayed response is delivered to the queue of the consumer that issued the original
 * request, and the broker cannot know which access type that consumer used. A consumer that
 * issued a create/update/delete holds {@code CREATE}/{@code UPDATE}/{@code DELETE} and may hold no
 * {@code QUERY} grant at all, so requiring a specific type would reject legitimate responses.
 * Deriving the type from the original request would require correlation state this design
 * deliberately avoids.</p>
 *
 * <h2>What this does not prevent</h2>
 *
 * <p>Misrouting <em>among entitled consumers</em> is accepted: a buggy or malicious provider may
 * deliver a response to the wrong consumer provided that consumer is entitled to the object. No
 * data leakage results — the recipient could already query the data — and it is a provider-side
 * bug the broker does not police. Likewise, entitlement is evaluated against the service name the
 * submission <em>claims</em>; a provider that mislabels its payload is not detectable here.</p>
 *
 * <p>Applies to the <strong>public</strong> ingress path only. {@code /internal/**} is token-gated
 * and carries a {@code queueId} already validated at request admission by
 * {@code delayed-queue-ownership-fix}, so it is deliberately exempt.</p>
 *
 * <p><strong>Requirements:</strong> 2.1, 2.2, 2.3, 2.4, 2.5, 2.7</p>
 */
@Component
@Slf4j
public class ResponseDestinationValidator {

    private final QueueOwnerCache queueOwnerCache;
    private final DestinationEntitlementCache entitlementCache;

    public ResponseDestinationValidator(QueueOwnerCache queueOwnerCache,
                                        DestinationEntitlementCache entitlementCache) {
        this.queueOwnerCache = queueOwnerCache;
        this.entitlementCache = entitlementCache;
        log.info("ResponseDestinationValidator initialized");
    }

    /**
     * Rejects the submission unless the destination queue's owner is entitled to the object.
     *
     * <p>Fails closed on every uncertainty: an unresolvable owner and a failed entitlement lookup
     * both deny, because neither establishes that delivery is permitted. Both denial reasons throw
     * the same exception with the same client-visible code, so the response cannot be used to
     * determine whether a queue exists or who owns it (requirement 2.4) — the discriminating
     * detail goes to the WARN log instead (requirement 2.5).</p>
     *
     * @param domainId    the domain ID
     * @param solutionId  the solution ID
     * @param queueId     the caller-supplied destination queue
     * @param serviceName the SIF object/service the response claims to carry
     * @param zoneId      the zone qualifying the service
     * @param contextId   the context qualifying the service
     * @param callerEnvironmentId the submitting environment, for the audit log only — it is
     *                            deliberately NOT part of the decision
     * @throws ResponseDestinationDeniedException if delivery must not proceed
     */
    public void requireEntitledDestination(String domainId, String solutionId, UUID queueId,
                                           String serviceName, String zoneId, String contextId,
                                           UUID callerEnvironmentId) {

        UUID destinationOwner = queueOwnerCache.getQueueOwner(domainId, solutionId, queueId);

        if (destinationOwner == null) {
            // Fail closed: without an owner there is nothing to evaluate entitlement against.
            log.warn("Response destination denied — owner unresolvable: caller={}, queueId={}, "
                            + "service={}, zone={}, context={}",
                    callerEnvironmentId, queueId, serviceName, zoneId, contextId);
            throw new ResponseDestinationDeniedException();
        }

        boolean entitled = entitlementCache.isEntitled(
                domainId, solutionId, destinationOwner, serviceName, zoneId, contextId);

        if (!entitled) {
            log.warn("Response destination denied — owner holds no grant: caller={}, queueId={}, "
                            + "destinationOwner={}, service={}, zone={}, context={}",
                    callerEnvironmentId, queueId, destinationOwner, serviceName, zoneId, contextId);
            throw new ResponseDestinationDeniedException();
        }

        log.debug("Response destination permitted: queueId={}, destinationOwner={}, service={}",
                queueId, destinationOwner, serviceName);
    }
}
