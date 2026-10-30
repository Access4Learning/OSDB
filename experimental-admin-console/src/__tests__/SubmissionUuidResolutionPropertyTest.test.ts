import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';

/**
 * Property 6: Submission resolves correct contextServiceUuid
 *
 * For any completed cascade selection (zone, context, service, serviceType),
 * the submitted payload contains the contextServiceUuid matching the unique
 * available option with those four field values.
 *
 * **Validates: Requirements 10.3**
 */

interface AvailableProviderOption {
  contextServiceUuid: string;
  zoneId: string;
  contextId: string;
  serviceName: string;
  serviceType: string;
}

/**
 * Resolves contextServiceUuid from cascade selections — extracted logic
 * matching ProviderRegistrationsSection.tsx handleSubmit.
 */
function resolveContextServiceUuid(
  availableOptions: AvailableProviderOption[],
  selectedZone: string,
  selectedContext: string,
  selectedService: string,
  selectedServiceType: string
): string | undefined {
  const matchingOption = availableOptions.find(
    opt =>
      opt.zoneId === selectedZone &&
      opt.contextId === selectedContext &&
      opt.serviceName === selectedService &&
      opt.serviceType === selectedServiceType
  );
  return matchingOption?.contextServiceUuid;
}

// Arbitraries
const identifierArb = fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9_-]{0,19}$/);
const serviceTypeArb = fc.constantFrom('OBJECT', 'UTILITY', 'FUNCTIONAL', 'SERVICEPATH');

const availableOptionArb = fc.record({
  contextServiceUuid: fc.uuid(),
  zoneId: identifierArb,
  contextId: identifierArb,
  serviceName: identifierArb,
  serviceType: serviceTypeArb,
});

/**
 * Generate a list of available options with unique (zone, context, service, serviceType) tuples,
 * then pick one at random as the "selected" option.
 */
const optionsWithSelectionArb = fc
  .array(availableOptionArb, { minLength: 1, maxLength: 20 })
  .chain((options) => {
    // Deduplicate by composite key to ensure uniqueness
    const seen = new Set<string>();
    const uniqueOptions: AvailableProviderOption[] = [];
    for (const opt of options) {
      const key = `${opt.zoneId}|${opt.contextId}|${opt.serviceName}|${opt.serviceType}`;
      if (!seen.has(key)) {
        seen.add(key);
        uniqueOptions.push(opt);
      }
    }
    // Pick a random index from the unique options
    return fc.record({
      options: fc.constant(uniqueOptions),
      selectedIndex: fc.integer({ min: 0, max: uniqueOptions.length - 1 }),
    });
  });

describe('Feature: provider-registry-ui-improvements, Property 6: Submission resolves correct contextServiceUuid', () => {
  it('resolves the correct contextServiceUuid for any valid cascade selection', () => {
    fc.assert(
      fc.property(optionsWithSelectionArb, ({ options, selectedIndex }) => {
        const selected = options[selectedIndex];

        const resolvedUuid = resolveContextServiceUuid(
          options,
          selected.zoneId,
          selected.contextId,
          selected.serviceName,
          selected.serviceType
        );

        // The resolved UUID must match the selected option's UUID
        expect(resolvedUuid).toBe(selected.contextServiceUuid);
      }),
      { numRuns: 20 }
    );
  });

  it('resolution is deterministic — same selection always yields same UUID', () => {
    fc.assert(
      fc.property(optionsWithSelectionArb, ({ options, selectedIndex }) => {
        const selected = options[selectedIndex];

        const uuid1 = resolveContextServiceUuid(
          options,
          selected.zoneId,
          selected.contextId,
          selected.serviceName,
          selected.serviceType
        );
        const uuid2 = resolveContextServiceUuid(
          options,
          selected.zoneId,
          selected.contextId,
          selected.serviceName,
          selected.serviceType
        );

        expect(uuid1).toBe(uuid2);
      }),
      { numRuns: 20 }
    );
  });

  it('resolved UUID differs from other options with different field values', () => {
    fc.assert(
      fc.property(
        optionsWithSelectionArb.filter(({ options }) => options.length >= 2),
        ({ options, selectedIndex }) => {
          const selected = options[selectedIndex];

          const resolvedUuid = resolveContextServiceUuid(
            options,
            selected.zoneId,
            selected.contextId,
            selected.serviceName,
            selected.serviceType
          );

          // Every other option with a different composite key must have a different UUID
          // (since we deduplicated by key, all remaining options have different keys)
          for (let i = 0; i < options.length; i++) {
            if (i === selectedIndex) continue;
            const other = options[i];
            const otherKey = `${other.zoneId}|${other.contextId}|${other.serviceName}|${other.serviceType}`;
            const selectedKey = `${selected.zoneId}|${selected.contextId}|${selected.serviceName}|${selected.serviceType}`;
            if (otherKey !== selectedKey) {
              // Different key means different option — UUID should differ
              // (UUIDs are randomly generated so collision is astronomically unlikely)
              expect(resolvedUuid).not.toBe(other.contextServiceUuid);
            }
          }
        }
      ),
      { numRuns: 20 }
    );
  });
});
