import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';
import type { AuthMode, EnvironmentInfo, AuthContextType } from '../types/auth';
import { authenticatedFetch, proxyFetch } from '../utils/auth';

// sessionStorage keys
const STORAGE_KEYS = {
  authMode: 'authMode',
  sessionToken: 'sessionToken',
  sharedSecret: 'sharedSecret',
  adminSessionToken: 'adminSessionToken',
  adminExpiresAt: 'adminExpiresAt',
  adminEnvironments: 'adminEnvironments',
  adminSelectedEnvironment: 'adminSelectedEnvironment',
  adminDomainId: 'adminDomainId',
  adminSolutionId: 'adminSolutionId',
} as const;

function clearAllStorage(): void {
  Object.values(STORAGE_KEYS).forEach((key) => sessionStorage.removeItem(key));
}

function loadEnvironments(): EnvironmentInfo[] {
  const raw = sessionStorage.getItem(STORAGE_KEYS.adminEnvironments);
  if (!raw) return [];
  try {
    return JSON.parse(raw) as EnvironmentInfo[];
  } catch {
    return [];
  }
}

function loadSelectedEnvironment(): EnvironmentInfo | null {
  const raw = sessionStorage.getItem(STORAGE_KEYS.adminSelectedEnvironment);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as EnvironmentInfo;
  } catch {
    return null;
  }
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  // Common
  const [authMode, setAuthMode] = useState<AuthMode | null>(
    () => (sessionStorage.getItem(STORAGE_KEYS.authMode) as AuthMode | null)
  );

  // Environment mode
  const [sessionToken, setSessionToken] = useState<string | null>(
    () => sessionStorage.getItem(STORAGE_KEYS.sessionToken)
  );
  const [sharedSecret, setSharedSecret] = useState<string | null>(
    () => sessionStorage.getItem(STORAGE_KEYS.sharedSecret)
  );

  // User mode
  const [adminSessionToken, setAdminSessionToken] = useState<string | null>(
    () => sessionStorage.getItem(STORAGE_KEYS.adminSessionToken)
  );
  const [expiresAt, setExpiresAt] = useState<string | null>(
    () => sessionStorage.getItem(STORAGE_KEYS.adminExpiresAt)
  );
  const [environments, setEnvironments] = useState<EnvironmentInfo[]>(loadEnvironments);
  const [selectedEnvironment, setSelectedEnvironment] = useState<EnvironmentInfo | null>(loadSelectedEnvironment);
  const [domainId, setDomainId] = useState<string | null>(
    () => sessionStorage.getItem(STORAGE_KEYS.adminDomainId)
  );
  const [solutionId, setSolutionId] = useState<string | null>(
    () => sessionStorage.getItem(STORAGE_KEYS.adminSolutionId)
  );
  const [username, setUsername] = useState<string | null>(
    () => sessionStorage.getItem('admin_username')
  );

  const isAuthenticated = authMode !== null;

  // Persist state to sessionStorage on changes
  useEffect(() => {
    if (authMode) {
      sessionStorage.setItem(STORAGE_KEYS.authMode, authMode);
    } else {
      sessionStorage.removeItem(STORAGE_KEYS.authMode);
    }
  }, [authMode]);

  useEffect(() => {
    if (sessionToken) {
      sessionStorage.setItem(STORAGE_KEYS.sessionToken, sessionToken);
    } else {
      sessionStorage.removeItem(STORAGE_KEYS.sessionToken);
    }
  }, [sessionToken]);

  useEffect(() => {
    if (sharedSecret) {
      sessionStorage.setItem(STORAGE_KEYS.sharedSecret, sharedSecret);
    } else {
      sessionStorage.removeItem(STORAGE_KEYS.sharedSecret);
    }
  }, [sharedSecret]);

  useEffect(() => {
    if (adminSessionToken) {
      sessionStorage.setItem(STORAGE_KEYS.adminSessionToken, adminSessionToken);
    } else {
      sessionStorage.removeItem(STORAGE_KEYS.adminSessionToken);
    }
  }, [adminSessionToken]);

  useEffect(() => {
    if (expiresAt) {
      sessionStorage.setItem(STORAGE_KEYS.adminExpiresAt, expiresAt);
    } else {
      sessionStorage.removeItem(STORAGE_KEYS.adminExpiresAt);
    }
  }, [expiresAt]);

  useEffect(() => {
    if (environments.length > 0) {
      sessionStorage.setItem(STORAGE_KEYS.adminEnvironments, JSON.stringify(environments));
    } else {
      sessionStorage.removeItem(STORAGE_KEYS.adminEnvironments);
    }
  }, [environments]);

  useEffect(() => {
    if (selectedEnvironment) {
      sessionStorage.setItem(STORAGE_KEYS.adminSelectedEnvironment, JSON.stringify(selectedEnvironment));
    } else {
      sessionStorage.removeItem(STORAGE_KEYS.adminSelectedEnvironment);
    }
  }, [selectedEnvironment]);

  useEffect(() => {
    if (domainId) {
      sessionStorage.setItem(STORAGE_KEYS.adminDomainId, domainId);
    } else {
      sessionStorage.removeItem(STORAGE_KEYS.adminDomainId);
    }
  }, [domainId]);

  useEffect(() => {
    if (solutionId) {
      sessionStorage.setItem(STORAGE_KEYS.adminSolutionId, solutionId);
    } else {
      sessionStorage.removeItem(STORAGE_KEYS.adminSolutionId);
    }
  }, [solutionId]);

  useEffect(() => {
    if (username) {
      sessionStorage.setItem('admin_username', username);
    } else {
      sessionStorage.removeItem('admin_username');
    }
  }, [username]);

  // Environment mode login (preserves existing behavior)
  const loginEnvironment = useCallback((token: string, secret: string, scopeDomain?: string, scopeSolution?: string) => {
    setAuthMode('environment');
    setSessionToken(token);
    setSharedSecret(secret);
    // Clear user mode state
    setAdminSessionToken(null);
    setExpiresAt(null);
    setEnvironments([]);
    setSelectedEnvironment(null);
    // Set solution admin scope if provided
    setDomainId(scopeDomain || null);
    setSolutionId(scopeSolution || null);
  }, []);

  // User mode login
  const loginUser = useCallback((
    usernameParam: string,
    token: string,
    expires: string,
    envs: EnvironmentInfo[],
    domain: string,
    solution: string
  ) => {
    setAuthMode('user');
    setUsername(usernameParam);
    setAdminSessionToken(token);
    setExpiresAt(expires);
    setEnvironments(envs);
    setDomainId(domain);
    setSolutionId(solution);
    // Clear environment mode state
    setSessionToken(null);
    setSharedSecret(null);
    setSelectedEnvironment(null);
  }, []);

  // Select environment (user mode)
  const selectEnvironment = useCallback((env: EnvironmentInfo) => {
    setSelectedEnvironment(env);
  }, []);

  // Logout clears all state for both modes
  const logout = useCallback(() => {
    setAuthMode(null);
    setSessionToken(null);
    setSharedSecret(null);
    setAdminSessionToken(null);
    setExpiresAt(null);
    setEnvironments([]);
    setSelectedEnvironment(null);
    setDomainId(null);
    setSolutionId(null);
    setUsername(null);
    sessionStorage.removeItem('admin_username');
    clearAllStorage();
  }, []);

  // Scope change message
  const [scopeChangeMessage, setScopeChangeMessage] = useState<string | null>(null);

  // Session expiry message
  const [sessionExpiredMessage, setSessionExpiredMessage] = useState<string | null>(null);
  const [sessionExpiredRedirect, setSessionExpiredRedirect] = useState<string | null>(null);

  // Check if URL scope differs from stored session scope; invalidate if so
  const checkScopeChange = useCallback((urlDomainId: string, urlSolutionId: string): boolean => {
    if (authMode !== 'user' || !domainId || !solutionId) {
      return false;
    }
    if (urlDomainId !== domainId || urlSolutionId !== solutionId) {
      // Invalidate session
      setAuthMode(null);
      setSessionToken(null);
      setSharedSecret(null);
      setAdminSessionToken(null);
      setExpiresAt(null);
      setEnvironments([]);
      setSelectedEnvironment(null);
      setDomainId(null);
      setSolutionId(null);
      clearAllStorage();
      setScopeChangeMessage('The domain or solution scope has changed. Please log in again for the new scope.');
      return true;
    }
    return false;
  }, [authMode, domainId, solutionId]);

  const clearScopeChangeMessage = useCallback(() => {
    setScopeChangeMessage(null);
  }, []);

  const clearSessionExpiredMessage = useCallback(() => {
    setSessionExpiredMessage(null);
    setSessionExpiredRedirect(null);
  }, []);

  // Fetch helper — dispatches to correct mode, intercepts session expiry
  const apiFetch = useCallback(async (path: string, options?: RequestInit): Promise<Response> => {
    if (authMode === 'environment' && sessionToken && sharedSecret) {
      return authenticatedFetch(path, sessionToken, sharedSecret, options);
    }
    if (authMode === 'user' && adminSessionToken) {
      const response = await proxyFetch(path, adminSessionToken, options);

      if (response.status === 401) {
        // Capture redirect path before clearing state
        const redirectPath = domainId && solutionId
          ? `/${domainId}/${solutionId}/login`
          : '/login';
        // Session expired — clear all state and signal expiry
        setAuthMode(null);
        setSessionToken(null);
        setSharedSecret(null);
        setAdminSessionToken(null);
        setExpiresAt(null);
        setEnvironments([]);
        setSelectedEnvironment(null);
        setDomainId(null);
        setSolutionId(null);
        clearAllStorage();
        setSessionExpiredMessage('Your session has expired. Please log in again.');
        setSessionExpiredRedirect(redirectPath);
        return response;
      }

      if (response.status === 502) {
        // Capture redirect path before clearing state
        const redirectPath = domainId && solutionId
          ? `/${domainId}/${solutionId}/login`
          : '/login';
        // Environment credentials invalid — clear all state
        setAuthMode(null);
        setSessionToken(null);
        setSharedSecret(null);
        setAdminSessionToken(null);
        setExpiresAt(null);
        setEnvironments([]);
        setSelectedEnvironment(null);
        setDomainId(null);
        setSolutionId(null);
        clearAllStorage();
        setSessionExpiredMessage('Environment credentials are invalid. Please log in again.');
        setSessionExpiredRedirect(redirectPath);
        return response;
      }

      // Non-401/502 errors: return response as-is for component to handle
      return response;
    }
    throw new Error('Not authenticated');
  }, [authMode, sessionToken, sharedSecret, adminSessionToken, domainId, solutionId]);

  const value: AuthContextType = {
    authMode,
    isAuthenticated,
    sessionToken,
    sharedSecret,
    adminSessionToken,
    expiresAt,
    environments,
    selectedEnvironment,
    domainId,
    solutionId,
    username,
    loginEnvironment,
    loginUser,
    selectEnvironment,
    logout,
    apiFetch,
    checkScopeChange,
    scopeChangeMessage,
    clearScopeChangeMessage,
    sessionExpiredMessage,
    sessionExpiredRedirect,
    clearSessionExpiredMessage,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
