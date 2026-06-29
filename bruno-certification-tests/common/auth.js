const CryptoJS = require('crypto-js');

/**
 * Generate HMAC-SHA256 authentication headers for SIF 3.7 broker requests.
 *
 * Supports three calling styles:
 *
 * 1. Role-based (.env lookup):
 *    auth.setAuthHeaders(req, bru, 'admin')
 *    → reads {currentEnv}-adminSessionToken / {currentEnv}-adminSharedSecret from .env
 *
 * 2. Variable prefix (Bruno vars from a previous test):
 *    auth.setAuthHeaders(req, bru, { varPrefix: 'sec01Admin' })
 *    → reads bru.getVar('sec01AdminSessionToken') / bru.getVar('sec01AdminSharedSecret')
 *
 * 3. Direct credentials:
 *    auth.setAuthHeaders(req, bru, { sessionToken: '...', sharedSecret: '...' })
 *
 * @param {Object} req - Bruno request object
 * @param {Object} bru - Bruno utility object
 * @param {string|Object} roleOrOptions - Role string OR options object
 */
function setAuthHeaders(req, bru, roleOrOptions = 'consumer') {
  let sessionToken, sharedSecret, source;

  if (typeof roleOrOptions === 'object' && roleOrOptions !== null) {
    // Options object
    if (roleOrOptions.sessionToken && roleOrOptions.sharedSecret) {
      // Style 3: Direct credentials
      sessionToken = roleOrOptions.sessionToken;
      sharedSecret = roleOrOptions.sharedSecret;
      source = 'direct';
    } else if (roleOrOptions.varPrefix) {
      // Style 2: Variable prefix — read from Bruno collection variables
      const prefix = roleOrOptions.varPrefix;
      sessionToken = bru.getVar(prefix + 'SessionToken');
      sharedSecret = bru.getVar(prefix + 'SharedSecret');
      source = `var:${prefix}`;

      if (!sessionToken) {
        throw new Error(
          `Missing Bruno variable "${prefix}SessionToken". ` +
          `Expected source: bru.getVar('${prefix}SessionToken'). ` +
          `Make sure a previous step (e.g., 03-get-admin-environment) stored it with bru.setVar('${prefix}SessionToken', value).`
        );
      }
      if (!sharedSecret) {
        throw new Error(
          `Missing Bruno variable "${prefix}SharedSecret". ` +
          `Expected source: bru.getVar('${prefix}SharedSecret'). ` +
          `Make sure a previous step (e.g., 03-get-admin-environment) stored it with bru.setVar('${prefix}SharedSecret', value).`
        );
      }
    } else {
      throw new Error(
        'Invalid options object passed to setAuthHeaders. ' +
        'Expected either { sessionToken, sharedSecret } for direct credentials ' +
        'or { varPrefix } for Bruno variable lookup. ' +
        'Received keys: ' + Object.keys(roleOrOptions).join(', ')
      );
    }
  } else {
    // Style 1: Role string — .env lookup
    const role = roleOrOptions;
    const envPrefix = bru.getEnvVar('currentEnv');

    if (!envPrefix) {
      throw new Error(
        'Missing environment variable "currentEnv" in Bruno environment configuration. ' +
        'Expected source: environments/local.bru (or active environment file). ' +
        'Set currentEnv to the credential prefix used in your .env file (e.g., "cert").'
      );
    }

    const sessionTokenKey = `${envPrefix}-${role}SessionToken`;
    const sharedSecretKey = `${envPrefix}-${role}SharedSecret`;
    const sessionTokenPath = `{{process.env.${sessionTokenKey}}}`;
    const sharedSecretPath = `{{process.env.${sharedSecretKey}}}`;

    sessionToken = bru.interpolate(sessionTokenPath);
    sharedSecret = bru.interpolate(sharedSecretPath);
    source = `env:${envPrefix}-${role}`;

    if (!sessionToken || sessionToken === sessionTokenPath) {
      throw new Error(
        `Missing .env variable "${sessionTokenKey}". ` +
        `Expected source: .env file in bruno-certification-tests/ root. ` +
        `Add the line: ${sessionTokenKey}=<your-session-token>`
      );
    }
    if (!sharedSecret || sharedSecret === sharedSecretPath) {
      throw new Error(
        `Missing .env variable "${sharedSecretKey}". ` +
        `Expected source: .env file in bruno-certification-tests/ root. ` +
        `Add the line: ${sharedSecretKey}=<your-shared-secret>`
      );
    }
  }

  // Generate ISO 8601 timestamp
  const timestamp = new Date().toISOString();

  // Compute HMAC-SHA256: hash(sessionToken:timestamp, sharedSecret)
  const valToHash = sessionToken + ':' + timestamp;
  const hash = CryptoJS.HmacSHA256(valToHash, sharedSecret).toString(CryptoJS.enc.Base64);

  // Build SIF authorization header: SIF_HMACSHA256 Base64(sessionToken:hash)
  const authToken = 'SIF_HMACSHA256 ' + CryptoJS.enc.Base64.stringify(
    CryptoJS.enc.Utf8.parse(sessionToken + ':' + hash)
  );

  // Set variables for use in request headers
  bru.setVar('authorizationHeader', authToken);
  bru.setVar('timestampHeader', timestamp);

  // Debug logging (enable via verboseLogging=true in environment)
  if (bru.getEnvVar('verboseLogging') === 'true') {
    console.log('Auth Debug:');
    console.log('  Source:', source);
    console.log('  Session Token:', sessionToken.substring(0, 8) + '...');
    console.log('  Timestamp:', timestamp);
    console.log('  Hash:', hash);
  }
}

module.exports = { setAuthHeaders };
