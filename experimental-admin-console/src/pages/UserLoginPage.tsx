import { useState, useEffect, type FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useAdminScope } from '../hooks/useAdminScope';
import type { LoginResponse } from '../types/auth';

const ENVIRONMENT_SERVICE_URL = import.meta.env.VITE_GATEWAY_URL || 'http://localhost:8080';

export default function UserLoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { loginUser, sessionExpiredMessage, clearSessionExpiredMessage } = useAuth();
  const { domainId, solutionId } = useAdminScope();
  const navigate = useNavigate();

  // Show session expired message if redirected due to expiry
  useEffect(() => {
    if (sessionExpiredMessage) {
      setError(sessionExpiredMessage);
      clearSessionExpiredMessage();
    }
  }, [sessionExpiredMessage, clearSessionExpiredMessage]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) return;
    if (!domainId || !solutionId) return;

    setLoading(true);
    setError('');

    try {
      const res = await fetch(`${ENVIRONMENT_SERVICE_URL}/admin-users/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: username.trim(),
          password,
          domainId,
          solutionId,
        }),
      });

      if (res.status === 200) {
        const data: LoginResponse = await res.json();
        loginUser(username.trim(), data.sessionToken, data.expiresAt, data.environments, domainId, solutionId);
        navigate(`/${domainId}/${solutionId}/select-environment`);
        return;
      }

      if (res.status === 401) {
        setError('Invalid credentials');
        setPassword('');
        return;
      }

      setError('An unexpected error occurred. Please try again.');
    } catch {
      setError('Connection failed. Is the service running?');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="bg-white rounded-lg shadow-md p-8 w-full max-w-sm">
        <h1 className="text-xl font-semibold text-gray-800 text-center mb-6">
          Admin Login
        </h1>
        <p className="text-xs text-gray-400 text-center mb-4 font-mono truncate" title={ENVIRONMENT_SERVICE_URL}>
          → {ENVIRONMENT_SERVICE_URL}
        </p>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="username" className="block text-sm font-medium text-gray-700 mb-1">
              Username
            </label>
            <input
              id="username"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              maxLength={128}
              className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              placeholder="Enter username"
            />
          </div>
          <div>
            <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">
              Password
            </label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              maxLength={128}
              className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              placeholder="Enter password"
            />
          </div>

          {error && (
            <div className="text-red-600 text-sm text-center">{error}</div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 text-white rounded py-2 text-sm font-medium hover:bg-blue-700 disabled:opacity-50 transition-colors flex items-center justify-center"
          >
            {loading ? (
              <>
                <svg className="animate-spin -ml-1 mr-2 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                Signing in...
              </>
            ) : (
              'Sign In'
            )}
          </button>
        </form>

        <div className="mt-4 text-center">
          <Link to="/login" className="text-sm text-blue-600 hover:text-blue-800">
            Sign in with environment credentials
          </Link>
        </div>
      </div>
    </div>
  );
}
