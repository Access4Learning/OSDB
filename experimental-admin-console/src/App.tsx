import { Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { useEffect } from 'react';
import LoginPage from './pages/LoginPage';
import DomainsPage from './pages/DomainsPage';
import SolutionsPage from './pages/SolutionsPage';
import SolutionOverviewPage from './pages/SolutionOverviewPage';
import EnvironmentOverviewPage from './pages/EnvironmentOverviewPage';
import ZoneDetailPage from './pages/ZoneDetailPage';
import MyEnvironmentPage from './pages/MyEnvironmentPage';
import TagDefinitionsPage from './pages/TagDefinitionsPage';
import UserLoginPage from './pages/UserLoginPage';
import EnvironmentSelectionPage from './pages/EnvironmentSelectionPage';
import InvalidScopePage from './pages/InvalidScopePage';
import UserDashboardPage from './pages/UserDashboardPage';
import { useAuth } from './context/AuthContext';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

/** Blocks solution admins from accessing global-only pages */
function GlobalAdminRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, authMode, domainId, solutionId } = useAuth();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  // Solution admin — redirect to their solution
  if (authMode === 'environment' && domainId && solutionId) {
    return <Navigate to={`/domains/${domainId}/solutions/${solutionId}`} replace />;
  }
  return <>{children}</>;
}

/** Watches for session expiry and redirects to the appropriate login page */
function SessionExpiredRedirect() {
  const { sessionExpiredRedirect, sessionExpiredMessage } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (sessionExpiredRedirect && sessionExpiredMessage) {
      navigate(sessionExpiredRedirect, { replace: true });
    }
  }, [sessionExpiredRedirect, sessionExpiredMessage, navigate]);

  return null;
}

function App() {
  return (
    <>
      <SessionExpiredRedirect />
      <Routes>
      {/* Existing environment login */}
      <Route path="/login" element={<LoginPage />} />

      {/* User login (public — no ProtectedRoute) */}
      <Route path="/:domainId/:solutionId/login" element={<UserLoginPage />} />

      {/* Environment selection (requires auth) */}
      <Route
        path="/:domainId/:solutionId/select-environment"
        element={
          <ProtectedRoute>
            <EnvironmentSelectionPage />
          </ProtectedRoute>
        }
      />

      {/* User-mode dashboard (requires auth) */}
      <Route
        path="/:domainId/:solutionId/dashboard/*"
        element={
          <ProtectedRoute>
            <UserDashboardPage />
          </ProtectedRoute>
        }
      />

      {/* Invalid scope error page */}
      <Route path="/invalid-scope" element={<InvalidScopePage />} />

      {/* Existing protected routes */}
      <Route
        path="/my-environment"
        element={
          <ProtectedRoute>
            <MyEnvironmentPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/domains"
        element={
          <GlobalAdminRoute>
            <DomainsPage />
          </GlobalAdminRoute>
        }
      />
      <Route
        path="/tag-definitions"
        element={
          <GlobalAdminRoute>
            <TagDefinitionsPage />
          </GlobalAdminRoute>
        }
      />
      <Route
        path="/domains/:domainId"
        element={
          <GlobalAdminRoute>
            <SolutionsPage />
          </GlobalAdminRoute>
        }
      />
      <Route
        path="/domains/:domainId/solutions/:solutionId"
        element={
          <ProtectedRoute>
            <SolutionOverviewPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/domains/:domainId/solutions/:solutionId/zones/:zoneId"
        element={
          <ProtectedRoute>
            <ZoneDetailPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/domains/:domainId/solutions/:solutionId/environments/:envId"
        element={
          <ProtectedRoute>
            <EnvironmentOverviewPage />
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </>
  );
}

export default App;
