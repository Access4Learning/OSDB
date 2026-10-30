import { render, screen, fireEvent, waitFor, cleanup, act } from '@testing-library/react';
import { vi, describe, it, expect, afterEach } from 'vitest';
import { useMemo } from 'react';
import { useSubscriptionDropdowns, AuthFetchFn, Subscription } from '../hooks/useSubscriptionDropdowns';
import { ActiveSubscription, MessageType } from '../utils/subscriptionFilterUtils';

const mockAclEntries = [
  { id: '1', environmentId: 'env1', serviceName: 'StudentPersonals', zoneId: 'zone-1', contextId: 'DEFAULT', accessType: 'SUBSCRIBE', accessLevel: 'APPROVED' },
  { id: '2', environmentId: 'env1', serviceName: 'StudentPersonals', zoneId: 'zone-2', contextId: 'DEFAULT', accessType: 'SUBSCRIBE', accessLevel: 'APPROVED' },
  { id: '3', environmentId: 'env1', serviceName: 'SchoolInfos', zoneId: 'zone-1', contextId: 'CTX-A', accessType: 'SUBSCRIBE', accessLevel: 'APPROVED' },
  { id: '4', environmentId: 'env1', serviceName: 'StaffPersonals', zoneId: 'zone-1', contextId: 'DEFAULT', accessType: 'PROVIDE', accessLevel: 'APPROVED' },
  { id: '5', environmentId: 'env1', serviceName: 'BadEntry', zoneId: null, contextId: null, accessType: 'SUBSCRIBE', accessLevel: 'REJECTED' },
];

// Stable empty array to avoid re-render loops from ref comparison in useSubscriptionDropdowns
const EMPTY_SUBSCRIPTIONS: Subscription[] = [];

/**
 * Minimal test wrapper that uses the useSubscriptionDropdowns hook
 * and renders the subscription form UI in isolation.
 */
function SubscriptionFormTestWrapper({
  authFetch,
  existingSubscriptions,
  onDeleteSub,
}: {
  authFetch: AuthFetchFn;
  existingSubscriptions?: Subscription[];
  onDeleteSub?: ActiveSubscription;
}) {
  const stableSubs = useMemo(() => existingSubscriptions ?? EMPTY_SUBSCRIPTIONS, [existingSubscriptions]);
  const subDropdowns = useSubscriptionDropdowns('queue-1', authFetch, 'domain-1', 'solution-1', 'env1', stableSubs);

  return (
    <div>
      {subDropdowns.loading ? (
        <p>Loading ACL entries...</p>
      ) : subDropdowns.error ? (
        <p role="alert">{subDropdowns.error}</p>
      ) : subDropdowns.serviceNames.length === 0 ? (
        <p>All available subscriptions created</p>
      ) : (
        <>
          <select
            aria-label="Message Type"
            value={subDropdowns.messageType}
            onChange={(e) => subDropdowns.setMessageType(e.target.value as MessageType)}
          >
            <option value="EVENT">EVENT</option>
            <option value="DELAYED_REQUEST">DELAYED_REQUEST</option>
          </select>
          <select
            aria-label="Service Name"
            value={subDropdowns.selectedService || ''}
            onChange={(e) => subDropdowns.setSelectedService(e.target.value)}
            disabled={subDropdowns.serviceNames.length === 0}
          >
            <option value="">— Select service —</option>
            {subDropdowns.serviceNames.map((s) => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
          <select
            aria-label="Zone ID"
            value={subDropdowns.selectedZone || ''}
            onChange={(e) => subDropdowns.setSelectedZone(e.target.value)}
            disabled={!subDropdowns.selectedService}
          >
            <option value="">
              {subDropdowns.selectedService ? '— Select zone —' : '— Select service first —'}
            </option>
            {subDropdowns.zoneIds.map((z) => (
              <option key={z} value={z}>{z || '(wildcard)'}</option>
            ))}
          </select>
          <select
            aria-label="Context ID"
            value={subDropdowns.selectedContext || ''}
            onChange={(e) => subDropdowns.setSelectedContext(e.target.value)}
            disabled={subDropdowns.selectedZone === null}
          >
            <option value="">
              {subDropdowns.selectedZone !== null ? '— Select context —' : '— Select zone first —'}
            </option>
            {subDropdowns.contextIds.map((c) => (
              <option key={c} value={c}>{c || '(wildcard)'}</option>
            ))}
          </select>
          <select
            aria-label="Service Type"
            value={subDropdowns.serviceType}
            onChange={(e) => subDropdowns.setServiceType(e.target.value)}
          >
            <option value="OBJECT">OBJECT</option>
            <option value="UTILITY">UTILITY</option>
          </select>
          <button
            onClick={() => subDropdowns.submitSubscription()}
            disabled={!subDropdowns.isFormComplete || subDropdowns.isSubmitting}
          >
            {subDropdowns.isSubmitting ? 'Creating...' : 'Add Subscription'}
          </button>
          <button
            data-testid="delete-btn"
            onClick={() => {
              if (onDeleteSub) {
                subDropdowns.onSubscriptionDeleted(onDeleteSub);
              }
            }}
          >
            Delete Subscription
          </button>
        </>
      )}
    </div>
  );
}

function createMockAuthFetch(response?: { ok?: boolean; status?: number; jsonData?: unknown }): AuthFetchFn {
  const { ok = true, status = 200, jsonData = mockAclEntries } = response ?? {};
  return vi.fn().mockResolvedValue({
    ok,
    status,
    json: () => Promise.resolve(jsonData),
  });
}

describe('SubscriptionForm UI Behavior', () => {
  afterEach(() => {
    cleanup();
  });

  async function renderAndLoad(authFetch: AuthFetchFn, props?: { existingSubscriptions?: Subscription[]; onDeleteSub?: ActiveSubscription }) {
    render(
      <SubscriptionFormTestWrapper
        authFetch={authFetch}
        existingSubscriptions={props?.existingSubscriptions}
        onDeleteSub={props?.onDeleteSub}
      />,
    );
    await waitFor(() => {
      expect(screen.queryByText('Loading ACL entries...')).not.toBeInTheDocument();
    });
  }

  describe('Expanding queue panel triggers ACL fetch', () => {
    it('should call authFetch with acl-entries endpoint when hook mounts', async () => {
      const authFetch = createMockAuthFetch();
      await renderAndLoad(authFetch);

      expect(authFetch).toHaveBeenCalledWith(
        '/domain-1/solution-1/acl-entries?environmentId=env1',
        expect.objectContaining({ signal: expect.any(AbortSignal) }),
        { 'X-Run-As-Environment': 'broker-services' }
      );
    });
  });

  describe('Loading state shown while fetching', () => {
    it('should display loading indicator while ACL entries are being fetched', async () => {
      // Use a delayed promise to catch the loading state
      let resolvePromise: (value: unknown) => void;
      const delayedFetch = vi.fn().mockImplementation(() => new Promise((resolve) => { resolvePromise = resolve; })) as unknown as AuthFetchFn;

      render(<SubscriptionFormTestWrapper authFetch={delayedFetch} />);

      // Loading should be visible
      expect(screen.getByText('Loading ACL entries...')).toBeInTheDocument();

      // Resolve the fetch
      await act(async () => {
        resolvePromise!({ ok: true, status: 200, json: () => Promise.resolve(mockAclEntries) });
      });

      // Loading should be gone
      await waitFor(() => {
        expect(screen.queryByText('Loading ACL entries...')).not.toBeInTheDocument();
      });

      expect(screen.getByLabelText('Service Name')).toBeInTheDocument();
    });
  });

  describe('Error state shown on fetch failure', () => {
    it('should display error message when authFetch returns non-ok response', async () => {
      const authFetch = createMockAuthFetch({ ok: false, status: 500 });

      render(<SubscriptionFormTestWrapper authFetch={authFetch} />);

      await waitFor(() => {
        const alert = screen.getByRole('alert');
        expect(alert).toBeInTheDocument();
        expect(alert.textContent).toContain('Could not load ACL entries');
      });
    });

    it('should display error when authFetch returns null', async () => {
      const authFetch = vi.fn().mockResolvedValue(null) as unknown as AuthFetchFn;

      render(<SubscriptionFormTestWrapper authFetch={authFetch} />);

      await waitFor(() => {
        const alert = screen.getByRole('alert');
        expect(alert).toBeInTheDocument();
        expect(alert.textContent).toContain('Could not load ACL entries');
      });
    });
  });

  describe('Dropdowns cascade correctly', () => {
    it('should have Zone ID disabled until service selected, Context ID disabled until zone selected', async () => {
      const authFetch = createMockAuthFetch();
      await renderAndLoad(authFetch);

      const zoneSelect = screen.getByLabelText('Zone ID') as HTMLSelectElement;
      const contextSelect = screen.getByLabelText('Context ID') as HTMLSelectElement;

      // Initially: zone disabled, context disabled
      expect(zoneSelect).toBeDisabled();
      expect(contextSelect).toBeDisabled();

      // Select a service → zone enabled, context still disabled
      const serviceSelect = screen.getByLabelText('Service Name') as HTMLSelectElement;
      fireEvent.change(serviceSelect, { target: { value: 'StudentPersonals' } });

      expect(zoneSelect).not.toBeDisabled();
      expect(contextSelect).toBeDisabled();

      // Select a zone → context enabled
      fireEvent.change(zoneSelect, { target: { value: 'zone-1' } });

      expect(contextSelect).not.toBeDisabled();
    });
  });

  describe('Changing message type clears selections and repopulates service dropdown', () => {
    it('should clear all selections and repopulate service names for new message type', async () => {
      const authFetch = createMockAuthFetch();
      await renderAndLoad(authFetch);

      const messageTypeSelect = screen.getByLabelText('Message Type') as HTMLSelectElement;
      const serviceSelect = screen.getByLabelText('Service Name') as HTMLSelectElement;

      // Verify EVENT services are present
      expect(serviceSelect.innerHTML).toContain('StudentPersonals');
      expect(serviceSelect.innerHTML).toContain('SchoolInfos');

      // Select some values
      fireEvent.change(serviceSelect, { target: { value: 'StudentPersonals' } });
      const zoneSelect = screen.getByLabelText('Zone ID') as HTMLSelectElement;
      fireEvent.change(zoneSelect, { target: { value: 'zone-1' } });

      // Change message type to DELAYED_REQUEST
      fireEvent.change(messageTypeSelect, { target: { value: 'DELAYED_REQUEST' } });

      // Service should be reset (back to placeholder)
      expect(serviceSelect.value).toBe('');
      // Zone and Context should be disabled again
      expect(screen.getByLabelText('Zone ID')).toBeDisabled();
      expect(screen.getByLabelText('Context ID')).toBeDisabled();

      // Service names should now show PROVIDE services
      expect(serviceSelect.innerHTML).toContain('StaffPersonals');
      expect(serviceSelect.innerHTML).not.toContain('StudentPersonals');
    });
  });

  describe('Add Subscription button disabled until all three dropdowns have values', () => {
    it('should be disabled initially and enabled only when service, zone, and context are selected', async () => {
      const authFetch = createMockAuthFetch();
      await renderAndLoad(authFetch);

      const button = screen.getByRole('button', { name: 'Add Subscription' });

      // Initially disabled
      expect(button).toBeDisabled();

      // Select service only → still disabled
      fireEvent.change(screen.getByLabelText('Service Name'), { target: { value: 'StudentPersonals' } });
      expect(button).toBeDisabled();

      // Select zone → still disabled
      fireEvent.change(screen.getByLabelText('Zone ID'), { target: { value: 'zone-1' } });
      expect(button).toBeDisabled();

      // Select context → enabled
      fireEvent.change(screen.getByLabelText('Context ID'), { target: { value: 'DEFAULT' } });
      expect(button).not.toBeDisabled();
    });
  });

  describe('Successful creation resets form and updates options', () => {
    it('should reset selections and remove subscribed combo from dropdown options', async () => {
      const authFetch = vi.fn().mockImplementation((path: string) => {
        if (path.includes('acl-entries')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve(mockAclEntries),
          });
        }
        // POST subscription creation
        return Promise.resolve({
          ok: true,
          status: 201,
          json: () => Promise.resolve({ id: 'new-sub-1' }),
        });
      }) as unknown as AuthFetchFn;

      await renderAndLoad(authFetch);

      const serviceSelect = screen.getByLabelText('Service Name') as HTMLSelectElement;

      // SchoolInfos has only one combo (zone-1/CTX-A), subscribe it
      fireEvent.change(serviceSelect, { target: { value: 'SchoolInfos' } });
      fireEvent.change(screen.getByLabelText('Zone ID'), { target: { value: 'zone-1' } });
      fireEvent.change(screen.getByLabelText('Context ID'), { target: { value: 'CTX-A' } });

      const button = screen.getByRole('button', { name: 'Add Subscription' });
      expect(button).not.toBeDisabled();

      // Submit
      fireEvent.click(button);

      // Wait for submit to complete and form to reset
      await waitFor(() => {
        expect(serviceSelect.value).toBe('');
      });

      // SchoolInfos fully subscribed → removed from service list
      expect(serviceSelect.innerHTML).not.toContain('SchoolInfos');
      // StudentPersonals still available
      expect(serviceSelect.innerHTML).toContain('StudentPersonals');
    });
  });

  describe('Deletion updates options to include freed combo', () => {
    it('should restore a fully-subscribed service after its subscription is deleted', async () => {
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

      const deleteSub: ActiveSubscription = {
        serviceName: 'SchoolInfos',
        zoneId: 'zone-1',
        contextId: 'CTX-A',
        messageType: 'EVENT',
      };

      const authFetch = createMockAuthFetch();
      await renderAndLoad(authFetch, { existingSubscriptions: existingSubs, onDeleteSub: deleteSub });

      const serviceSelect = screen.getByLabelText('Service Name') as HTMLSelectElement;

      // SchoolInfos should NOT be in service list initially (fully subscribed)
      expect(serviceSelect.innerHTML).not.toContain('SchoolInfos');

      // Click delete button to trigger onSubscriptionDeleted
      fireEvent.click(screen.getByTestId('delete-btn'));

      // SchoolInfos should reappear
      expect(serviceSelect.innerHTML).toContain('SchoolInfos');
    });
  });
});
