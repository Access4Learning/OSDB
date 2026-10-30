import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useAdminScope } from '../hooks/useAdminScope';
import type { EnvironmentInfo } from '../types/auth';

const ENVIRONMENT_SERVICE_URL = import.meta.env.VITE_GATEWAY_URL || 'http://localhost:8080';

export default function EnvironmentSelectionPage() {
  const { environments, adminSessionToken, selectEnvironment } = useAuth();
  const { domainId, solutionId } = useAdminScope();
  const navigate = useNavigate();

  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [autoSelectAttempted, setAutoSelectAttempted] = useState(false);

  const dashboardPath = `/${domainId}/${solutionId}/dashboard`;

  const callSelectEnvironment = useCallback(async (env: EnvironmentInfo): Promise<boolean> => {
    setLoading(true);
    setError('');

    try {
      const res = await fetch(
        `${ENVIRONMENT_SERVICE_URL}/admin-users/sessions/select-environment`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Admin-Session-Token': adminSessionToken || '',
          },
          body: JSON.stringify({ environmentId: env.id }),
        }
      );

      if (res.status === 204 || res.status === 200) {
        selectEnvironment(env);
        navigate(dashboardPath, { replace: true });
        return true;
      }

      if (res.status === 403) {
        setError('You do not have access to this environment.');
      } else {
        setError('Environment selection failed. Please try again.');
      }
      return false;
    } catch {
      setError('Connection failed. Is the service running?');
      return false;
    } finally {
      setLoading(false);
    }
  }, [adminSessionToken, selectEnvironment, navigate, dashboardPath]);

  // Auto-select if exactly 1 environment
  useEffect(() => {
    if (autoSelectAttempted) return;
    if (environments.length === 1) {
      setAutoSelectAttempted(true);
      callSelectEnvironment(environments[0]);
    }
  }, [environments, autoSelectAttempted, callSelectEnvironment]);

  const handleSelect = (env: EnvironmentInfo) => {
    callSelectEnvironment(env);
  };

  // If auto-select is in progress for single environment, show loading
  if (environments.length === 1 && !autoSelectAttempted) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="text-gray-500 text-sm">Connecting...</div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="bg-white rounded-lg shadow-md p-8 w-full max-w-md">
        <h1 className="text-xl font-semibold text-gray-800 text-center mb-2">
          Select Environment
        </h1>
        <p className="text-sm text-gray-500 text-center mb-6">
          Choose an environment to manage
        </p>

        {error && (
          <div className="text-red-600 text-sm text-center mb-4">{error}</div>
        )}

        <div className="space-y-3">
          {environments.map((env) => (
            <button
              key={env.id}
              onClick={() => handleSelect(env)}
              disabled={loading}
              className="w-full text-left bg-white border border-gray-200 rounded-lg p-4 hover:border-blue-500 hover:bg-blue-50 disabled:opacity-50 transition-colors"
            >
              <div className="font-medium text-gray-800 text-sm">
                {env.applicationName}
              </div>
              <div className="text-xs text-gray-400 font-mono mt-1 truncate">
                {env.id}
              </div>
            </button>
          ))}
        </div>

        {loading && (
          <div className="text-gray-500 text-sm text-center mt-4">
            Selecting environment...
          </div>
        )}
      </div>
    </div>
  );
}
