import CryptoJS from 'crypto-js';

const GATEWAY_URL = import.meta.env.VITE_GATEWAY_URL || 'http://localhost:8080';

console.log('[OSDB] Gateway URL:', GATEWAY_URL);

export function generateAuthHeaders(
  sessionToken: string,
  sharedSecret: string
): { Authorization: string; timestamp: string } {
  const timestamp = new Date().toISOString();
  const message = sessionToken + ':' + timestamp;
  const hash = CryptoJS.HmacSHA256(message, sharedSecret);
  const hashBase64 = CryptoJS.enc.Base64.stringify(hash);
  const tokenPart = CryptoJS.enc.Base64.stringify(CryptoJS.enc.Utf8.parse(sessionToken + ':' + hashBase64));
  const authorization = `SIF_HMACSHA256 ${tokenPart}`;
  return { Authorization: authorization, timestamp };
}

const ENVIRONMENT_SERVICE_URL = import.meta.env.VITE_GATEWAY_URL || 'http://localhost:8080';

export async function proxyFetch(
  path: string,
  adminSessionToken: string,
  options: RequestInit = {}
): Promise<Response> {
  const headers = new Headers(options.headers);
  headers.set('X-Admin-Session-Token', adminSessionToken);
  headers.set('Content-Type', 'application/json');

  const url = `${ENVIRONMENT_SERVICE_URL}/admin-proxy${path}`;
  return fetch(url, { ...options, headers });
}

export async function authenticatedFetch(
  path: string,
  sessionToken: string,
  sharedSecret: string,
  options: RequestInit = {},
  extraHeaders?: Record<string, string>
): Promise<Response> {
  const { Authorization, timestamp } = generateAuthHeaders(sessionToken, sharedSecret);

  const headers = new Headers(options.headers);
  headers.set('Authorization', Authorization);
  headers.set('timestamp', timestamp);
  headers.set('Content-Type', 'application/json');

  if (extraHeaders) {
    for (const [key, value] of Object.entries(extraHeaders)) {
      headers.set(key, value);
    }
  }

  const url = `${GATEWAY_URL}${path}`;
  return fetch(url, { ...options, headers });
}
