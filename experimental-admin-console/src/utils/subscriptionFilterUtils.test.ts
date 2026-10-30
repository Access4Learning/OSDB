import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import {
  type AclEntry,
  type ActiveSubscription,
  type MessageType,
  getEligibleAclEntries,
  getEligibleServiceNames,
  getEligibleZoneIds,
  getEligibleContextIds,
} from './subscriptionFilterUtils';



/**
 * Feature: subscription-ui-acl-dropdowns
 * Property 1: Message type filtering returns only correct ACL type
 *
 * Validates: Requirements 1.2, 1.3, 3.1, 3.2
 *
 * For any list of AclEntry objects and for any message type (EVENT or DELAYED_REQUEST),
 * getEligibleAclEntries shall return ONLY entries where:
 * - EVENT → accessType === 'SUBSCRIBE' AND accessLevel === 'APPROVED'
 * - DELAYED_REQUEST → accessType === 'PROVIDE' AND accessLevel === 'APPROVED'
 */
describe('Property 1: Message type filtering returns only correct ACL type', () => {
  const ACCESS_TYPES = ['SUBSCRIBE', 'PROVIDE', 'QUERY', 'UPDATE', 'DELETE', 'CREATE'] as const;
  const ACCESS_LEVELS = ['APPROVED', 'REJECTED', 'PENDING', 'SUPPORTED', 'UNSUPPORTED', 'REQUESTED'] as const;

  const aclEntryArb: fc.Arbitrary<AclEntry> = fc.record({
    id: fc.uuid(),
    environmentId: fc.uuid(),
    serviceName: fc.string({ minLength: 1, maxLength: 20 }),
    zoneId: fc.oneof(fc.string({ minLength: 1, maxLength: 10 }), fc.constant(null)),
    contextId: fc.oneof(fc.string({ minLength: 1, maxLength: 10 }), fc.constant(null)),
    accessType: fc.constantFrom(...ACCESS_TYPES),
    accessLevel: fc.constantFrom(...ACCESS_LEVELS),
  });

  const messageTypeArb: fc.Arbitrary<MessageType> = fc.constantFrom('EVENT', 'DELAYED_REQUEST');

  it('should return only entries matching the correct accessType and APPROVED accessLevel for any message type', () => {
    fc.assert(
      fc.property(
        fc.array(aclEntryArb, { minLength: 0, maxLength: 50 }),
        messageTypeArb,
        (entries, messageType) => {
          const result = getEligibleAclEntries(entries, messageType);

          const expectedAccessType = messageType === 'EVENT' ? 'SUBSCRIBE' : 'PROVIDE';

          // Every entry in the result must have the correct accessType and APPROVED level
          for (const entry of result) {
            expect(entry.accessType).toBe(expectedAccessType);
            expect(entry.accessLevel).toBe('APPROVED');
          }

          // Every entry in the input that matches criteria must appear in the result
          const expectedEntries = entries.filter(
            (e) => e.accessType === expectedAccessType && e.accessLevel === 'APPROVED'
          );
          expect(result).toHaveLength(expectedEntries.length);
        }
      ),
      { numRuns: 100 }
    );
  });
});

/**
 * Feature: subscription-ui-acl-dropdowns
 * Property 2: Cascading dropdown options are distinct and sorted
 *
 * Validates: Requirements 2.1, 2.3, 2.5
 */
describe('Property 2: Cascading dropdown options are distinct and sorted', () => {
  const messageTypes: MessageType[] = ['EVENT', 'DELAYED_REQUEST'];

  // Generator for eligible AclEntry objects (already filtered: accessType=SUBSCRIBE, accessLevel=APPROVED)
  const eligibleEntryArb = fc.record({
    id: fc.uuid(),
    environmentId: fc.uuid(),
    serviceName: fc.stringMatching(/^[a-zA-Z0-9]{1,20}$/),
    zoneId: fc.oneof(fc.stringMatching(/^[a-zA-Z0-9]{1,15}$/), fc.constant(null)),
    contextId: fc.oneof(fc.stringMatching(/^[a-zA-Z0-9]{1,15}$/), fc.constant(null)),
    accessType: fc.constant('SUBSCRIBE'),
    accessLevel: fc.constant('APPROVED'),
  }) as fc.Arbitrary<AclEntry>;

  const eligibleEntriesArb = fc.array(eligibleEntryArb, { minLength: 0, maxLength: 30 });

  const messageTypeArb = fc.constantFrom(...messageTypes);

  function isSortedAscending(arr: string[]): boolean {
    for (let i = 1; i < arr.length; i++) {
      if (arr[i - 1] > arr[i]) return false;
    }
    return true;
  }

  function hasNoDuplicates(arr: string[]): boolean {
    return new Set(arr).size === arr.length;
  }

  it('getEligibleServiceNames returns distinct and sorted results', () => {
    fc.assert(
      fc.property(eligibleEntriesArb, messageTypeArb, (entries, messageType) => {
        const emptySubscriptions: ActiveSubscription[] = [];
        const result = getEligibleServiceNames(entries, emptySubscriptions, messageType);

        expect(hasNoDuplicates(result)).toBe(true);
        expect(isSortedAscending(result)).toBe(true);
      }),
      { numRuns: 100 }
    );
  });

  it('getEligibleZoneIds returns distinct and sorted results for a valid selected service', () => {
    // Generate entries then pick a service name from the generated entries
    const entriesWithServiceArb = eligibleEntriesArb
      .filter((entries) => entries.length > 0)
      .chain((entries) => {
        const serviceNames = [...new Set(entries.map((e) => e.serviceName))];
        return fc.tuple(fc.constant(entries), fc.constantFrom(...serviceNames));
      });

    fc.assert(
      fc.property(entriesWithServiceArb, messageTypeArb, ([entries, selectedService], messageType) => {
        const emptySubscriptions: ActiveSubscription[] = [];
        const result = getEligibleZoneIds(entries, selectedService, emptySubscriptions, messageType);

        expect(hasNoDuplicates(result)).toBe(true);
        expect(isSortedAscending(result)).toBe(true);
      }),
      { numRuns: 100 }
    );
  });

  it('getEligibleContextIds returns distinct and sorted results for a valid selected service and zone', () => {
    // Generate entries then pick a service+zone combination from the generated entries
    const entriesWithServiceZoneArb = eligibleEntriesArb
      .filter((entries) => entries.length > 0)
      .chain((entries) => {
        const serviceNames = [...new Set(entries.map((e) => e.serviceName))];
        return fc.tuple(
          fc.constant(entries),
          fc.constantFrom(...serviceNames)
        );
      })
      .chain(([entries, selectedService]) => {
        const zonesForService = [
          ...new Set(
            entries
              .filter((e) => e.serviceName === selectedService)
              .map((e) => e.zoneId ?? '')
          ),
        ];
        return fc.tuple(
          fc.constant(entries),
          fc.constant(selectedService),
          fc.constantFrom(...zonesForService)
        );
      });

    fc.assert(
      fc.property(entriesWithServiceZoneArb, messageTypeArb, ([entries, selectedService, selectedZone], messageType) => {
        const emptySubscriptions: ActiveSubscription[] = [];
        const result = getEligibleContextIds(entries, selectedService, selectedZone, emptySubscriptions, messageType);

        expect(hasNoDuplicates(result)).toBe(true);
        expect(isSortedAscending(result)).toBe(true);
      }),
      { numRuns: 100 }
    );
  });
});

describe('subscriptionFilterUtils - edge cases', () => {
  it('empty ACL entries returns empty service names', () => {
    const result = getEligibleServiceNames([], [], 'EVENT');
    expect(result).toEqual([]);
  });

  it('single ACL entry appears as sole option', () => {
    const entry: AclEntry = {
      id: '1',
      environmentId: 'env1',
      serviceName: 'StudentPersonals',
      zoneId: 'zone-1',
      contextId: 'DEFAULT',
      accessType: 'SUBSCRIBE',
      accessLevel: 'APPROVED',
    };

    expect(getEligibleServiceNames([entry], [], 'EVENT')).toEqual(['StudentPersonals']);
    expect(getEligibleZoneIds([entry], 'StudentPersonals', [], 'EVENT')).toEqual(['zone-1']);
    expect(getEligibleContextIds([entry], 'StudentPersonals', 'zone-1', [], 'EVENT')).toEqual(['DEFAULT']);
  });

  it('null zoneId/contextId treated correctly in matching', () => {
    const entry: AclEntry = {
      id: '2',
      environmentId: 'env1',
      serviceName: 'Svc',
      zoneId: null,
      contextId: null,
      accessType: 'SUBSCRIBE',
      accessLevel: 'APPROVED',
    };

    // null becomes empty string in results
    expect(getEligibleZoneIds([entry], 'Svc', [], 'EVENT')).toEqual(['']);
    expect(getEligibleContextIds([entry], 'Svc', '', [], 'EVENT')).toEqual(['']);

    // When subscribed with null values, service is fully subscribed
    const sub: ActiveSubscription = {
      serviceName: 'Svc',
      zoneId: null,
      contextId: null,
      messageType: 'EVENT',
    };
    expect(getEligibleServiceNames([entry], [sub], 'EVENT')).toEqual([]);
  });

  it('fully-subscribed service omitted from service list', () => {
    const entries: AclEntry[] = [
      { id: '1', environmentId: 'env1', serviceName: 'Svc1', zoneId: 'z1', contextId: 'c1', accessType: 'SUBSCRIBE', accessLevel: 'APPROVED' },
      { id: '2', environmentId: 'env1', serviceName: 'Svc2', zoneId: 'z1', contextId: 'c1', accessType: 'SUBSCRIBE', accessLevel: 'APPROVED' },
    ];
    const subs: ActiveSubscription[] = [
      { serviceName: 'Svc1', zoneId: 'z1', contextId: 'c1', messageType: 'EVENT' },
    ];

    expect(getEligibleServiceNames(entries, subs, 'EVENT')).toEqual(['Svc2']);
  });
});



/**
 * Feature: subscription-ui-acl-dropdowns
 * Property 3: Subscribed combinations are excluded from dropdown options
 *
 * Validates: Requirements 4.1, 4.2, 4.3
 *
 * For any set of eligible ACL entries and any set of active subscriptions:
 * 1. No combination (serviceName, zoneId, contextId, messageType) that appears in active
 *    subscriptions shall appear as a selectable option in the dropdown chain
 * 2. Every eligible combination that does NOT appear in active subscriptions shall remain
 *    selectable through the cascading dropdowns
 */
describe('Property 3: Subscribed combinations are excluded from dropdown options', () => {
  // Generator for eligible entries (already filtered: accessType=SUBSCRIBE, accessLevel=APPROVED)
  const eligibleEntryArb3: fc.Arbitrary<AclEntry> = fc.record({
    id: fc.uuid(),
    environmentId: fc.uuid(),
    serviceName: fc.stringMatching(/^[A-Za-z][A-Za-z0-9]{0,9}$/),
    zoneId: fc.oneof(fc.stringMatching(/^[a-z][a-z0-9-]{0,7}$/), fc.constant(null)),
    contextId: fc.oneof(fc.stringMatching(/^[A-Z][A-Z0-9]{0,5}$/), fc.constant(null)),
    accessType: fc.constant('SUBSCRIBE'),
    accessLevel: fc.constant('APPROVED'),
  });

  it('no subscribed combo is reachable through the cascade and all non-subscribed combos remain selectable', () => {
    fc.assert(
      fc.property(
        fc.array(eligibleEntryArb3, { minLength: 1, maxLength: 30 }),
        fc.nat({ max: 100 }),
        (eligibleEntries, subSeed) => {
          const messageType: MessageType = 'EVENT';

          // Derive all unique combos from the eligible entries
          const allCombos: { serviceName: string; zoneId: string | null; contextId: string | null }[] = [];
          const comboSet = new Set<string>();
          for (const entry of eligibleEntries) {
            const key = `${entry.serviceName}|${entry.zoneId ?? ''}|${entry.contextId ?? ''}`;
            if (!comboSet.has(key)) {
              comboSet.add(key);
              allCombos.push({ serviceName: entry.serviceName, zoneId: entry.zoneId, contextId: entry.contextId });
            }
          }

          // Use subSeed to deterministically select a subset as active subscriptions
          const activeSubscriptions: ActiveSubscription[] = allCombos
            .filter((_, i) => (subSeed + i) % 3 === 0) // Subscribe roughly 1/3 of combos
            .map((combo) => ({
              serviceName: combo.serviceName,
              zoneId: combo.zoneId,
              contextId: combo.contextId,
              messageType,
            }));

          const subscribedKeySet = new Set(
            activeSubscriptions.map(
              (sub) => `${sub.serviceName}|${sub.zoneId ?? ''}|${sub.contextId ?? ''}|${sub.messageType}`
            )
          );

          // Determine which combos are NOT subscribed (should be reachable)
          const availableCombos = allCombos.filter(
            (combo) => !subscribedKeySet.has(`${combo.serviceName}|${combo.zoneId ?? ''}|${combo.contextId ?? ''}|${messageType}`)
          );

          // Walk the cascade and collect all reachable combos
          const serviceNames = getEligibleServiceNames(eligibleEntries, activeSubscriptions, messageType);
          const reachableCombos: string[] = [];

          for (const svc of serviceNames) {
            const zoneIds = getEligibleZoneIds(eligibleEntries, svc, activeSubscriptions, messageType);
            for (const zone of zoneIds) {
              const contextIds = getEligibleContextIds(eligibleEntries, svc, zone, activeSubscriptions, messageType);
              for (const ctx of contextIds) {
                reachableCombos.push(`${svc}|${zone}|${ctx}|${messageType}`);
              }
            }
          }

          // ASSERTION 1: No subscribed combo appears in the reachable set
          for (const key of reachableCombos) {
            expect(subscribedKeySet.has(key)).toBe(false);
          }

          // ASSERTION 2: Every non-subscribed combo IS reachable
          for (const combo of availableCombos) {
            const comboKey = `${combo.serviceName}|${combo.zoneId ?? ''}|${combo.contextId ?? ''}|${messageType}`;
            expect(reachableCombos).toContain(comboKey);
          }
        }
      ),
      { numRuns: 100 }
    );
  });
});
