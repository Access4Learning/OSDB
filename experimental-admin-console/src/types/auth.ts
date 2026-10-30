/**
 * Authentication types for the dual-mode admin console auth system.
 * Supports both "environment" (HMAC) and "user" (session token proxy) modes.
 */

/** Discriminator for the two authentication flows */
export type AuthMode = 'environment' | 'user';

/** Environment info returned from the login response */
export interface EnvironmentInfo {
  id: string;
  applicationName: string;
}

/** Core auth state tracked by AuthContext */
export interface AuthState {
  // Common
  authMode: AuthMode | null;
  isAuthenticated: boolean;

  // Environment mode (existing HMAC flow)
  sessionToken: string | null;
  sharedSecret: string | null;

  // User mode (session token proxy flow)
  adminSessionToken: string | null;
  expiresAt: string | null;
  environments: EnvironmentInfo[];
  selectedEnvironment: EnvironmentInfo | null;
  domainId: string | null;
  solutionId: string | null;
  username: string | null;
}

/** Full context type exposed to consuming components */
export interface AuthContextType extends AuthState {
  // Environment mode login (existing, unchanged)
  loginEnvironment: (sessionToken: string, sharedSecret: string, scopeDomain?: string, scopeSolution?: string) => void;

  // User mode login (new)
  loginUser: (
    username: string,
    adminSessionToken: string,
    expiresAt: string,
    environments: EnvironmentInfo[],
    domainId: string,
    solutionId: string
  ) => void;
  selectEnvironment: (env: EnvironmentInfo) => void;

  // Common
  logout: () => void;

  // Fetch helper — dispatches to correct mode
  apiFetch: (path: string, options?: RequestInit) => Promise<Response>;

  // Scope change detection
  checkScopeChange: (urlDomainId: string, urlSolutionId: string) => boolean;
  scopeChangeMessage: string | null;
  clearScopeChangeMessage: () => void;

  // Session expiry
  sessionExpiredMessage: string | null;
  sessionExpiredRedirect: string | null;
  clearSessionExpiredMessage: () => void;
}

/** Request body for POST /admin-users/login */
export interface LoginRequest {
  username: string;
  password: string;
  domainUuid: string;
  solutionUuid: string;
}

/** Response body from POST /admin-users/login (HTTP 200) */
export interface LoginResponse {
  sessionToken: string;
  expiresAt: string;
  environments: EnvironmentInfo[];
}

/** Request body for POST /admin-users/sessions/select-environment */
export interface SelectEnvironmentRequest {
  environmentId: string;
}
