import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';

// --- Router mock (useParams is mutated per suite) ---
let mockParams: Record<string, string> = {};
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useParams: () => mockParams,
  };
});

// --- Auth mock: provide credentials so authFetch proceeds ---
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ sessionToken: 'tok', sharedSecret: 'sec', logout: vi.fn() }),
}));

// --- authenticatedFetch mock: intercept all API calls ---
const mockAuthFetch = vi.fn();
vi.mock('../utils/auth', () => ({
  authenticatedFetch: (...args: any[]) => mockAuthFetch(...args),
  generateAuthHeaders: () => ({ Authorization: 'x', timestamp: 'y' }),
  proxyFetch: (...args: any[]) => mockAuthFetch(...args),
}));

import SolutionOverviewPage from '../pages/SolutionOverviewPage';
import ZoneDetailPage from '../pages/ZoneDetailPage';

const mockResponse = (status: number, body?: any): Response => ({
  ok: status >= 200 && status < 300,
  status,
  json: () => Promise.resolve(body),
  text: () => Promise.resolve(typeof body === 'string' ? body : JSON.stringify(body)),
} as Response);

describe('Zone/Context SIF-conformant shape — SolutionOverviewPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockParams = { domainId: 'd1', solutionId: 's1' };
    mockAuthFetch.mockImplementation((path: string, _t?: string, _s?: string, opts?: RequestInit) => {
      const method = opts?.method || 'GET';
      if (path === '/d1/solutions/s1') {
        return Promise.resolve(mockResponse(200, { id: 'sol-uuid', solutionId: 's1', domainId: 'd1', description: '' }));
      }
      if (path === '/d1/s1/environments') {
        return Promise.resolve(mockResponse(200, { environments: { environment: [] } }));
      }
      if (path === '/d1/s1/zones' && method === 'GET') {
        // List envelope preserved: zones.zone[]; each element carries business `id` + internal `uuid`
        return Promise.resolve(mockResponse(200, {
          zones: { zone: [{ id: 'district-1', uuid: 'zone-uuid-1', description: 'District 1', createdAt: null }] },
        }));
      }
      if (path === '/d1/s1/zones/zone' && method === 'POST') {
        return Promise.resolve(mockResponse(201, { zone: { id: 'new-zone', uuid: 'zone-uuid-2' } }));
      }
      return Promise.resolve(mockResponse(200, {}));
    });
  });

  async function renderAndOpenZones() {
    await act(async () => {
      render(
        <MemoryRouter>
          <SolutionOverviewPage />
        </MemoryRouter>
      );
    });
    await act(async () => {
      fireEvent.click(screen.getByText('Zones'));
    });
  }

  it('renders zone list identifiers read from the `id` field', async () => {
    await renderAndOpenZones();
    await waitFor(() => {
      expect(screen.getByText('district-1')).toBeInTheDocument();
    });
    // The internal uuid must NOT be surfaced as the identifier
    expect(screen.queryByText('zone-uuid-1')).not.toBeInTheDocument();
  });

  it('zone create posts to /zones/zone with a { zone: { id } } body', async () => {
    await renderAndOpenZones();
    await waitFor(() => expect(screen.getByText('district-1')).toBeInTheDocument());

    fireEvent.click(screen.getByText('Add New Zone'));
    fireEvent.change(screen.getByPlaceholderText('e.g. district-1'), { target: { value: 'new-zone' } });

    await act(async () => {
      fireEvent.click(screen.getByText('Create'));
    });

    expect(mockAuthFetch).toHaveBeenCalledWith(
      '/d1/s1/zones/zone',
      'tok',
      'sec',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ zone: { id: 'new-zone', description: null } }),
      }),
      expect.anything()
    );
  });
});

describe('Zone/Context SIF-conformant shape — ZoneDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockParams = { domainId: 'd1', solutionId: 's1', zoneId: 'district-1' };
    mockAuthFetch.mockImplementation((path: string, _t?: string, _s?: string, opts?: RequestInit) => {
      const method = opts?.method || 'GET';
      if (path === '/d1/s1/zones/district-1/contexts' && method === 'GET') {
        // List envelope preserved: contexts.context[]; business `id` + internal `uuid`
        return Promise.resolve(mockResponse(200, {
          contexts: {
            context: [
              { id: 'DEFAULT', uuid: 'ctx-uuid-def', description: 'Default', createdAt: null },
              { id: 'ctx-a', uuid: 'ctx-uuid-a', description: 'Context A', createdAt: null },
            ],
          },
        }));
      }
      if (path === '/d1/s1/solution-services') {
        return Promise.resolve(mockResponse(200, []));
      }
      if (path === '/d1/s1/zones/district-1/contexts/context' && method === 'POST') {
        return Promise.resolve(mockResponse(201, { context: { id: 'ctx-b', uuid: 'ctx-uuid-b' } }));
      }
      return Promise.resolve(mockResponse(200, {}));
    });
  });

  async function renderPage() {
    await act(async () => {
      render(
        <MemoryRouter>
          <ZoneDetailPage />
        </MemoryRouter>
      );
    });
  }

  it('renders context list identifiers read from the `id` field', async () => {
    await renderPage();
    await waitFor(() => {
      expect(screen.getByText('ctx-a')).toBeInTheDocument();
      expect(screen.getByText('DEFAULT')).toBeInTheDocument();
    });
    // Internal uuid must NOT be surfaced as the identifier
    expect(screen.queryByText('ctx-uuid-a')).not.toBeInTheDocument();
  });

  it('context create posts to /contexts/context with a { context: { id } } body', async () => {
    await renderPage();
    await waitFor(() => expect(screen.getByText('ctx-a')).toBeInTheDocument());

    fireEvent.click(screen.getByText('Add Context'));
    fireEvent.change(screen.getByPlaceholderText('e.g. DEFAULT'), { target: { value: 'ctx-b' } });

    await act(async () => {
      fireEvent.click(screen.getByText('Create'));
    });

    expect(mockAuthFetch).toHaveBeenCalledWith(
      '/d1/s1/zones/district-1/contexts/context',
      'tok',
      'sec',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ context: { id: 'ctx-b', description: null } }),
      }),
      undefined
    );
  });
});
