import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as fc from 'fast-check';

/**
 * Property 5: Proxy URL construction
 * For any valid API path string `p`, proxyFetch(p, token) SHALL construct
 * the request URL as VITE_ENVIRONMENT_SERVICE_URL + "/admin-proxy" + p
 * without double-slashing or path modification.
 *
 * **Validates: Requirements 4.2**
 */

// Valid URL path character arbitrary: letters, digits, and common path chars
const pathCharArb = fc.oneof(
  fc.constantFrom(...'abcdefghijklmnopqrstuvwxyz'.split('')),
  fc.constantFrom(...'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('')),
  fc.constantFrom(...'0123456789'.split('')),
  fc.constantFrom(...'-._~/'.split(''))
);

const validPathArb = fc
  .array(pathCharArb, { minLength: 1, maxLength: 50 })
  .map((chars) => chars.join(''))
  .map((s) => (s.startsWith('/') ? s : '/' + s))
  // Filter out double slashes at the start (path should not start with //)
  .filter((s) => !s.startsWith('//'));

describe('Feature: admin-console-auth, Property 5: Proxy URL construction', () => {
  let mockFetch: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    mockFetch = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }));
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.resetModules();
  });

  it('constructs URL as baseUrl + "/admin-proxy" + path without modification', async () => {
    const { proxyFetch } = await import('../utils/auth');

    await fc.assert(
      fc.asyncProperty(validPathArb, async (path) => {
        mockFetch.mockClear();

        await proxyFetch(path, 'test-token-123');

        expect(mockFetch).toHaveBeenCalledTimes(1);
        const calledUrl = mockFetch.mock.calls[0][0];

        // URL must be exactly baseUrl + "/admin-proxy" + path
        const expectedUrl = `http://localhost:8081/admin-proxy${path}`;
        expect(calledUrl).toBe(expectedUrl);
      }),
      { numRuns: 100 }
    );
  });


});

/**
 * Property 6: Proxy request preserves method and body
 * Validates: Requirements 4.5
 *
 * For any HTTP method in {GET, POST, PUT, DELETE} and any request body
 * (including empty), proxyFetch forwards the same method and body
 * without modification.
 */
describe('Feature: admin-console-auth, Property 6: Proxy request preserves method and body', () => {
  let mockFetch: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    mockFetch = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }));
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.resetModules();
  });

  it('should forward HTTP method and body without modification for any method and body', async () => {
    const { proxyFetch } = await import('../utils/auth');

    await fc.assert(
      fc.asyncProperty(
        fc.oneof(
          fc.constant('GET'),
          fc.constant('POST'),
          fc.constant('PUT'),
          fc.constant('DELETE')
        ),
        fc.option(fc.json(), { nil: undefined }),
        async (method, body) => {
          mockFetch.mockClear();

          const options: RequestInit = { method };
          if (body !== undefined) {
            options.body = body;
          }

          await proxyFetch('/test/path', 'test-token', options);

          expect(mockFetch).toHaveBeenCalledTimes(1);
          const [, fetchOptions] = mockFetch.mock.calls[0];

          // Method preserved without modification
          expect(fetchOptions.method).toBe(method);

          // Body preserved without modification
          if (body !== undefined) {
            expect(fetchOptions.body).toBe(body);
          } else {
            expect(fetchOptions.body).toBeUndefined();
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should preserve empty string body without modification', async () => {
    const { proxyFetch } = await import('../utils/auth');

    await fc.assert(
      fc.asyncProperty(
        fc.oneof(
          fc.constant('POST'),
          fc.constant('PUT')
        ),
        async (method) => {
          mockFetch.mockClear();

          await proxyFetch('/some/path', 'token-123', { method, body: '' });

          expect(mockFetch).toHaveBeenCalledTimes(1);
          const [, fetchOptions] = mockFetch.mock.calls[0];

          expect(fetchOptions.method).toBe(method);
          expect(fetchOptions.body).toBe('');
        }
      ),
      { numRuns: 100 }
    );
  });
});
