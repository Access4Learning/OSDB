import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import EnvironmentSelectionPage from '../pages/EnvironmentSelectionPage';

// Mock useNavigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useParams: () => ({ domainId: 'domain-1', solutionId: 'solution-1' }),
  };
});

// Mock AuthContext
const mockSelectEnvironment = vi.fn();
const mockAuthValues = {
  authMode: 'user' as const,
  isAuthenticated: true,
  sessionToken: null,
  sharedSecret: null,
  adminSessionToken: 'test-admin-token',
  expiresAt: '2099-01-01T00:00:00Z',
  environments: [
    { id: 'env-1', applicationName: 'School SIS' },
    { id: 'env-2', applicationName: 'District LMS' },
  ],
  selectedEnvironment: null,
  domainId: 'domain-1',
  solutionId: 'solution-1',
  loginEnvironment: vi.fn(),
  loginUser: vi.fn(),
  selectEnvironment: mockSelectEnvironment,
  logout: vi.fn(),
  apiFetch: vi.fn(),
  checkScopeChange: vi.fn().mockReturnValue(false),
  scopeChangeMessage: null,
  clearScopeChangeMessage: vi.fn(),
};

vi.mock('../context/AuthContext', () => ({
  useAuth: () => mockAuthValues,
}));

// Mock useAdminScope
vi.mock('../hooks/useAdminScope', () => ({
  useAdminScope: () => ({ domainId: 'domain-1', solutionId: 'solution-1', isValid: true }),
}));

describe('EnvironmentSelectionPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthValues.environments = [
      { id: 'env-1', applicationName: 'School SIS' },
      { id: 'env-2', applicationName: 'District LMS' },
    ];
    global.fetch = vi.fn();
  });

  function renderPage() {
    return render(
      <MemoryRouter initialEntries={['/admin/domain-1/solution-1/select-environment']}>
        <EnvironmentSelectionPage />
      </MemoryRouter>
    );
  }

  describe('multiple environments', () => {
    it('renders environment list when multiple environments exist', () => {
      renderPage();

      expect(screen.getByText('Select Environment')).toBeInTheDocument();
      expect(screen.getByText('School SIS')).toBeInTheDocument();
      expect(screen.getByText('District LMS')).toBeInTheDocument();
      expect(screen.getByText('env-1')).toBeInTheDocument();
      expect(screen.getByText('env-2')).toBeInTheDocument();
    });

    it('calls select-environment API on environment click', async () => {
      (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
        status: 204,
      });

      renderPage();

      fireEvent.click(screen.getByText('School SIS'));

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledWith(
          expect.stringContaining('/admin-users/sessions/select-environment'),
          expect.objectContaining({
            method: 'POST',
            headers: expect.objectContaining({
              'X-Admin-Session-Token': 'test-admin-token',
            }),
            body: JSON.stringify({ environmentId: 'env-1' }),
          })
        );
      });

      await waitFor(() => {
        expect(mockSelectEnvironment).toHaveBeenCalledWith({ id: 'env-1', applicationName: 'School SIS' });
        expect(mockNavigate).toHaveBeenCalledWith('/admin/domain-1/solution-1/dashboard', { replace: true });
      });
    });

    it('shows error on failed selection', async () => {
      (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
        status: 500,
      });

      renderPage();

      fireEvent.click(screen.getByText('School SIS'));

      await waitFor(() => {
        expect(screen.getByText('Environment selection failed. Please try again.')).toBeInTheDocument();
      });

      // Should NOT navigate
      expect(mockNavigate).not.toHaveBeenCalled();
    });

    it('shows 403 error message on forbidden selection', async () => {
      (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
        status: 403,
      });

      renderPage();

      fireEvent.click(screen.getByText('District LMS'));

      await waitFor(() => {
        expect(screen.getByText('You do not have access to this environment.')).toBeInTheDocument();
      });
    });
  });

  describe('single environment auto-select', () => {
    beforeEach(() => {
      mockAuthValues.environments = [
        { id: 'env-single', applicationName: 'Only Environment' },
      ];
    });

    it('auto-selects when single environment and succeeds', async () => {
      (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
        status: 204,
      });

      renderPage();

      await waitFor(() => {
        expect(global.fetch).toHaveBeenCalledWith(
          expect.stringContaining('/admin-users/sessions/select-environment'),
          expect.objectContaining({
            method: 'POST',
            body: JSON.stringify({ environmentId: 'env-single' }),
          })
        );
      });

      await waitFor(() => {
        expect(mockSelectEnvironment).toHaveBeenCalledWith({ id: 'env-single', applicationName: 'Only Environment' });
        expect(mockNavigate).toHaveBeenCalledWith('/admin/domain-1/solution-1/dashboard', { replace: true });
      });
    });

    it('shows selection page with error when auto-select fails', async () => {
      (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
        status: 500,
      });

      renderPage();

      await waitFor(() => {
        expect(screen.getByText('Environment selection failed. Please try again.')).toBeInTheDocument();
      });

      // Should show the environment in the list for manual retry
      expect(screen.getByText('Only Environment')).toBeInTheDocument();
    });

    it('shows selection page with error when auto-select network fails', async () => {
      (global.fetch as ReturnType<typeof vi.fn>).mockRejectedValueOnce(new Error('Network error'));

      renderPage();

      await waitFor(() => {
        expect(screen.getByText('Connection failed. Is the service running?')).toBeInTheDocument();
      });

      // Environment should still be visible for retry
      expect(screen.getByText('Only Environment')).toBeInTheDocument();
    });
  });
});
