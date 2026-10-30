import { describe, it, expect, vi } from 'vitest';
import fc from 'fast-check';

/**
 * Property 7: URL scope extraction
 *
 * For any pair of UUID strings (domainId, solutionId) placed in the URL pattern
 * /admin/{domainId}/{solutionId}/..., the useAdminScope hook SHALL extract exactly
 * those two values as domainId and solutionId, and isValid SHALL be true.
 *
 * **Validates: Requirements 5.1**
 */

// Mock react-router-dom before importing the hook
vi.mock('react-router-dom', () => ({
  useParams: vi.fn(),
}));

import { useParams } from 'react-router-dom';
import { useAdminScope } from '../hooks/useAdminScope';

const mockedUseParams = vi.mocked(useParams);

describe('Feature: admin-console-auth, Property 7: URL scope extraction', () => {
  it('extracts domainId and solutionId from URL params and isValid is true for any UUID pair', () => {
    fc.assert(
      fc.property(fc.uuid(), fc.uuid(), (domainId, solutionId) => {
        mockedUseParams.mockReturnValue({ domainId, solutionId });

        const result = useAdminScope();

        expect(result.domainId).toBe(domainId);
        expect(result.solutionId).toBe(solutionId);
        expect(result.isValid).toBe(true);
      }),
      { numRuns: 100 }
    );
  });

  it('isValid is false when domainId is missing', () => {
    fc.assert(
      fc.property(fc.uuid(), (solutionId) => {
        mockedUseParams.mockReturnValue({ solutionId });

        const result = useAdminScope();

        expect(result.domainId).toBeUndefined();
        expect(result.solutionId).toBe(solutionId);
        expect(result.isValid).toBe(false);
      }),
      { numRuns: 100 }
    );
  });

  it('isValid is false when solutionId is missing', () => {
    fc.assert(
      fc.property(fc.uuid(), (domainId) => {
        mockedUseParams.mockReturnValue({ domainId });

        const result = useAdminScope();

        expect(result.domainId).toBe(domainId);
        expect(result.solutionId).toBeUndefined();
        expect(result.isValid).toBe(false);
      }),
      { numRuns: 100 }
    );
  });

  it('isValid is false when both params are missing', () => {
    mockedUseParams.mockReturnValue({});

    const result = useAdminScope();

    expect(result.domainId).toBeUndefined();
    expect(result.solutionId).toBeUndefined();
    expect(result.isValid).toBe(false);
  });
});
