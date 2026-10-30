import { useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { authenticatedFetch } from '../utils/auth';

export default function LoginPage() {
  const [searchParams] = useSearchParams();
  const [token, setToken] = useState('');
  const [secret, setSecret] = useState('');
  const [domain, setDomain] = useState(searchParams.get('domain') || '');
  const [solution, setSolution] = useState(searchParams.get('solution') || '');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { loginEnvironment } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!token.trim() || !secret.trim()) return;

    setLoading(true);
    setError('');

    // Store credentials (without scope initially — scope determined below)
    loginEnvironment(token.trim(), secret.trim());

    try {
      // Try fetching domains — if it works, user is global admin
      const res = await authenticatedFetch('/domains', token.trim(), secret.trim());

      if (res.status === 200) {
        // Global admin — go to domains page
        navigate('/domains');
        return;
      }

      if (res.status === 401) {
        setError('Invalid credentials');
        return;
      }

      if (res.status === 403) {
        // Not a global admin. If domain/solution provided, try solution-level admin.
        if (domain.trim() && solution.trim()) {
          const solutionRes = await authenticatedFetch(
            `/${domain.trim()}/${solution.trim()}/environments`,
            token.trim(), secret.trim()
          );
          if (solutionRes.status === 200) {
            // Solution admin — re-login with scope and redirect
            loginEnvironment(token.trim(), secret.trim(), domain.trim(), solution.trim());
            navigate(`/domains/${domain.trim()}/solutions/${solution.trim()}`);
            return;
          }
        }

        // Regular environment — go to my-environment
        navigate('/my-environment');
        return;
      }

      // Unexpected status — still try admin flow
      navigate('/domains');
    } catch (err) {
      setError('Connection failed. Is the broker running?');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="bg-white rounded-lg shadow-md p-8 w-full max-w-sm">
        <h1 className="text-xl font-semibold text-gray-800 text-center mb-6">
          SIF Unity Broker
        </h1>
        <p className="text-xs text-gray-400 text-center mb-4 font-mono truncate" title={import.meta.env.VITE_GATEWAY_URL || 'http://localhost:8080'}>
          → {import.meta.env.VITE_GATEWAY_URL || 'http://localhost:8080'}
        </p>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="sessionToken" className="block text-sm font-medium text-gray-700 mb-1">
              Session Token
            </label>
            <input
              id="sessionToken"
              type="text"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              placeholder="Enter session token"
            />
          </div>
          <div>
            <label htmlFor="sharedSecret" className="block text-sm font-medium text-gray-700 mb-1">
              Shared Secret
            </label>
            <input
              id="sharedSecret"
              type="password"
              value={secret}
              onChange={(e) => setSecret(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              placeholder="Enter shared secret"
            />
          </div>

          <div className="border-t border-gray-200 pt-3 mt-3">
            <p className="text-xs text-gray-400 mb-2">Optional — for solution-level admin access:</p>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label htmlFor="domainId" className="block text-xs font-medium text-gray-600 mb-1">
                  Domain ID
                </label>
                <input
                  id="domainId"
                  type="text"
                  value={domain}
                  onChange={(e) => setDomain(e.target.value)}
                  className="w-full border border-gray-300 rounded px-2 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  placeholder="e.g. a4l"
                />
              </div>
              <div>
                <label htmlFor="solutionId" className="block text-xs font-medium text-gray-600 mb-1">
                  Solution ID
                </label>
                <input
                  id="solutionId"
                  type="text"
                  value={solution}
                  onChange={(e) => setSolution(e.target.value)}
                  className="w-full border border-gray-300 rounded px-2 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  placeholder="e.g. acceptancetesting"
                />
              </div>
            </div>
          </div>

          {error && (
            <div className="text-red-600 text-sm text-center">{error}</div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 text-white rounded py-2 text-sm font-medium hover:bg-blue-700 disabled:opacity-50 transition-colors"
          >
            {loading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  );
}
