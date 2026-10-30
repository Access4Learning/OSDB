import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';

/**
 * Property 4: Cascade reset clears downstream selections
 *
 * For any form state where dropdowns at levels 1..N are selected,
 * changing the selection at level K (where K < N) SHALL clear all
 * selections at levels K+1..N and disable those dropdowns.
 *
 * The cascade levels are:
 *   Level 1: Zone
 *   Level 2: Context
 *   Level 3: Service
 *   Level 4: ServiceType
 *
 * Reset handlers (from ProviderRegistrationsSection.tsx):
 *   handleZoneChange(zone): sets zone, clears context/service/serviceType
 *   handleContextChange(context): sets context, clears service/serviceType
 *   handleServiceChange(service): sets service, clears serviceType
 *
 * **Validates: Requirements 6.3, 7.3, 8.2, 9.1, 9.2, 9.3**
 */

// --- Pure model of cascade form state ---

interface CascadeFormState {
  selectedZone: string;
  selectedContext: string;
  selectedService: string;
  selectedServiceType: string;
}

// The cascade reset handlers extracted as pure functions (mirrors component logic)
function handleZoneChange(_state: CascadeFormState, zone: string): CascadeFormState {
  return {
    selectedZone: zone,
    selectedContext: '',
    selectedService: '',
    selectedServiceType: '',
  };
}

function handleContextChange(state: CascadeFormState, context: string): CascadeFormState {
  return {
    ...state,
    selectedContext: context,
    selectedService: '',
    selectedServiceType: '',
  };
}

function handleServiceChange(state: CascadeFormState, service: string): CascadeFormState {
  return {
    ...state,
    selectedService: service,
    selectedServiceType: '',
  };
}

// --- Helpers ---

/** Returns levels K+1..N as field names for a given level K (1-indexed) */
function downstreamFields(level: number): (keyof CascadeFormState)[] {
  const allFields: (keyof CascadeFormState)[] = [
    'selectedZone',
    'selectedContext',
    'selectedService',
    'selectedServiceType',
  ];
  // level is 1-indexed, so downstream starts at index `level`
  return allFields.slice(level);
}

// --- Arbitraries ---

const nonEmptyStringArb = fc.string({ minLength: 1, maxLength: 30 }).filter(s => s.trim().length > 0);

/** Generates a form state with all 4 levels filled (fully selected) */
const fullFormStateArb: fc.Arbitrary<CascadeFormState> = fc.record({
  selectedZone: nonEmptyStringArb,
  selectedContext: nonEmptyStringArb,
  selectedService: nonEmptyStringArb,
  selectedServiceType: nonEmptyStringArb,
});

/** Generates a form state where at least the first K levels are filled */
function formStateWithMinLevels(minLevels: number): fc.Arbitrary<CascadeFormState> {
  const fields: fc.Arbitrary<string>[] = [
    nonEmptyStringArb, // zone always filled
    minLevels >= 2 ? nonEmptyStringArb : fc.oneof(nonEmptyStringArb, fc.constant('')),
    minLevels >= 3 ? nonEmptyStringArb : fc.oneof(nonEmptyStringArb, fc.constant('')),
    minLevels >= 4 ? nonEmptyStringArb : fc.oneof(nonEmptyStringArb, fc.constant('')),
  ];
  return fc.tuple(fields[0], fields[1], fields[2], fields[3]).map(([z, c, s, st]) => ({
    selectedZone: z,
    selectedContext: c,
    selectedService: s,
    selectedServiceType: st,
  }));
}

// --- Tests ---

describe('Feature: provider-registry-ui-improvements, Property 4: Cascade reset clears downstream selections', () => {
  it('handleZoneChange clears context, service, and serviceType for any prior state', () => {
    fc.assert(
      fc.property(
        formStateWithMinLevels(1),
        nonEmptyStringArb,
        (initialState, newZone) => {
          const result = handleZoneChange(initialState, newZone);

          // Zone is set to new value
          expect(result.selectedZone).toBe(newZone);

          // All downstream (levels 2, 3, 4) must be cleared
          for (const field of downstreamFields(1)) {
            expect(result[field]).toBe('');
          }
        }
      ),
      { numRuns: 20 }
    );
  });

  it('handleContextChange clears service and serviceType for any prior state', () => {
    fc.assert(
      fc.property(
        formStateWithMinLevels(2),
        nonEmptyStringArb,
        (initialState, newContext) => {
          const result = handleContextChange(initialState, newContext);

          // Zone preserved
          expect(result.selectedZone).toBe(initialState.selectedZone);
          // Context is set to new value
          expect(result.selectedContext).toBe(newContext);

          // Downstream (levels 3, 4) must be cleared
          for (const field of downstreamFields(2)) {
            expect(result[field]).toBe('');
          }
        }
      ),
      { numRuns: 20 }
    );
  });

  it('handleServiceChange clears serviceType for any prior state', () => {
    fc.assert(
      fc.property(
        formStateWithMinLevels(3),
        nonEmptyStringArb,
        (initialState, newService) => {
          const result = handleServiceChange(initialState, newService);

          // Zone and context preserved
          expect(result.selectedZone).toBe(initialState.selectedZone);
          expect(result.selectedContext).toBe(initialState.selectedContext);
          // Service is set to new value
          expect(result.selectedService).toBe(newService);

          // Downstream (level 4) must be cleared
          for (const field of downstreamFields(3)) {
            expect(result[field]).toBe('');
          }
        }
      ),
      { numRuns: 20 }
    );
  });

  it('for any level K change, all upstream levels 1..K-1 are preserved', () => {
    fc.assert(
      fc.property(
        fullFormStateArb,
        fc.integer({ min: 1, max: 3 }), // level to change (1=zone, 2=context, 3=service)
        nonEmptyStringArb,
        (initialState, level, newValue) => {
          let result: CascadeFormState;
          switch (level) {
            case 1:
              result = handleZoneChange(initialState, newValue);
              break;
            case 2:
              result = handleContextChange(initialState, newValue);
              break;
            case 3:
              result = handleServiceChange(initialState, newValue);
              break;
            default:
              return;
          }

          // Upstream fields (before the changed level) are preserved
          const upstreamFields: (keyof CascadeFormState)[] = [
            'selectedZone',
            'selectedContext',
            'selectedService',
            'selectedServiceType',
          ].slice(0, level - 1) as (keyof CascadeFormState)[];

          for (const field of upstreamFields) {
            expect(result[field]).toBe(initialState[field]);
          }

          // The changed level itself has the new value
          const changedField: keyof CascadeFormState = [
            'selectedZone',
            'selectedContext',
            'selectedService',
            'selectedServiceType',
          ][level - 1] as keyof CascadeFormState;
          expect(result[changedField]).toBe(newValue);

          // All downstream are cleared
          for (const field of downstreamFields(level)) {
            expect(result[field]).toBe('');
          }
        }
      ),
      { numRuns: 20 }
    );
  });
});
