const crypto = require('crypto');

/**
 * Generate a random UUID v4
 * @returns {string} UUID
 */
function generateUUID() {
  return crypto.randomUUID();
}

/**
 * Validate UUID format
 * @param {string} uuid - UUID to validate
 * @returns {boolean} True if valid UUID
 */
function isValidUUID(uuid) {
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  return uuidRegex.test(uuid);
}

/**
 * Generate ISO 8601 timestamp
 * @returns {string} ISO 8601 timestamp
 */
function generateTimestamp() {
  return new Date().toISOString();
}

/**
 * Generate test data name with prefix
 * @param {string} baseName - Base name for the resource
 * @param {Object} bru - Bruno utility object (optional)
 * @returns {string} Prefixed name with UUID
 */
function getTestDataName(baseName, bru) {
  const prefix = bru ? bru.getEnvVar('testDataPrefix') || 'bruno-test' : 'bruno-test';
  const uuid = generateUUID().split('-')[0]; // Use first segment of UUID for brevity
  return `${prefix}-${baseName}-${uuid}`;
}

/**
 * Async sleep for specified milliseconds
 * @param {number} ms - Milliseconds to sleep
 * @returns {Promise} Promise that resolves after sleep
 */
function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * Extract resource ID from Location header
 * @param {string} locationHeader - Location header value
 * @returns {string|null} Extracted ID or null
 */
function extractIdFromLocation(locationHeader) {
  if (!locationHeader) return null;
  
  // Extract last segment of URL path
  const parts = locationHeader.split('/');
  return parts[parts.length - 1];
}

/**
 * Generate random string
 * @param {number} length - Length of string
 * @returns {string} Random string
 */
function generateRandomString(length = 32) {
  return crypto.randomBytes(length).toString('hex').substring(0, length);
}

/**
 * Parse JSON safely
 * @param {string} jsonString - JSON string to parse
 * @returns {Object|null} Parsed object or null on error
 */
function safeJsonParse(jsonString) {
  try {
    return JSON.parse(jsonString);
  } catch (e) {
    console.error('JSON parse error:', e.message);
    return null;
  }
}

module.exports = {
  generateUUID,
  isValidUUID,
  generateTimestamp,
  getTestDataName,
  sleep,
  extractIdFromLocation,
  generateRandomString,
  safeJsonParse
};
