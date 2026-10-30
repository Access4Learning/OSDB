import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import UsersTab from './UsersTab';

const DOMAIN_ID = 'domain-1';
const SOLUTION_ID = 'solution-1';
const ENV_ID = 'env-1';

const mockUsers = [
  { id: 'user-1', username: 'alice', grantedAt: '2024-06-01T10:00:00Z' },
  { id: 'user-2', username: 'bob', grantedAt: '2024-06-02T12:00:00Z' },
];

function createMockAuthFetch(overrides: Partial<Record<string, () => Promise<Response | null>>> = {}) {
  return vi.fn(async (path: string, opts?: RequestInit): Promise<Response | null> => {
    // Check overrides first
    for (const [pattern, handler] of Object.entries(overrides)) {
      if (path.includes(pattern)) {
        return handler!();
      }
    }

    // Default: GET users list
    if (path.includes(`/environments/${ENV_ID}/admin-users`) && (!opts || opts.method === undefined || opts.method === 'GET')) {
      return new Response(JSON.stringify(mockUsers), { status: 200 });
    }

    return new Response(JSON.stringify({ message: 'Not found' }), { status: 404 });
  });
}

function renderUsersTab(authFetch = createMockAuthFetch()) {
  return {
    authFetch,
    ...render(
      <UsersTab
        domainId={DOMAIN_ID}
        solutionId={SOLUTION_ID}
        envId={ENV_ID}
        authFetch={authFetch}
      />
    ),
  };
}

describe('UsersTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading state initially', () => {
    // authFetch that never resolves to keep loading state
    const authFetch = vi.fn(() => new Promise<Response | null>(() => {}));
    render(
      <UsersTab domainId={DOMAIN_ID} solutionId={SOLUTION_ID} envId={ENV_ID} authFetch={authFetch} />
    );

    expect(screen.getByText(/loading users/i)).toBeInTheDocument();
  });

  it('renders user list after successful fetch', async () => {
    renderUsersTab();

    await waitFor(() => {
      expect(screen.getByText('alice')).toBeInTheDocument();
    });
    expect(screen.getByText('bob')).toBeInTheDocument();
  });

  it('renders empty state message when no users', async () => {
    const authFetch = vi.fn(async () => new Response(JSON.stringify([]), { status: 200 }));
    render(
      <UsersTab domainId={DOMAIN_ID} solutionId={SOLUTION_ID} envId={ENV_ID} authFetch={authFetch} />
    );

    await waitFor(() => {
      expect(screen.getByText(/no users have access/i)).toBeInTheDocument();
    });
  });

  it('renders error state on fetch failure', async () => {
    const authFetch = vi.fn(async () => new Response(JSON.stringify({ message: 'Server error' }), { status: 500 }));
    render(
      <UsersTab domainId={DOMAIN_ID} solutionId={SOLUTION_ID} envId={ENV_ID} authFetch={authFetch} />
    );

    await waitFor(() => {
      expect(screen.getByText(/failed to fetch users \(500\)/i)).toBeInTheDocument();
    });
  });

  it('add user form submission calls correct API sequence (create → grant → refresh)', async () => {
    const authFetch = vi.fn(async (path: string, opts?: RequestInit): Promise<Response | null> => {
      // GET users list
      if (path.includes(`/environments/${ENV_ID}/admin-users`)) {
        return new Response(JSON.stringify(mockUsers), { status: 200 });
      }
      // POST create user
      if (path === '/admin-users' && opts?.method === 'POST') {
        return new Response(JSON.stringify({ id: 'new-user-id' }), { status: 201 });
      }
      // POST grant access
      if (path.includes('/admin-users/new-user-id/environments') && opts?.method === 'POST') {
        return new Response(JSON.stringify({}), { status: 201 });
      }
      return new Response(JSON.stringify({}), { status: 404 });
    });

    render(
      <UsersTab domainId={DOMAIN_ID} solutionId={SOLUTION_ID} envId={ENV_ID} authFetch={authFetch} />
    );

    // Wait for initial load
    await waitFor(() => {
      expect(screen.getByText('alice')).toBeInTheDocument();
    });

    // Open add form
    fireEvent.click(screen.getByRole('button', { name: /add user/i }));

    // Fill form
    fireEvent.change(screen.getByPlaceholderText(/enter username/i), { target: { value: 'newuser' } });
    fireEvent.change(screen.getByPlaceholderText(/enter password/i), { target: { value: 'pass123' } });

    // Submit
    fireEvent.click(screen.getByRole('button', { name: /^add$/i }));

    await waitFor(() => {
      // Verify create call
      expect(authFetch).toHaveBeenCalledWith('/admin-users', expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          username: 'newuser',
          password: 'pass123',
          domainId: DOMAIN_ID,
          solutionId: SOLUTION_ID,
        }),
      }));
    });

    // Verify grant call
    expect(authFetch).toHaveBeenCalledWith(
      `/${DOMAIN_ID}/${SOLUTION_ID}/admin-users/new-user-id/environments`,
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ environmentId: ENV_ID }),
      })
    );

    // Verify refresh (GET called again after grant)
    const getCalls = authFetch.mock.calls.filter(
      (call: [string, RequestInit?]) => call[0].includes(`/environments/${ENV_ID}/admin-users`) && (!call[1] || !call[1].method || call[1].method === 'GET')
    );
    expect(getCalls.length).toBeGreaterThanOrEqual(2);
  });

  it('duplicate username flow (409 → lookup → grant)', async () => {
    const authFetch = vi.fn(async (path: string, opts?: RequestInit): Promise<Response | null> => {
      // GET users list (initial + refresh)
      if (path.includes(`/environments/${ENV_ID}/admin-users`)) {
        return new Response(JSON.stringify(mockUsers), { status: 200 });
      }
      // POST create user → 409
      if (path === '/admin-users' && opts?.method === 'POST') {
        return new Response(JSON.stringify({ message: 'User already exists' }), { status: 409 });
      }
      // GET lookup users
      if (path.includes('/admin-users?domainId=')) {
        return new Response(JSON.stringify([{ id: 'existing-user-id', username: 'existinguser' }]), { status: 200 });
      }
      // POST grant access
      if (path.includes('/admin-users/existing-user-id/environments') && opts?.method === 'POST') {
        return new Response(JSON.stringify({}), { status: 201 });
      }
      return new Response(JSON.stringify({}), { status: 404 });
    });

    render(
      <UsersTab domainId={DOMAIN_ID} solutionId={SOLUTION_ID} envId={ENV_ID} authFetch={authFetch} />
    );

    await waitFor(() => {
      expect(screen.getByText('alice')).toBeInTheDocument();
    });

    // Open add form
    fireEvent.click(screen.getByRole('button', { name: /add user/i }));

    // Fill form with existing username
    fireEvent.change(screen.getByPlaceholderText(/enter username/i), { target: { value: 'existinguser' } });
    fireEvent.change(screen.getByPlaceholderText(/enter password/i), { target: { value: 'pass123' } });

    // Submit
    fireEvent.click(screen.getByRole('button', { name: /^add$/i }));

    await waitFor(() => {
      // Verify lookup call was made
      expect(authFetch).toHaveBeenCalledWith(
        `/admin-users?domainId=${DOMAIN_ID}&solutionId=${SOLUTION_ID}`
      );
    });

    // Verify grant was called with existing user id
    expect(authFetch).toHaveBeenCalledWith(
      `/${DOMAIN_ID}/${SOLUTION_ID}/admin-users/existing-user-id/environments`,
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ environmentId: ENV_ID }),
      })
    );
  });

  it('remove user shows confirmation dialog and removes from list on success', async () => {
    const authFetch = vi.fn(async (path: string, opts?: RequestInit): Promise<Response | null> => {
      // GET users list
      if (path.includes(`/environments/${ENV_ID}/admin-users`) && (!opts || !opts.method || opts.method === 'GET')) {
        return new Response(JSON.stringify(mockUsers), { status: 200 });
      }
      // DELETE remove user
      if (path.includes(`/admin-users/user-1/environments/${ENV_ID}`) && opts?.method === 'DELETE') {
        return new Response(null, { status: 204 });
      }
      return new Response(JSON.stringify({}), { status: 404 });
    });

    render(
      <UsersTab domainId={DOMAIN_ID} solutionId={SOLUTION_ID} envId={ENV_ID} authFetch={authFetch} />
    );

    await waitFor(() => {
      expect(screen.getByText('alice')).toBeInTheDocument();
    });

    // Click remove on first user
    const removeButtons = screen.getAllByRole('button', { name: /remove/i });
    fireEvent.click(removeButtons[0]);

    // Confirmation dialog appears with username
    await waitFor(() => {
      expect(screen.getByText(/are you sure you want to remove/i)).toBeInTheDocument();
      // Clarifies it only removes access
      expect(screen.getByText(/only removes access/i)).toBeInTheDocument();
    });
    // Username shown in dialog (multiple instances expected: table + dialog)
    expect(screen.getAllByText('alice').length).toBeGreaterThanOrEqual(2);

    // Confirm removal
    const confirmButton = screen.getAllByRole('button', { name: /^remove$/i }).find(
      btn => btn.closest('.fixed') !== null
    );
    fireEvent.click(confirmButton!);

    // User removed from list
    await waitFor(() => {
      expect(screen.queryByText('alice')).not.toBeInTheDocument();
    });
    // Bob still there
    expect(screen.getByText('bob')).toBeInTheDocument();
  });

  it('submit button disabled when fields empty', async () => {
    renderUsersTab();

    await waitFor(() => {
      expect(screen.getByText('alice')).toBeInTheDocument();
    });

    // Open add form
    fireEvent.click(screen.getByRole('button', { name: /add user/i }));

    // Submit button should be disabled with empty fields
    const submitButton = screen.getByRole('button', { name: /^add$/i });
    expect(submitButton).toBeDisabled();

    // Fill only username
    fireEvent.change(screen.getByPlaceholderText(/enter username/i), { target: { value: 'test' } });
    expect(submitButton).toBeDisabled();

    // Fill password too
    fireEvent.change(screen.getByPlaceholderText(/enter password/i), { target: { value: 'pass' } });
    expect(submitButton).not.toBeDisabled();
  });
});
