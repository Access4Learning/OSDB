import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const ENVIRONMENT_SERVICE_URL = import.meta.env.VITE_GATEWAY_URL || 'http://localhost:8080';

export default function Navbar() {
  const { logout, authMode, adminSessionToken, selectedEnvironment, domainId, solutionId, username } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = async () => {
    if (authMode === 'user' && adminSessionToken) {
      // Best-effort server-side session invalidation
      try {
        await fetch(`${ENVIRONMENT_SERVICE_URL}/admin-users/sessions/logout`, {
          method: 'POST',
          headers: {
            'X-Admin-Session-Token': adminSessionToken,
            'Content-Type': 'application/json',
          },
        });
      } catch {
        // Ignore — logout is best-effort
      }
      // Clear all state and redirect to user login page
      logout();
      navigate(`/${domainId}/${solutionId}/login`);
    } else {
      // Environment mode: preserve existing behavior
      logout();
      navigate('/login');
    }
  };

  const handleSwitchEnvironment = () => {
    if (domainId && solutionId) {
      navigate(`/${domainId}/${solutionId}/select-environment`);
    }
  };

  // Build breadcrumbs from path
  const buildBreadcrumbs = () => {
    const parts = location.pathname.split('/').filter(Boolean);
    const crumbs: { label: string; path: string }[] = [];

    if (parts[0] === 'domains') {
      crumbs.push({ label: 'Domains', path: '/domains' });
      if (parts[1]) {
        crumbs.push({ label: parts[1], path: `/domains/${parts[1]}` });
        if (parts[2] === 'solutions' && parts[3]) {
          crumbs.push({ label: parts[3], path: `/domains/${parts[1]}/solutions/${parts[3]}` });
          if (parts[4] === 'environments' && parts[5]) {
            crumbs.push({ label: parts[5].slice(0, 8) + '...', path: `/domains/${parts[1]}/solutions/${parts[3]}/environments/${parts[5]}` });
          }
        }
      }
    }
    return crumbs;
  };

  const breadcrumbs = buildBreadcrumbs();

  // Solution admin: domainId+solutionId set in environment auth mode
  const isSolutionAdmin = authMode === 'environment' && !!domainId && !!solutionId;

  return (
    <nav className="bg-white border-b border-gray-200 px-6 py-3 flex items-center justify-between">
      <div className="flex items-center gap-2">
        {!isSolutionAdmin && (
          <button onClick={() => navigate('/domains')} className="text-lg font-semibold text-gray-800 hover:text-blue-600">SIF Unity Broker</button>
        )}
        {isSolutionAdmin && (
          <button onClick={() => navigate(`/domains/${domainId}/solutions/${solutionId}`)} className="text-lg font-semibold text-gray-800 hover:text-blue-600">SIF Unity Broker</button>
        )}
        {!isSolutionAdmin && (
          <button onClick={() => navigate('/tag-definitions')} className={`ml-4 text-sm ${location.pathname === '/tag-definitions' ? 'text-blue-600 font-medium' : 'text-gray-500 hover:text-gray-700'}`}>Tag Definitions</button>
        )}
        {breadcrumbs.length > 0 && (
          <div className="flex items-center gap-1 ml-4 text-sm text-gray-500">
            {breadcrumbs.map((crumb, i) => (
              <span key={crumb.path} className="flex items-center gap-1">
                {i > 0 && <span className="text-gray-300">›</span>}
                <button onClick={() => navigate(crumb.path)} className="hover:text-blue-600 hover:underline">{crumb.label}</button>
              </span>
            ))}
          </div>
        )}
      </div>
      <div className="flex items-center gap-3">
        {authMode === 'user' && username && (
          <span className="text-sm text-gray-500 mr-4">User: {username}</span>
        )}
        {authMode === 'environment' && (
          <span className="text-sm text-gray-500 mr-4">Environment: {selectedEnvironment?.applicationName || ''}</span>
        )}
        {authMode === 'user' && selectedEnvironment && (
          <>
            <span className="text-sm text-gray-600">
              Env: {selectedEnvironment.applicationName}
            </span>
            <button
              onClick={handleSwitchEnvironment}
              className="text-sm text-blue-600 hover:text-blue-800 border border-blue-300 rounded px-3 py-1 hover:bg-blue-50 transition-colors"
            >
              Switch Environment
            </button>
          </>
        )}
        <button
          onClick={handleLogout}
          className="text-sm text-gray-600 hover:text-gray-900 border border-gray-300 rounded px-3 py-1 hover:bg-gray-50 transition-colors"
        >
          Logout
        </button>
      </div>
    </nav>
  );
}
