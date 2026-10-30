import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';

/**
 * Property 3: Cascade filtering produces correct dropdown values
 *
 * For any list of available provider options and any valid sequence of upstream
 * selections (zone, context, service), the dropdown at level N contains exactly
 * the distinct values at level N from available options that match all selections
 * at levels 1..N-1.
 *
 * **Validates: Requirements 5.1, 6.1, 7.1, 8.1**
 */

// --- Types mirroring the component ---

interface AvailableProviderOption {
  contextServiceUuid: string;
  zoneId: string;
  contextId: string;
  serviceName: string;
  serviceType: string;
}

// --- Pure cascade filtering functions (extracted from ProviderRegistrationsSection.tsx logic) ---

function getAvailableZones(options: AvailableProviderOption[]): string[] {
  return [...new Set(options.map(opt => opt.zoneId))].sort();
}

function getAvailableContexts(options: AvailableProviderOption[], selectedZone: string): string[] {
  return [...new Set(
    options
      .filter(opt => opt.zoneId === selectedZone)
      .map(opt => opt.contextId)
  )].sort();
}

function getAvailableServices(options: AvailableProviderOption[], selectedZone: string, selectedContext: string): string[] {
  return [...new Set(
    options
      .filter(opt => opt.zoneId === selectedZone && opt.contextId === selectedContext)
      .map(opt => opt.serviceName)
  )].sort();
}

function getAvailableServiceTypes(options: AvailableProviderOption[], selectedZone: string, selectedContext: string, selectedService: string): string[] {
  return [...new Set(
    options
      .filter(opt => opt.zoneId === selectedZone && opt.contextId === selectedContext && opt.serviceName === selectedService)
      .map(opt => opt.serviceType)
  )].sort();
}

// --- Arbitraries ---

const identifierArb = fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9_-]{0,19}$/);

const availableOptionArb: fc.Arbitrary<AvailableProviderOption> = fc.record({
  contextServiceUuid: fc.uuid(),
  zoneId: identifierArb,
  contextId: identifierArb,
  serviceName: identifierArb,
  serviceType: fc.constantFrom('OBJECT', 'UTILITY', 'FUNCTIONAL', 'SERVICEPATH'),
});

const optionsListArb = fc.array(availableOptionArb, { minLength: 1, maxLength: 30 });

describe('Feature: provider-registry-ui-improvements, Property 3: Cascade filtering produces correct dropdown values', () => {
  it('zone dropdown contains exactly the distinct zoneIds from all options', () => {
    fc.assert(
      fc.property(optionsListArb, (options) => {
        const zones = getAvailableZones(options);
        const expected = [...new Set(options.map(o => o.zoneId))].sort();

        expect(zones).toEqual(expected);
        // No duplicates
        expect(zones.length).toBe(new Set(zones).size);
      }),
      { numRuns: 20 }
    );
  });

  it('context dropdown contains exactly distinct contextIds matching selected zone', () => {
    fc.assert(
      fc.property(
        optionsListArb.chain(options => {
          const zones = [...new Set(options.map(o => o.zoneId))];
          return fc.record({
            options: fc.constant(options),
            selectedZone: fc.constantFrom(...zones),
          });
        }),
        ({ options, selectedZone }) => {
          const contexts = getAvailableContexts(options, selectedZone);
          const expected = [...new Set(
            options
              .filter(o => o.zoneId === selectedZone)
              .map(o => o.contextId)
          )].sort();

          expect(contexts).toEqual(expected);
          // No duplicates
          expect(contexts.length).toBe(new Set(contexts).size);
          // All contexts belong to selected zone
          for (const ctx of contexts) {
            expect(options.some(o => o.zoneId === selectedZone && o.contextId === ctx)).toBe(true);
          }
        }
      ),
      { numRuns: 20 }
    );
  });

  it('service dropdown contains exactly distinct serviceNames matching selected zone+context', () => {
    fc.assert(
      fc.property(
        optionsListArb.chain(options => {
          const zones = [...new Set(options.map(o => o.zoneId))];
          return fc.constantFrom(...zones).chain(selectedZone => {
            const contexts = [...new Set(
              options.filter(o => o.zoneId === selectedZone).map(o => o.contextId)
            )];
            return fc.record({
              options: fc.constant(options),
              selectedZone: fc.constant(selectedZone),
              selectedContext: fc.constantFrom(...contexts),
            });
          });
        }),
        ({ options, selectedZone, selectedContext }) => {
          const services = getAvailableServices(options, selectedZone, selectedContext);
          const expected = [...new Set(
            options
              .filter(o => o.zoneId === selectedZone && o.contextId === selectedContext)
              .map(o => o.serviceName)
          )].sort();

          expect(services).toEqual(expected);
          // No duplicates
          expect(services.length).toBe(new Set(services).size);
          // All services belong to selected zone+context
          for (const svc of services) {
            expect(options.some(o => o.zoneId === selectedZone && o.contextId === selectedContext && o.serviceName === svc)).toBe(true);
          }
        }
      ),
      { numRuns: 20 }
    );
  });

  it('serviceType dropdown contains exactly distinct serviceTypes matching zone+context+service', () => {
    fc.assert(
      fc.property(
        optionsListArb.chain(options => {
          const zones = [...new Set(options.map(o => o.zoneId))];
          return fc.constantFrom(...zones).chain(selectedZone => {
            const contexts = [...new Set(
              options.filter(o => o.zoneId === selectedZone).map(o => o.contextId)
            )];
            return fc.constantFrom(...contexts).chain(selectedContext => {
              const services = [...new Set(
                options.filter(o => o.zoneId === selectedZone && o.contextId === selectedContext).map(o => o.serviceName)
              )];
              return fc.record({
                options: fc.constant(options),
                selectedZone: fc.constant(selectedZone),
                selectedContext: fc.constant(selectedContext),
                selectedService: fc.constantFrom(...services),
              });
            });
          });
        }),
        ({ options, selectedZone, selectedContext, selectedService }) => {
          const serviceTypes = getAvailableServiceTypes(options, selectedZone, selectedContext, selectedService);
          const expected = [...new Set(
            options
              .filter(o => o.zoneId === selectedZone && o.contextId === selectedContext && o.serviceName === selectedService)
              .map(o => o.serviceType)
          )].sort();

          expect(serviceTypes).toEqual(expected);
          // No duplicates
          expect(serviceTypes.length).toBe(new Set(serviceTypes).size);
          // All service types belong to selected zone+context+service
          for (const st of serviceTypes) {
            expect(options.some(o =>
              o.zoneId === selectedZone &&
              o.contextId === selectedContext &&
              o.serviceName === selectedService &&
              o.serviceType === st
            )).toBe(true);
          }
        }
      ),
      { numRuns: 20 }
    );
  });

  it('each level N dropdown excludes values that do NOT match upstream selections', () => {
    fc.assert(
      fc.property(
        optionsListArb.chain(options => {
          const zones = [...new Set(options.map(o => o.zoneId))];
          return fc.constantFrom(...zones).chain(selectedZone => {
            const contexts = [...new Set(
              options.filter(o => o.zoneId === selectedZone).map(o => o.contextId)
            )];
            return fc.constantFrom(...contexts).chain(selectedContext => {
              const services = [...new Set(
                options.filter(o => o.zoneId === selectedZone && o.contextId === selectedContext).map(o => o.serviceName)
              )];
              return fc.record({
                options: fc.constant(options),
                selectedZone: fc.constant(selectedZone),
                selectedContext: fc.constant(selectedContext),
                selectedService: fc.constantFrom(...services),
              });
            });
          });
        }),
        ({ options, selectedZone, selectedContext, selectedService }) => {
          // Context dropdown must not contain contexts from other zones
          const contexts = getAvailableContexts(options, selectedZone);
          const contextsFromOtherZones = options
            .filter(o => o.zoneId !== selectedZone)
            .map(o => o.contextId);
          for (const foreignCtx of contextsFromOtherZones) {
            // A context ID may appear in multiple zones; only exclude if it ONLY exists in other zones
            if (!options.some(o => o.zoneId === selectedZone && o.contextId === foreignCtx)) {
              expect(contexts).not.toContain(foreignCtx);
            }
          }

          // Service dropdown must not contain services from other zone+context combos
          const services = getAvailableServices(options, selectedZone, selectedContext);
          const servicesFromOtherContexts = options
            .filter(o => o.zoneId === selectedZone && o.contextId !== selectedContext)
            .map(o => o.serviceName);
          for (const foreignSvc of servicesFromOtherContexts) {
            if (!options.some(o => o.zoneId === selectedZone && o.contextId === selectedContext && o.serviceName === foreignSvc)) {
              expect(services).not.toContain(foreignSvc);
            }
          }

          // ServiceType dropdown must not contain types from other zone+context+service combos
          const serviceTypes = getAvailableServiceTypes(options, selectedZone, selectedContext, selectedService);
          const typesFromOtherServices = options
            .filter(o => o.zoneId === selectedZone && o.contextId === selectedContext && o.serviceName !== selectedService)
            .map(o => o.serviceType);
          for (const foreignType of typesFromOtherServices) {
            if (!options.some(o => o.zoneId === selectedZone && o.contextId === selectedContext && o.serviceName === selectedService && o.serviceType === foreignType)) {
              expect(serviceTypes).not.toContain(foreignType);
            }
          }
        }
      ),
      { numRuns: 20 }
    );
  });
});
