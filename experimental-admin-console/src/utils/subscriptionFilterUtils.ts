export interface AclEntry {
  id: string;
  environmentId: string;
  serviceName: string;
  zoneId: string | null;
  contextId: string | null;
  accessType: string;
  accessLevel: string;
}

export interface ActiveSubscription {
  serviceName: string;
  zoneId: string | null;
  contextId: string | null;
  messageType: string;
}

export type MessageType = 'EVENT' | 'DELAYED_REQUEST';

export interface SubscriptionCombo {
  serviceName: string;
  zoneId: string | null;
  contextId: string | null;
  messageType: string;
}

/**
 * Builds the exclusion matching key for a subscription combo.
 */
function comboKey(serviceName: string, zoneId: string | null, contextId: string | null, messageType: string): string {
  return `${serviceName}|${zoneId ?? ''}|${contextId ?? ''}|${messageType}`;
}

/**
 * Returns the approved ACL entries relevant for the given message type.
 * EVENT → SUBSCRIBE + APPROVED entries
 * DELAYED_REQUEST → PROVIDE + APPROVED entries
 */
export function getEligibleAclEntries(
  aclEntries: AclEntry[],
  messageType: MessageType
): AclEntry[] {
  const requiredAccessType = messageType === 'EVENT' ? 'SUBSCRIBE' : 'PROVIDE';
  return aclEntries.filter(
    (entry) => entry.accessType === requiredAccessType && entry.accessLevel === 'APPROVED'
  );
}

/**
 * Returns distinct service names from eligible ACL entries,
 * excluding services where ALL combinations are already subscribed.
 * Result is sorted alphabetically ascending.
 */
export function getEligibleServiceNames(
  eligibleEntries: AclEntry[],
  activeSubscriptions: ActiveSubscription[],
  messageType: MessageType
): string[] {
  const subscribedKeys = new Set(
    activeSubscriptions
      .filter((sub) => sub.messageType === messageType)
      .map((sub) => comboKey(sub.serviceName, sub.zoneId, sub.contextId, sub.messageType))
  );

  // Group entries by service name
  const serviceEntries = new Map<string, AclEntry[]>();
  for (const entry of eligibleEntries) {
    const existing = serviceEntries.get(entry.serviceName);
    if (existing) {
      existing.push(entry);
    } else {
      serviceEntries.set(entry.serviceName, [entry]);
    }
  }

  // Include service only if at least one combo is NOT subscribed
  const result: string[] = [];
  for (const [serviceName, entries] of serviceEntries) {
    const allSubscribed = entries.every((entry) =>
      subscribedKeys.has(comboKey(entry.serviceName, entry.zoneId, entry.contextId, messageType))
    );
    if (!allSubscribed) {
      result.push(serviceName);
    }
  }

  return result.sort();
}

/**
 * Returns distinct zone IDs from eligible ACL entries matching the selected service,
 * excluding zones where ALL combinations for that service are already subscribed.
 * Result is sorted alphabetically ascending.
 */
export function getEligibleZoneIds(
  eligibleEntries: AclEntry[],
  selectedService: string,
  activeSubscriptions: ActiveSubscription[],
  messageType: MessageType
): string[] {
  const subscribedKeys = new Set(
    activeSubscriptions
      .filter((sub) => sub.messageType === messageType)
      .map((sub) => comboKey(sub.serviceName, sub.zoneId, sub.contextId, sub.messageType))
  );

  // Filter entries for the selected service
  const serviceEntries = eligibleEntries.filter((entry) => entry.serviceName === selectedService);

  // Group by zoneId
  const zoneEntries = new Map<string, AclEntry[]>();
  for (const entry of serviceEntries) {
    const zoneKey = entry.zoneId ?? '';
    const existing = zoneEntries.get(zoneKey);
    if (existing) {
      existing.push(entry);
    } else {
      zoneEntries.set(zoneKey, [entry]);
    }
  }

  // Include zone only if at least one combo is NOT subscribed
  const result: string[] = [];
  for (const [zoneId, entries] of zoneEntries) {
    const allSubscribed = entries.every((entry) =>
      subscribedKeys.has(comboKey(entry.serviceName, entry.zoneId, entry.contextId, messageType))
    );
    if (!allSubscribed) {
      result.push(zoneId);
    }
  }

  return result.sort();
}

/**
 * Returns distinct context IDs from eligible ACL entries matching the selected
 * service + zone, excluding contexts already subscribed.
 * Result is sorted alphabetically ascending.
 */
export function getEligibleContextIds(
  eligibleEntries: AclEntry[],
  selectedService: string,
  selectedZone: string,
  activeSubscriptions: ActiveSubscription[],
  messageType: MessageType
): string[] {
  const subscribedKeys = new Set(
    activeSubscriptions
      .filter((sub) => sub.messageType === messageType)
      .map((sub) => comboKey(sub.serviceName, sub.zoneId, sub.contextId, sub.messageType))
  );

  // Filter entries for selected service + zone
  // selectedZone === '' means null zoneId
  const serviceZoneEntries = eligibleEntries.filter((entry) => {
    const entryZoneKey = entry.zoneId ?? '';
    return entry.serviceName === selectedService && entryZoneKey === selectedZone;
  });

  // Get distinct contextIds, excluding already-subscribed ones
  const seen = new Set<string>();
  const result: string[] = [];

  for (const entry of serviceZoneEntries) {
    const contextKey = entry.contextId ?? '';
    if (seen.has(contextKey)) continue;
    seen.add(contextKey);

    const key = comboKey(entry.serviceName, entry.zoneId, entry.contextId, messageType);
    if (!subscribedKeys.has(key)) {
      result.push(contextKey);
    }
  }

  return result.sort();
}
