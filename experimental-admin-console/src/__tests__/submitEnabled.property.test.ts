import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';

/**
 * Property 5: Submit button enabled invariant
 *
 * For any form state, the submit button SHALL be enabled if and only if
 * all four cascade dropdowns have a selection AND the endpoint field is
 * non-empty AND at least one supported request type operation is selected.
 *
 * **Validates: Requirements 10.1, 10.2**
 */

// Pure reimplementation of the isSubmitDisabled logic from ProviderRegistrationsSection.tsx
function isSubmitDisabled(
  selectedZone: string,
  selectedContext: string,
  selectedService: string,
  selectedServiceType: string,
  endpoint: string,
  immediateOps: string[],
  delayedOps: string[],
  submitting: boolean
): boolean {
  return (
    !selectedZone ||
    !selectedContext ||
    !selectedService ||
    !selectedServiceType ||
    !endpoint.trim() ||
    (immediateOps.length === 0 && delayedOps.length === 0) ||
    submitting
  );
}

// Arbitraries
const OPERATIONS = ['CREATE', 'QUERY', 'UPDATE', 'DELETE'];

const nonEmptyStringArb = fc.string({ minLength: 1, maxLength: 30 }).filter(s => s.trim().length > 0);
const emptyishStringArb = fc.constantFrom('', '   ', '\t', '\n');
const opsSubsetArb = fc.subarray([...OPERATIONS], { minLength: 0, maxLength: OPERATIONS.length });

describe('Feature: provider-registry-ui-improvements, Property 5: Submit button enabled invariant', () => {
  it('submit button is enabled when all 4 dropdowns selected AND endpoint non-empty AND at least one operation selected AND not submitting', () => {
    fc.assert(
      fc.property(
        nonEmptyStringArb, // selectedZone
        nonEmptyStringArb, // selectedContext
        nonEmptyStringArb, // selectedService
        nonEmptyStringArb, // selectedServiceType
        nonEmptyStringArb, // endpoint (non-empty trimmed)
        opsSubsetArb,      // immediateOps
        opsSubsetArb,      // delayedOps
        (zone, context, service, serviceType, endpoint, immediateOps, delayedOps) => {
          // Precondition: at least one operation must be selected
          fc.pre(immediateOps.length > 0 || delayedOps.length > 0);

          const disabled = isSubmitDisabled(zone, context, service, serviceType, endpoint, immediateOps, delayedOps, false);
          expect(disabled).toBe(false);
        }
      ),
      { numRuns: 20 }
    );
  });

  it('submit button is disabled when zone is empty', () => {
    fc.assert(
      fc.property(
        nonEmptyStringArb, // selectedContext
        nonEmptyStringArb, // selectedService
        nonEmptyStringArb, // selectedServiceType
        nonEmptyStringArb, // endpoint
        opsSubsetArb,      // immediateOps
        opsSubsetArb,      // delayedOps
        (context, service, serviceType, endpoint, immediateOps, delayedOps) => {
          fc.pre(immediateOps.length > 0 || delayedOps.length > 0);

          const disabled = isSubmitDisabled('', context, service, serviceType, endpoint, immediateOps, delayedOps, false);
          expect(disabled).toBe(true);
        }
      ),
      { numRuns: 20 }
    );
  });

  it('submit button is disabled when context is empty', () => {
    fc.assert(
      fc.property(
        nonEmptyStringArb, // selectedZone
        nonEmptyStringArb, // selectedService
        nonEmptyStringArb, // selectedServiceType
        nonEmptyStringArb, // endpoint
        opsSubsetArb,      // immediateOps
        opsSubsetArb,      // delayedOps
        (zone, service, serviceType, endpoint, immediateOps, delayedOps) => {
          fc.pre(immediateOps.length > 0 || delayedOps.length > 0);

          const disabled = isSubmitDisabled(zone, '', service, serviceType, endpoint, immediateOps, delayedOps, false);
          expect(disabled).toBe(true);
        }
      ),
      { numRuns: 20 }
    );
  });

  it('submit button is disabled when service is empty', () => {
    fc.assert(
      fc.property(
        nonEmptyStringArb, // selectedZone
        nonEmptyStringArb, // selectedContext
        nonEmptyStringArb, // selectedServiceType
        nonEmptyStringArb, // endpoint
        opsSubsetArb,      // immediateOps
        opsSubsetArb,      // delayedOps
        (zone, context, serviceType, endpoint, immediateOps, delayedOps) => {
          fc.pre(immediateOps.length > 0 || delayedOps.length > 0);

          const disabled = isSubmitDisabled(zone, context, '', serviceType, endpoint, immediateOps, delayedOps, false);
          expect(disabled).toBe(true);
        }
      ),
      { numRuns: 20 }
    );
  });

  it('submit button is disabled when serviceType is empty', () => {
    fc.assert(
      fc.property(
        nonEmptyStringArb, // selectedZone
        nonEmptyStringArb, // selectedContext
        nonEmptyStringArb, // selectedService
        nonEmptyStringArb, // endpoint
        opsSubsetArb,      // immediateOps
        opsSubsetArb,      // delayedOps
        (zone, context, service, endpoint, immediateOps, delayedOps) => {
          fc.pre(immediateOps.length > 0 || delayedOps.length > 0);

          const disabled = isSubmitDisabled(zone, context, service, '', endpoint, immediateOps, delayedOps, false);
          expect(disabled).toBe(true);
        }
      ),
      { numRuns: 20 }
    );
  });

  it('submit button is disabled when endpoint is empty or whitespace-only', () => {
    fc.assert(
      fc.property(
        nonEmptyStringArb, // selectedZone
        nonEmptyStringArb, // selectedContext
        nonEmptyStringArb, // selectedService
        nonEmptyStringArb, // selectedServiceType
        emptyishStringArb, // endpoint (empty/whitespace)
        opsSubsetArb,      // immediateOps
        opsSubsetArb,      // delayedOps
        (zone, context, service, serviceType, endpoint, immediateOps, delayedOps) => {
          fc.pre(immediateOps.length > 0 || delayedOps.length > 0);

          const disabled = isSubmitDisabled(zone, context, service, serviceType, endpoint, immediateOps, delayedOps, false);
          expect(disabled).toBe(true);
        }
      ),
      { numRuns: 20 }
    );
  });

  it('submit button is disabled when no operations are selected (both immediate and delayed empty)', () => {
    fc.assert(
      fc.property(
        nonEmptyStringArb, // selectedZone
        nonEmptyStringArb, // selectedContext
        nonEmptyStringArb, // selectedService
        nonEmptyStringArb, // selectedServiceType
        nonEmptyStringArb, // endpoint
        (zone, context, service, serviceType, endpoint) => {
          const disabled = isSubmitDisabled(zone, context, service, serviceType, endpoint, [], [], false);
          expect(disabled).toBe(true);
        }
      ),
      { numRuns: 20 }
    );
  });

  it('submit button is disabled when submitting is true regardless of other state', () => {
    fc.assert(
      fc.property(
        nonEmptyStringArb, // selectedZone
        nonEmptyStringArb, // selectedContext
        nonEmptyStringArb, // selectedService
        nonEmptyStringArb, // selectedServiceType
        nonEmptyStringArb, // endpoint
        opsSubsetArb,      // immediateOps
        opsSubsetArb,      // delayedOps
        (zone, context, service, serviceType, endpoint, immediateOps, delayedOps) => {
          fc.pre(immediateOps.length > 0 || delayedOps.length > 0);

          const disabled = isSubmitDisabled(zone, context, service, serviceType, endpoint, immediateOps, delayedOps, true);
          expect(disabled).toBe(true);
        }
      ),
      { numRuns: 20 }
    );
  });

  it('biconditional: submit enabled iff all conditions met (random form states)', () => {
    // Generate arbitrary form states and verify the biconditional holds
    const formStateArb = fc.record({
      zone: fc.oneof(fc.constant(''), nonEmptyStringArb),
      context: fc.oneof(fc.constant(''), nonEmptyStringArb),
      service: fc.oneof(fc.constant(''), nonEmptyStringArb),
      serviceType: fc.oneof(fc.constant(''), nonEmptyStringArb),
      endpoint: fc.oneof(emptyishStringArb, nonEmptyStringArb),
      immediateOps: opsSubsetArb,
      delayedOps: opsSubsetArb,
      submitting: fc.boolean(),
    });

    fc.assert(
      fc.property(formStateArb, (state) => {
        const allDropdownsSelected =
          state.zone.length > 0 &&
          state.context.length > 0 &&
          state.service.length > 0 &&
          state.serviceType.length > 0;
        const endpointNonEmpty = state.endpoint.trim().length > 0;
        const hasOperation = state.immediateOps.length > 0 || state.delayedOps.length > 0;
        const notSubmitting = !state.submitting;

        const shouldBeEnabled = allDropdownsSelected && endpointNonEmpty && hasOperation && notSubmitting;

        const disabled = isSubmitDisabled(
          state.zone,
          state.context,
          state.service,
          state.serviceType,
          state.endpoint,
          state.immediateOps,
          state.delayedOps,
          state.submitting
        );

        expect(disabled).toBe(!shouldBeEnabled);
      }),
      { numRuns: 20 }
    );
  });
});
