const CryptoJS = require('crypto-js');

/**
 * Generate HMAC-SHA256 authentication and set header variables.
 *
 * Supports three calling styles:
 *
 * 1. Role-based (.env lookup):
 *    auth.setAuthHeaders(req, bru, 'admin')
 *    → reads {currentEnv}-adminSessionToken / SharedSecret from .env
 *
 * 2. Variable prefix (Bruno vars from a previous test):
 *    auth.setAuthHeaders(req, bru, { varPrefix: 'created' })
 *    → reads bru.getVar('createdSessionToken') / bru.getVar('createdSharedSecret')
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
      // Direct credentials
      sessionToken = roleOrOptions.sessionToken;
      sharedSecret = roleOrOptions.sharedSecret;
      source = 'direct';
    } else if (roleOrOptions.varPrefix) {
      // Variable prefix — read from Bruno vars
      const prefix = roleOrOptions.varPrefix;
      sessionToken = bru.getVar(prefix + 'SessionToken');
      sharedSecret = bru.getVar(prefix + 'SharedSecret');
      source = `var:${prefix}`;

      if (!sessionToken || !sharedSecret) {
        throw new Error(
          `Missing credentials from Bruno vars: ${prefix}SessionToken / ${prefix}SharedSecret. ` +
          `Make sure a previous test stored them with bru.setVar().`
        );
      }
    } else {
      throw new Error(
        'Options object must have either { sessionToken, sharedSecret } or { varPrefix }.'
      );
    }
  } else {
    // Role string — .env lookup
    const role = roleOrOptions;
    const envPrefix = bru.getEnvVar('currentEnv');

    if (!envPrefix) {
      throw new Error('currentEnv not set in environment configuration.');
    }

    const sessionTokenPath = `{{process.env.${envPrefix}-${role}SessionToken}}`;
    const sharedSecretPath = `{{process.env.${envPrefix}-${role}SharedSecret}}`;

    sessionToken = bru.interpolate(sessionTokenPath);
    sharedSecret = bru.interpolate(sharedSecretPath);
    source = `env:${envPrefix}-${role}`;

    if (!sessionToken || !sharedSecret ||
        sessionToken === sessionTokenPath || sharedSecret === sharedSecretPath) {
      throw new Error(
        `Missing credentials for environment: ${envPrefix}, role: ${role}. ` +
        `Check your .env file for ${envPrefix}-${role}SessionToken and ${envPrefix}-${role}SharedSecret`
      );
    }
  }

  // Generate ISO 8601 timestamp
  const timestamp = new Date().toISOString();

  // Compute HMAC: sessionToken:timestamp
  const valToHash = sessionToken + ':' + timestamp;
  const hash = CryptoJS.HmacSHA256(valToHash, sharedSecret).toString(CryptoJS.enc.Base64);

  // Build header: SIF_HMACSHA256 Base64(sessionToken:hash)
  const authToken = 'SIF_HMACSHA256 ' + CryptoJS.enc.Base64.stringify(
    CryptoJS.enc.Utf8.parse(sessionToken + ':' + hash)
  );

  // Set variables for use in headers section
  bru.setVar('authorizationHeader', authToken);
  bru.setVar('timestampHeader', timestamp);

  // Debug logging
  if (bru.getEnvVar('verboseLogging') === 'true') {
    console.log('Auth Debug:');
    console.log('  Source:', source);
    console.log('  Session Token:', sessionToken);
    console.log('  Timestamp:', timestamp);
    console.log('  Hash:', hash);
  }
}

module.exports = { setAuthHeaders };
