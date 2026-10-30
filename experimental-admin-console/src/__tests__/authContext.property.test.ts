import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as fc from 'fast-check';
import { renderHook, act } from '@testing-library/react';
import { createElement, type ReactNode } from 'react';

/**
 * Property 1: Mode-based fetch dispatch
 *
 * For any API path and any auth state, when apiFetch is called:
 * - if authMode is "environment", the request dispatches to authenticatedFetch (HMAC)
 * - if authMode is "user", the request dispatches to proxyFetch (X-Admin-Session-Token)
 *
 * **Validates: Requirements 3.2, 3.3, 4.1**
 */

// Mock the auth utilities before importing AuthContext
vi.mock('../utils/auth', () => ({
  authenticatedFetch: vi.fn().mockResolvedValue(new Response('{}', { status: 200 })),
  proxyFetch: vi.fn().mockResolvedValue(new Response('{}', { status: 200 })),
}));

// Valid URL path arbitrary
const pathCharArb = fc.oneof(
  fc.constantFrom(...'abcdefghijklmnopqrstuvwxyz'.split('')),
  fc.constantFrom(...'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('')),
  fc.constantFrom(...'0123456789'.split('')),
  fc.constantFrom(...'-._~/'.split(''))
);

const validPathArb = fc
  .array(pathCharArb, { minLength: 1, maxLength: 50 })
  .map((chars) => chars.join(''))
  .map((s) => (s.startsWith('/') ? s : '/' + s))
  .filter((s) => !s.startsWith('//'));

// Safe ISO date arbitrary using integer timestamps to avoid Invalid Date issues
const MIN_TIMESTAMP = new Date('2020-01-01T00:00:00Z').getTime();
const MAX_TIMESTAMP = new Date('2030-12-31T23:59:59Z').getTime();
const isoDateArb = fc.integer({ min: MIN_TIMESTAMP, max: MAX_TIMESTAMP }).map((ts) => new Date(ts).toISOString());

describe('Feature: admin-console-auth, Property 1: Mode-based fetch dispatch', () => {
  let authenticatedFetch: ReturnType<typeof vi.fn>;
  let proxyFetch: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    // Clear sessionStorage before each test
    sessionStorage.clear();

    const authModule = await import('../utils/auth');
    authenticatedFetch = authModule.authenticatedFetch as unknown as ReturnType<typeof vi.fn>;
    proxyFetch = authModule.proxyFetch as unknown as ReturnType<typeof vi.fn>;

    authenticatedFetch.mockClear();
    proxyFetch.mockClear();
    authenticatedFetch.mockResolvedValue(new Response('{}', { status: 200 }));
    proxyFetch.mockResolvedValue(new Response('{}', { status: 200 }));
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('dispatches to authenticatedFetch when authMode is "environment" for any path', async () => {
    const { AuthProvider, useAuth } = await import('../context/AuthContext');

    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(AuthProvider, null, children);

    await fc.assert(
      fc.asyncProperty(validPathArb, async (path) => {
        authenticatedFetch.mockClear();
        proxyFetch.mockClear();
        sessionStorage.clear();

        const { result } = renderHook(() => useAuth(), { wrapper });

        // Login in environment mode
        act(() => {
          result.current.loginEnvironment('session-token-abc', 'shared-secret-xyz');
        });

        // Call apiFetch
        await act(async () => {
          await result.current.apiFetch(path);
        });

        // authenticatedFetch should be called, proxyFetch should NOT
        expect(authenticatedFetch).toHaveBeenCalledTimes(1);
        expect(authenticatedFetch).toHaveBeenCalledWith(
          path,
          'session-token-abc',
          'shared-secret-xyz',
          undefined
        );
        expect(proxyFetch).not.toHaveBeenCalled();
      }),
      { numRuns: 100 }
    );
  });

  it('dispatches to proxyFetch when authMode is "user" for any path', async () => {
    const { AuthProvider, useAuth } = await import('../context/AuthContext');

    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(AuthProvider, null, children);

    await fc.assert(
      fc.asyncProperty(validPathArb, async (path) => {
        authenticatedFetch.mockClear();
        proxyFetch.mockClear();
        sessionStorage.clear();

        const { result } = renderHook(() => useAuth(), { wrapper });

        // Login in user mode
        act(() => {
          result.current.loginUser(
            'testuser',
            'admin-session-token-123',
            '2025-12-31T23:59:59Z',
            [{ id: 'env-1', applicationName: 'Test App' }],
            'domain-uuid',
            'solution-uuid'
          );
        });

        // Call apiFetch
        await act(async () => {
          await result.current.apiFetch(path);
        });

        // proxyFetch should be called, authenticatedFetch should NOT
        expect(proxyFetch).toHaveBeenCalledTimes(1);
        expect(proxyFetch).toHaveBeenCalledWith(
          path,
          'admin-session-token-123',
          undefined
        );
        expect(authenticatedFetch).not.toHaveBeenCalled();
      }),
      { numRuns: 100 }
    );
  });
});


/**
 * Property 3: Admin session token visibility
 *
 * For any auth state where authMode is "environment" or null,
 * the adminSessionToken field SHALL be null.
 * The adminSessionToken SHALL only be non-null when authMode is "user".
 *
 * **Validates: Requirements 3.4**
 */
describe('Feature: admin-console-auth, Property 3: Admin session token visibility', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('adminSessionToken is null after loginEnvironment for any credentials', async () => {
    const { AuthProvider, useAuth } = await import('../context/AuthContext');

    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(AuthProvider, null, children);

    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 64 }),
        fc.string({ minLength: 1, maxLength: 64 }),
        (sessionToken, sharedSecret) => {
          sessionStorage.clear();
          const { result } = renderHook(() => useAuth(), { wrapper });

          act(() => {
            result.current.loginEnvironment(sessionToken, sharedSecret);
          });

          expect(result.current.authMode).toBe('environment');
          expect(result.current.adminSessionToken).toBeNull();
        }
      ),
      { numRuns: 100 }
    );
  });

  it('adminSessionToken is null when authMode is null (initial state / after logout)', async () => {
    const { AuthProvider, useAuth } = await import('../context/AuthContext');

    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(AuthProvider, null, children);

    fc.assert(
      fc.property(
        fc.uuid(),
        isoDateArb,
        fc.array(
          fc.record({ id: fc.uuid(), applicationName: fc.string({ minLength: 1, maxLength: 50 }) }),
          { minLength: 1, maxLength: 5 }
        ),
        fc.uuid(),
        fc.uuid(),
        (adminToken, expiresAt, environments, domainId, solutionId) => {
          sessionStorage.clear();
          const { result } = renderHook(() => useAuth(), { wrapper });

          // Login as user to set a token
          act(() => {
            result.current.loginUser('testuser', adminToken, expiresAt, environments, domainId, solutionId);
          });

          // Then logout — authMode becomes null
          act(() => {
            result.current.logout();
          });

          expect(result.current.authMode).toBeNull();
          expect(result.current.adminSessionToken).toBeNull();
        }
      ),
      { numRuns: 100 }
    );
  });

  it('adminSessionToken is non-null only when authMode is "user" for any token', async () => {
    const { AuthProvider, useAuth } = await import('../context/AuthContext');

    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(AuthProvider, null, children);

    fc.assert(
      fc.property(
        fc.uuid(),
        isoDateArb,
        fc.array(
          fc.record({ id: fc.uuid(), applicationName: fc.string({ minLength: 1, maxLength: 50 }) }),
          { minLength: 1, maxLength: 5 }
        ),
        fc.uuid(),
        fc.uuid(),
        (adminToken, expiresAt, environments, domainId, solutionId) => {
          sessionStorage.clear();
          const { result } = renderHook(() => useAuth(), { wrapper });

          act(() => {
            result.current.loginUser('testuser', adminToken, expiresAt, environments, domainId, solutionId);
          });

          expect(result.current.authMode).toBe('user');
          expect(result.current.adminSessionToken).toBe(adminToken);
          expect(result.current.adminSessionToken).not.toBeNull();
        }
      ),
      { numRuns: 100 }
    );
  });

  it('adminSessionToken becomes null when switching from user mode to environment mode', async () => {
    const { AuthProvider, useAuth } = await import('../context/AuthContext');

    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(AuthProvider, null, children);

    fc.assert(
      fc.property(
        fc.uuid(),
        isoDateArb,
        fc.array(
          fc.record({ id: fc.uuid(), applicationName: fc.string({ minLength: 1, maxLength: 50 }) }),
          { minLength: 1, maxLength: 5 }
        ),
        fc.uuid(),
        fc.uuid(),
        fc.string({ minLength: 1, maxLength: 64 }),
        fc.string({ minLength: 1, maxLength: 64 }),
        (adminToken, expiresAt, environments, domainId, solutionId, envSessionToken, envSharedSecret) => {
          sessionStorage.clear();
          const { result } = renderHook(() => useAuth(), { wrapper });

          // Login as user first
          act(() => {
            result.current.loginUser('testuser', adminToken, expiresAt, environments, domainId, solutionId);
          });

          expect(result.current.adminSessionToken).not.toBeNull();

          // Switch to environment mode
          act(() => {
            result.current.loginEnvironment(envSessionToken, envSharedSecret);
          });

          expect(result.current.authMode).toBe('environment');
          expect(result.current.adminSessionToken).toBeNull();
        }
      ),
      { numRuns: 100 }
    );
  });
});
