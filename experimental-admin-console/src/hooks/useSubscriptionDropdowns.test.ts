import { renderHook, act, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';
import { useSubscriptionDropdowns, AuthFetchFn, Subscription } from './useSubscriptionDropdowns';

const mockAclEntries = [
  { id: '1', environmentId: 'env1', serviceName: 'StudentPersonals', zoneId: 'zone-1', contextId: 'DEFAULT', accessType: 'SUBSCRIBE', accessLevel: 'APPROVED' },
  { id: '2', environmentId: 'env1', serviceName: 'StudentPersonals', zoneId: 'zone-2', contextId: 'DEFAULT', accessType: 'SUBSCRIBE', accessLevel: 'APPROVED' },
  { id: '3', environmentId: 'env1', serviceName: 'SchoolInfos', zoneId: 'zone-1', contextId: 'CTX-A', accessType: 'SUBSCRIBE', accessLevel: 'APPROVED' },
  { id: '4', environmentId: 'env1', serviceName: 'StaffPersonals', zoneId: 'zone-1', contextId: 'DEFAULT', accessType: 'PROVIDE', accessLevel: 'APPROVED' },
  { id: '5', environmentId: 'env1', serviceName: 'BadEntry', zoneId: null, contextId: null, accessType: 'SUBSCRIBE', accessLevel: 'REJECTED' },
];

function createMockAuthFetch(response?: { ok?: boolean; status?: number; jsonData?: unknown }): AuthFetchFn {
  const { ok = true, status = 200, jsonData = { aclEntries: mockAclEntries } } = response ?? {};
  return vi.fn().mockResolvedValue({
    ok,
    status,
    json: () => Promise.resolve(jsonData),
  });
}

const defaultProps = {
  queueId: 'queue-1',
  domainId: 'domain-1',
  solutionId: 'solution-1',
  envId: 'env1',
  existingSubscriptions: [] as Subscription[],
};

describe('useSubscriptionDropdowns', () => {
  describe('loading state during fetch', () => {
    it('should start with loading=true and transition to loading=false after fetch completes', async () => {
      const authFetch = createMockAuthFetch();

      const { result } = renderHook(() =>
        useSubscriptionDropdowns(
          defaultProps.queueId,
          authFetch,
          defaultProps.domainId,
          defaultProps.solutionId,
          defaultProps.envId,
          defaultProps.existingSubscriptions,
        ),
      );

      // Initially loading
      expect(result.current.loading).toBe(true);
      expect(result.current.error).toBeNull();

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.error).toBeNull();
      // Service names populated from SUBSCRIBE+APPROVED entries (default messageType is EVENT)
      expect(result.current.serviceNames).toContain('StudentPersonals');
      expect(result.current.serviceNames).toContain('SchoolInfos');
      // StaffPersonals is PROVIDE, BadEntry is REJECTED — neither should appear for EVENT
      expect(result.current.serviceNames).not.toContain('StaffPersonals');
      expect(result.current.serviceNames).not.toContain('BadEntry');
    });
  });

  describe('error state on fetch failure', () => {
    it('should set error when authFetch returns non-ok response', async () => {
      const authFetch = createMockAuthFetch({ ok: false, status: 500 });

      const { result } = renderHook(() =>
        useSubscriptionDropdowns(
          defaultProps.queueId,
          authFetch,
          defaultProps.domainId,
          defaultProps.solutionId,
          defaultProps.envId,
          defaultProps.existingSubscriptions,
        ),
      );

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.error).toContain('Could not load ACL entries');
      expect(result.current.error).toContain('500');
    });

    it('should set error when authFetch returns null', async () => {
      const authFetch = vi.fn().mockResolvedValue(null) as unknown as AuthFetchFn;

      const { result } = renderHook(() =>
        useSubscriptionDropdowns(
          defaultProps.queueId,
          authFetch,
          defaultProps.domainId,
          defaultProps.solutionId,
          defaultProps.envId,
          defaultProps.existingSubscriptions,
        ),
      );

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.error).toBe('Could not load ACL entries');
    });
  });

  describe('error state on timeout', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('should set timeout error when fetch does not resolve within 10 seconds', async () => {
      // Create a fetch that never resolves but respects abort signal
      const authFetch = vi.fn().mockImplementation((_path: string, opts?: RequestInit) => {
        return new Promise((_resolve, reject) => {
          if (opts?.signal) {
            opts.signal.addEventListener('abort', () => {
              const abortError = new Error('The operation was aborted');
              abortError.name = 'AbortError';
              reject(abortError);
            });
          }
        });
      }) as unknown as AuthFetchFn;

      const { result } = renderHook(() =>
        useSubscriptionDropdowns(
          defaultProps.queueId,
          authFetch,
          defaultProps.domainId,
          defaultProps.solutionId,
          defaultProps.envId,
          defaultProps.existingSubscriptions,
        ),
      );

      expect(result.current.loading).toBe(true);

      // Advance past the 10-second timeout
      await act(async () => {
        vi.advanceTimersByTime(10000);
      });

      expect(result.current.loading).toBe(false);
      expect(result.current.error).toContain('timed out');
    });
  });

  describe('cascading reset when message type changes', () => {
    it('should clear all selections when message type changes', async () => {
      const authFetch = createMockAuthFetch();

      const { result } = renderHook(() =>
        useSubscriptionDropdowns(
          defaultProps.queueId,
          authFetch,
          defaultProps.domainId,
          defaultProps.solutionId,
          defaultProps.envId,
          defaultProps.existingSubscriptions,
        ),
      );

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      // Make selections
      act(() => {
        result.current.setSelectedService('StudentPersonals');
      });
      act(() => {
        result.current.setSelectedZone('zone-1');
      });
      act(() => {
        result.current.setSelectedContext('DEFAULT');
      });

      expect(result.current.selectedService).toBe('StudentPersonals');
      expect(result.current.selectedZone).toBe('zone-1');
      expect(result.current.selectedContext).toBe('DEFAULT');

      // Change message type → all selections should reset
      act(() => {
        result.current.setMessageType('DELAYED_REQUEST');
      });

      expect(result.current.selectedService).toBeNull();
      expect(result.current.selectedZone).toBeNull();
      expect(result.current.selectedContext).toBeNull();
      expect(result.current.messageType).toBe('DELAYED_REQUEST');
      // Service names should now come from PROVIDE entries
      expect(result.current.serviceNames).toContain('StaffPersonals');
      expect(result.current.serviceNames).not.toContain('StudentPersonals');
    });
  });

  describe('isFormComplete only true when all three dropdowns selected', () => {
    it('should be false initially and true only when service, zone, and context are all selected', async () => {
      const authFetch = createMockAuthFetch();

      const { result } = renderHook(() =>
        useSubscriptionDropdowns(
          defaultProps.queueId,
          authFetch,
          defaultProps.domainId,
          defaultProps.solutionId,
          defaultProps.envId,
          defaultProps.existingSubscriptions,
        ),
      );

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      // Initially not complete
      expect(result.current.isFormComplete).toBe(false);

      // Select service only → still not complete
      act(() => {
        result.current.setSelectedService('StudentPersonals');
      });
      expect(result.current.isFormComplete).toBe(false);

      // Select zone → still not complete
      act(() => {
        result.current.setSelectedZone('zone-1');
      });
      expect(result.current.isFormComplete).toBe(false);

      // Select context → now complete
      act(() => {
        result.current.setSelectedContext('DEFAULT');
      });
      expect(result.current.isFormComplete).toBe(true);
    });
  });

  describe('options recompute after subscription created/deleted', () => {
    it('should remove combo from options after successful subscription creation', async () => {
      const postResponse = {
        ok: true,
        status: 201,
        json: () => Promise.resolve({ id: 'new-sub-1' }),
      };
      const authFetch = vi.fn().mockImplementation((path: string) => {
        if (path.includes('acl-entries')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve({ aclEntries: mockAclEntries }),
          });
        }
        // POST subscription
        return Promise.resolve(postResponse);
      }) as unknown as AuthFetchFn;

      const { result } = renderHook(() =>
        useSubscriptionDropdowns(
          defaultProps.queueId,
          authFetch,
          defaultProps.domainId,
          defaultProps.solutionId,
          defaultProps.envId,
          defaultProps.existingSubscriptions,
        ),
      );

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      // Select a combo to subscribe
      act(() => {
        result.current.setSelectedService('SchoolInfos');
      });
      act(() => {
        result.current.setSelectedZone('zone-1');
      });
      act(() => {
        result.current.setSelectedContext('CTX-A');
      });

      expect(result.current.isFormComplete).toBe(true);

      // Submit subscription
      await act(async () => {
        await result.current.submitSubscription();
      });

      // After creation, selections should reset
      expect(result.current.selectedService).toBeNull();
      expect(result.current.selectedZone).toBeNull();
      expect(result.current.selectedContext).toBeNull();

      // SchoolInfos only had one combo (zone-1 / CTX-A), so it should be fully subscribed and gone
      expect(result.current.serviceNames).not.toContain('SchoolInfos');
    });

    it('should restore combo to options after subscription deleted', async () => {
      // Start with an existing subscription that uses up one combo
      const existingSubs: Subscription[] = [
        {
          id: 'sub-1',
          queueId: 'queue-1',
          serviceName: 'SchoolInfos',
          serviceType: 'OBJECT',
          zoneId: 'zone-1',
          contextId: 'CTX-A',
          messageType: 'EVENT',
        },
      ];

      const authFetch = createMockAuthFetch();

      const { result } = renderHook(() =>
        useSubscriptionDropdowns(
          defaultProps.queueId,
          authFetch,
          defaultProps.domainId,
          defaultProps.solutionId,
          defaultProps.envId,
          existingSubs,
        ),
      );

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      // SchoolInfos should NOT be in service names (fully subscribed)
      expect(result.current.serviceNames).not.toContain('SchoolInfos');

      // Delete the subscription
      act(() => {
        result.current.onSubscriptionDeleted({
          serviceName: 'SchoolInfos',
          zoneId: 'zone-1',
          contextId: 'CTX-A',
          messageType: 'EVENT',
        });
      });

      // SchoolInfos should now reappear
      expect(result.current.serviceNames).toContain('SchoolInfos');
    });
  });
});
