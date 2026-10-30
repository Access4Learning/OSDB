import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import * as fc from 'fast-check';
import { renderHook, act } from '@testing-library/react';
import { createElement, type ReactNode } from 'react';
import { AuthProvider, useAuth } from '../context/AuthContext';

/**
 * Property 4: Session state round-trip via sessionStorage
 *
 * For any valid auth state (either mode), persisting the state to sessionStorage
 * and then constructing a new AuthContext from sessionStorage SHALL produce an
 * equivalent auth state (same mode, same tokens, same environment list, same
 * selected environment).
 *
 * **Validates: Requirements 3.6**
 */

// --- Arbitraries ---

/** Non-empty alphanumeric string (simulates tokens, IDs) */
const tokenArb = fc.string({ minLength: 1, maxLength: 64 }).filter((s) => s.trim().length > 0);

/** UUID-like string arbitrary */
const uuidArb = fc.uuid();

/** ISO 8601 date string arbitrary */
const MIN_TIMESTAMP = new Date('2020-01-01T00:00:00Z').getTime();
const MAX_TIMESTAMP = new Date('2030-12-31T23:59:59Z').getTime();
const isoDateArb = fc.integer({ min: MIN_TIMESTAMP, max: MAX_TIMESTAMP }).map((ts) => new Date(ts).toISOString());

/** Application name arbitrary */
const appNameArb = fc.string({ minLength: 1, maxLength: 50 }).filter((s) => s.trim().length > 0);

/** EnvironmentInfo arbitrary */
const environmentInfoArb = fc.record({
  id: uuidArb,
  applicationName: appNameArb,
});

/** Non-empty list of environments */
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

describe('Feature: admin-console-auth, Property 4: Session state round-trip via sessionStorage', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('environment mode state survives unmount/remount via sessionStorage', async () => {
    await fc.assert(
      fc.asyncProperty(envModeArb, async ({ sessionToken, sharedSecret }) => {
        sessionStorage.clear();

        // Render first provider and login
        const { result, unmount } = renderHook(() => useAuth(), { wrapper });

        act(() => {
          result.current.loginEnvironment(sessionToken, sharedSecret);
        });

        // Verify state was set
        expect(result.current.authMode).toBe('environment');
        expect(result.current.sessionToken).toBe(sessionToken);
        expect(result.current.sharedSecret).toBe(sharedSecret);

        // Unmount (simulates page close / component teardown)
        unmount();

        // Render a new provider (reads from sessionStorage)
        const { result: result2, unmount: unmount2 } = renderHook(() => useAuth(), { wrapper });

        // Verify round-trip: state matches
        expect(result2.current.authMode).toBe('environment');
        expect(result2.current.isAuthenticated).toBe(true);
        expect(result2.current.sessionToken).toBe(sessionToken);
        expect(result2.current.sharedSecret).toBe(sharedSecret);

        // User mode fields should be null/empty
        expect(result2.current.adminSessionToken).toBeNull();
        expect(result2.current.expiresAt).toBeNull();
        expect(result2.current.environments).toEqual([]);
        expect(result2.current.selectedEnvironment).toBeNull();

        unmount2();
      }),
      { numRuns: 100 }
    );
  });

  it('user mode state survives unmount/remount via sessionStorage', async () => {
    await fc.assert(
      fc.asyncProperty(userModeArb, async ({ adminSessionToken, expiresAt, environments, domainId, solutionId }) => {
        sessionStorage.clear();

        // Render first provider and login as user
        const { result, unmount } = renderHook(() => useAuth(), { wrapper });

        act(() => {
          result.current.loginUser('testuser', adminSessionToken, expiresAt, environments, domainId, solutionId);
        });

        // Verify state was set
        expect(result.current.authMode).toBe('user');
        expect(result.current.adminSessionToken).toBe(adminSessionToken);
        expect(result.current.expiresAt).toBe(expiresAt);
        expect(result.current.environments).toEqual(environments);
        expect(result.current.domainId).toBe(domainId);
        expect(result.current.solutionId).toBe(solutionId);

        // Unmount
        unmount();

        // Render a new provider (reads from sessionStorage)
        const { result: result2, unmount: unmount2 } = renderHook(() => useAuth(), { wrapper });

        // Verify round-trip: state matches
        expect(result2.current.authMode).toBe('user');
        expect(result2.current.isAuthenticated).toBe(true);
        expect(result2.current.adminSessionToken).toBe(adminSessionToken);
        expect(result2.current.expiresAt).toBe(expiresAt);
        expect(result2.current.environments).toEqual(environments);
        expect(result2.current.domainId).toBe(domainId);
        expect(result2.current.solutionId).toBe(solutionId);

        // Environment mode fields should be null
        expect(result2.current.sessionToken).toBeNull();
        expect(result2.current.sharedSecret).toBeNull();

        unmount2();
      }),
      { numRuns: 100 }
    );
  });

  it('user mode with selected environment round-trips via sessionStorage', async () => {
    await fc.assert(
      fc.asyncProperty(userModeArb, async ({ adminSessionToken, expiresAt, environments, domainId, solutionId }) => {
        sessionStorage.clear();

        const selectedEnv = environments[0];

        // Render first provider, login, and select environment
        const { result, unmount } = renderHook(() => useAuth(), { wrapper });

        act(() => {
          result.current.loginUser('testuser', adminSessionToken, expiresAt, environments, domainId, solutionId);
        });

        act(() => {
          result.current.selectEnvironment(selectedEnv);
        });

        expect(result.current.selectedEnvironment).toEqual(selectedEnv);

        // Unmount
        unmount();

        // Render a new provider
        const { result: result2, unmount: unmount2 } = renderHook(() => useAuth(), { wrapper });

        // Verify selected environment round-trips
        expect(result2.current.authMode).toBe('user');
        expect(result2.current.selectedEnvironment).toEqual(selectedEnv);
        expect(result2.current.environments).toEqual(environments);
        expect(result2.current.adminSessionToken).toBe(adminSessionToken);

        unmount2();
      }),
      { numRuns: 100 }
    );
  });
});
