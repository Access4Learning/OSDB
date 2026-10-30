import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as fc from 'fast-check';
import { renderHook, act } from '@testing-library/react';
import { createElement, type ReactNode } from 'react';

/**
 * Property 9: Complete state cleanup on logout or expiry
 *
 * For any populated auth state (either mode), after a logout event,
 * ALL auth-related sessionStorage keys are removed and all corresponding
 * AuthContext fields are null/empty.
 *
 * **Validates: Requirements 7.2, 9.2**
 */

// Mock the auth utilities
vi.mock('../utils/auth', () => ({
  authenticatedFetch: vi.fn().mockResolvedValue(new Response('{}', { status: 200 })),
  proxyFetch: vi.fn().mockResolvedValue(new Response('{}', { status: 200 })),
}));

// All sessionStorage keys used by AuthContext
const ALL_STORAGE_KEYS = [
  'authMode',
  'sessionToken',
  'sharedSecret',
  'adminSessionToken',
  'adminExpiresAt',
  'adminEnvironments',
  'adminSelectedEnvironment',
  'adminDomainId',
  'adminSolutionId',
] as const;

// --- Arbitraries ---

const tokenArb = fc.string({ minLength: 1, maxLength: 64 }).filter((s) => s.trim().length > 0);
const uuidArb = fc.uuid();
const isoDateArb = fc.integer({ min: 1577836800000, max: 1924991999000 }).map((ts) => new Date(ts).toISOString());
const appNameArb = fc.string({ minLength: 1, maxLength: 50 }).filter((s) => s.trim().length > 0);

const environmentInfoArb = fc.record({
  id: uuidArb,
  applicationName: appNameArb,
});

const environmentsArb = fc.array(environmentInfoArb, { minLength: 1, maxLength: 5 });

/** Environment mode login params */
const envModeArb = fc.record({
  sessionToken: tokenArb,
  sharedSecret: tokenArb,
});

/** User mode login params */
const userModeArb = fc.record({
  adminSessionToken: tokenArb,
  expiresAt: isoDateArb,
  environments: environmentsArb,
  domainId: uuidArb,
  solutionId: uuidArb,
});

// --- Helpers ---

function wrapper({ children }: { children: ReactNode }) {
  return createElement(AuthProvider, null, children);
}

let AuthProvider: typeof import('../context/AuthContext').AuthProvider;
let useAuth: typeof import('../context/AuthContext').useAuth;

describe('Feature: admin-console-auth, Property 9: Complete state cleanup on logout', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    const mod = await import('../context/AuthContext');
    AuthProvider = mod.AuthProvider;
    useAuth = mod.useAuth;
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('all sessionStorage keys are removed and all context fields are null/empty after logout from environment mode', () => {
    fc.assert(
      fc.property(envModeArb, ({ sessionToken, sharedSecret }) => {
        sessionStorage.clear();

        const { result } = renderHook(() => useAuth(), { wrapper });

        // Populate state in environment mode
        act(() => {
          result.current.loginEnvironment(sessionToken, sharedSecret);
        });

        // Verify state is populated
        expect(result.current.authMode).toBe('environment');
        expect(result.current.isAuthenticated).toBe(true);
        expect(result.current.sessionToken).toBe(sessionToken);
        expect(result.current.sharedSecret).toBe(sharedSecret);

        // Logout
        act(() => {
          result.current.logout();
        });

        // Verify ALL sessionStorage keys are removed
        for (const key of ALL_STORAGE_KEYS) {
          expect(sessionStorage.getItem(key)).toBeNull();
        }

        // Verify all AuthContext fields are null/empty
        expect(result.current.authMode).toBeNull();
        expect(result.current.isAuthenticated).toBe(false);
        expect(result.current.sessionToken).toBeNull();
        expect(result.current.sharedSecret).toBeNull();
        expect(result.current.adminSessionToken).toBeNull();
        expect(result.current.expiresAt).toBeNull();
        expect(result.current.environments).toEqual([]);
        expect(result.current.selectedEnvironment).toBeNull();
        expect(result.current.domainId).toBeNull();
        expect(result.current.solutionId).toBeNull();
      }),
      { numRuns: 100 }
    );
  });

  it('all sessionStorage keys are removed and all context fields are null/empty after logout from user mode', () => {
    fc.assert(
      fc.property(userModeArb, ({ adminSessionToken, expiresAt, environments, domainId, solutionId }) => {
        sessionStorage.clear();

        const { result } = renderHook(() => useAuth(), { wrapper });

        // Populate state in user mode
        act(() => {
          result.current.loginUser('testuser', adminSessionToken, expiresAt, environments, domainId, solutionId);
        });

        // Verify state is populated
        expect(result.current.authMode).toBe('user');
        expect(result.current.isAuthenticated).toBe(true);
        expect(result.current.adminSessionToken).toBe(adminSessionToken);
        expect(result.current.expiresAt).toBe(expiresAt);
        expect(result.current.environments).toEqual(environments);
        expect(result.current.domainId).toBe(domainId);
        expect(result.current.solutionId).toBe(solutionId);

        // Logout
        act(() => {
          result.current.logout();
        });

        // Verify ALL sessionStorage keys are removed
        for (const key of ALL_STORAGE_KEYS) {
          expect(sessionStorage.getItem(key)).toBeNull();
        }

        // Verify all AuthContext fields are null/empty
        expect(result.current.authMode).toBeNull();
        expect(result.current.isAuthenticated).toBe(false);
        expect(result.current.sessionToken).toBeNull();
        expect(result.current.sharedSecret).toBeNull();
        expect(result.current.adminSessionToken).toBeNull();
        expect(result.current.expiresAt).toBeNull();
        expect(result.current.environments).toEqual([]);
        expect(result.current.selectedEnvironment).toBeNull();
        expect(result.current.domainId).toBeNull();
        expect(result.current.solutionId).toBeNull();
      }),
      { numRuns: 100 }
    );
  });

  it('all sessionStorage keys are removed after logout from user mode with selected environment', () => {
    fc.assert(
      fc.property(userModeArb, ({ adminSessionToken, expiresAt, environments, domainId, solutionId }) => {
        sessionStorage.clear();

        const { result } = renderHook(() => useAuth(), { wrapper });

        // Populate state in user mode with selected environment
        act(() => {
          result.current.loginUser('testuser', adminSessionToken, expiresAt, environments, domainId, solutionId);
        });

        act(() => {
          result.current.selectEnvironment(environments[0]);
        });

        // Verify selected environment is set
        expect(result.current.selectedEnvironment).toEqual(environments[0]);

        // Logout
        act(() => {
          result.current.logout();
        });

        // Verify ALL sessionStorage keys are removed
        for (const key of ALL_STORAGE_KEYS) {
          expect(sessionStorage.getItem(key)).toBeNull();
        }

        // Verify all AuthContext fields are null/empty
        expect(result.current.authMode).toBeNull();
        expect(result.current.isAuthenticated).toBe(false);
        expect(result.current.sessionToken).toBeNull();
        expect(result.current.sharedSecret).toBeNull();
        expect(result.current.adminSessionToken).toBeNull();
        expect(result.current.expiresAt).toBeNull();
        expect(result.current.environments).toEqual([]);
        expect(result.current.selectedEnvironment).toBeNull();
        expect(result.current.domainId).toBeNull();
        expect(result.current.solutionId).toBeNull();
      }),
      { numRuns: 100 }
    );
  });
});
