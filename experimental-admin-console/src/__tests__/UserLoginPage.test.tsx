import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import UserLoginPage from '../pages/UserLoginPage';

// Mock AuthContext
const mockLoginUser = vi.fn();
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    loginUser: mockLoginUser,
  }),
}));

// Mock useNavigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/domain-123/solution-456/login']}>
      <Routes>
        <Route path="/admin/:domainId/:solutionId/login" element={<UserLoginPage />} />
      </Routes>
    </MemoryRouter>
  );
}

describe('UserLoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders username and password fields', () => {
    renderPage();

    expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toHaveAttribute('type', 'password');
  });

  it('renders sign in button', () => {
    renderPage();

    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('submits login with correct payload', async () => {
    const mockResponse = {
      ok: true,
      status: 200,
      json: async () => ({
        sessionToken: 'token-abc',
        expiresAt: '2025-01-01T00:00:00Z',
        environments: [{ id: 'env-1', applicationName: 'Test App' }],
      }),
    };
    (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce(mockResponse);

    renderPage();

    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(
        'http://localhost:8080/admin-users/login',
        expect.objectContaining({
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            username: 'admin',
            password: 'password123',
            domainUuid: 'domain-123',
            solutionUuid: 'solution-456',
          }),
        })
      );
    });
  });

  it('calls loginUser and navigates on successful login', async () => {
    const mockResponse = {
      ok: true,
      status: 200,
      json: async () => ({
        sessionToken: 'token-abc',
        expiresAt: '2025-01-01T00:00:00Z',
        environments: [{ id: 'env-1', applicationName: 'Test App' }],
      }),
    };
    (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce(mockResponse);

    renderPage();

    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(mockLoginUser).toHaveBeenCalledWith(
        'token-abc',
        '2025-01-01T00:00:00Z',
        [{ id: 'env-1', applicationName: 'Test App' }],
        'domain-123',
        'solution-456'
      );
    });

    expect(mockNavigate).toHaveBeenCalledWith('/admin/domain-123/solution-456/select-environment');
  });

  it('shows error message and clears password on 401', async () => {
    const mockResponse = {
      ok: false,
      status: 401,
      json: async () => ({ message: 'Unauthorized' }),
    };
    (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce(mockResponse);

    renderPage();

    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'wrongpass' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByText(/invalid credentials/i)).toBeInTheDocument();
    });

    // Password cleared, username preserved
    expect(screen.getByLabelText(/password/i)).toHaveValue('');
    expect(screen.getByLabelText(/username/i)).toHaveValue('admin');
  });

  it('disables button and shows loading state during request', async () => {
    // Never-resolving fetch to keep loading state
    (global.fetch as ReturnType<typeof vi.fn>).mockReturnValueOnce(new Promise(() => {}));

    renderPage();

    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      const button = screen.getByRole('button');
      expect(button).toBeDisabled();
      expect(button).toHaveTextContent(/signing in/i);
    });
  });

  it('shows link to environment login page', () => {
    renderPage();

    const link = screen.getByRole('link', { name: /sign in with environment credentials/i });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/login');
  });

  it('shows connection error on network failure', async () => {
    (global.fetch as ReturnType<typeof vi.fn>).mockRejectedValueOnce(new Error('Network error'));

    renderPage();

    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'admin' } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByText(/connection failed/i)).toBeInTheDocument();
    });
  });
});
