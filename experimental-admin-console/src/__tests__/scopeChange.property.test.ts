import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as fc from 'fast-check';
import { renderHook, act } from '@testing-library/react';
import { createElement, type ReactNode } from 'react';

/**
 * Property 8: Scope change invalidates session
 *
 * For any authenticated user-mode session scoped to (domainId_A, solutionId_A),
 * navigating to a URL containing a different (domainId_B, solutionId_B) where at
 * least one component differs SHALL result in the session being invalidated
 * (authMode set to null, all session tokens cleared).
 *
 * **Validates: Requirements 5.5**
 */

// Mock auth utilities
vi.mock('../utils/auth', () => ({
  authenticatedFetch: vi.fn().mockResolvedValue(new Response('{}', { status: 200 })),
  proxyFetch: vi.fn().mockResolvedValue(new Response('{}', { status: 200 })),
}));

// Safe ISO date arbitrary using integer timestamps to avoid Invalid Date issues
const MIN_TIMESTAMP = new Date('2020-01-01T00:00:00Z').getTime();
const MAX_TIMESTAMP = new Date('2030-12-31T23:59:59Z').getTime();
const isoDateArb = fc.integer({ min: MIN_TIMESTAMP, max: MAX_TIMESTAMP }).map((ts) => new Date(ts).toISOString());

describe('Feature: admin-console-auth, Property 8: Scope change invalidates session', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('checkScopeChange invalidates session when at least one scope component differs', async () => {
    const { AuthProvider, useAuth } = await import('../context/AuthContext');

    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(AuthProvider, null, children);

    fc.assert(
      fc.property(
        // Generate two UUID pairs and filter so at least one component differs
        fc.uuid(),
        fc.uuid(),
        fc.uuid(),
        fc.uuid(),
        fc.uuid(), // adminSessionToken
        isoDateArb, // expiresAt
        (domainIdA, solutionIdA, domainIdB, solutionIdB, adminToken, expiresAt) => {
          // Ensure at least one component differs
          fc.pre(domainIdA !== domainIdB || solutionIdA !== solutionIdB);

          sessionStorage.clear();
          const { result } = renderHook(() => useAuth(), { wrapper });

          // Login as user with scope (domainId_A, solutionId_A)
          act(() => {
            result.current.loginUser(
              'testuser',
              adminToken,
              expiresAt,
              [{ id: 'env-1', applicationName: 'Test App' }],
              domainIdA,
              solutionIdA
            );
          });

          // Verify user mode is active
          expect(result.current.authMode).toBe('user');
          expect(result.current.adminSessionToken).toBe(adminToken);
          expect(result.current.domainId).toBe(domainIdA);
          expect(result.current.solutionId).toBe(solutionIdA);

          // Call checkScopeChange with different scope
          let scopeChanged: boolean = false;
          act(() => {
            scopeChanged = result.current.checkScopeChange(domainIdB, solutionIdB);
          });

          // Session should be invalidated
          expect(scopeChanged).toBe(true);
          expect(result.current.authMode).toBeNull();
          expect(result.current.adminSessionToken).toBeNull();
          expect(result.current.sessionToken).toBeNull();
          expect(result.current.sharedSecret).toBeNull();
          expect(result.current.domainId).toBeNull();
          expect(result.current.solutionId).toBeNull();
          expect(result.current.isAuthenticated).toBe(false);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('checkScopeChange does NOT invalidate session when scope is identical', async () => {
    const { AuthProvider, useAuth } = await import('../context/AuthContext');

    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(AuthProvider, null, children);

    fc.assert(
      fc.property(
        fc.uuid(),
        fc.uuid(),
        fc.uuid(), // adminSessionToken
        isoDateArb, // expiresAt
        (domainId, solutionId, adminToken, expiresAt) => {
          sessionStorage.clear();
          const { result } = renderHook(() => useAuth(), { wrapper });

          // Login as user
          act(() => {
            result.current.loginUser(
              'testuser',
              adminToken,
              expiresAt,
              [{ id: 'env-1', applicationName: 'Test App' }],
              domainId,
              solutionId
            );
          });

          // Call checkScopeChange with same scope
          let scopeChanged: boolean = false;
          act(() => {
            scopeChanged = result.current.checkScopeChange(domainId, solutionId);
          });

          // Session should remain intact
          expect(scopeChanged).toBe(false);
          expect(result.current.authMode).toBe('user');
          expect(result.current.adminSessionToken).toBe(adminToken);
          expect(result.current.domainId).toBe(domainId);
          expect(result.current.solutionId).toBe(solutionId);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('checkScopeChange returns false and does not invalidate when not in user mode', async () => {
    const { AuthProvider, useAuth } = await import('../context/AuthContext');

    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(AuthProvider, null, children);

    fc.assert(
      fc.property(
        fc.uuid(),
        fc.uuid(),
        fc.string({ minLength: 1, maxLength: 64 }),
        fc.string({ minLength: 1, maxLength: 64 }),
        (urlDomainId, urlSolutionId, sessionToken, sharedSecret) => {
          sessionStorage.clear();
          const { result } = renderHook(() => useAuth(), { wrapper });

          // Login in environment mode
          act(() => {
            result.current.loginEnvironment(sessionToken, sharedSecret);
          });

          // Call checkScopeChange — should not invalidate environment mode sessions
          let scopeChanged: boolean = false;
          act(() => {
            scopeChanged = result.current.checkScopeChange(urlDomainId, urlSolutionId);
          });

          expect(scopeChanged).toBe(false);
          expect(result.current.authMode).toBe('environment');
          expect(result.current.sessionToken).toBe(sessionToken);
        }
      ),
      { numRuns: 100 }
    );
  });
});
