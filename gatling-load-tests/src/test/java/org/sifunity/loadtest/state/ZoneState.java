// SPDX-License-Identifier: MIT
package org.sifunity.loadtest.state;

/**
 * Holds per-zone state captured during setup: zone identity, provider and
 * consumer credentials, and consumer infrastructure IDs (queue, ACL,
 * subscription). Serialized to JSON between simulation phases.
 */
public record ZoneState(
    String zoneId,
    String contextUuid,
    String providerEnvId,
    String providerSessionToken,
    String providerSharedSecret,
    String consumerEnvId,
    String consumerSessionToken,
    String consumerSharedSecret,
    String consumerQueueId,
    String consumerAclId,
    String consumerSubscriptionId
) {}
