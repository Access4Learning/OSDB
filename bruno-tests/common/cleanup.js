const auth = require('./auth.js');

/**
 * Delete a single resource
 * @param {string} baseUrl - Base URL for the resource type
 * @param {string} resourceId - ID of resource to delete
 * @param {Object} req - Bruno request object
 * @param {Object} bru - Bruno utility object
 * @returns {Promise<void>}
 * @throws {Error} On any error
 */
async function deleteResource(baseUrl, resourceId, req, bru) {
  const url = `${baseUrl}/${resourceId}`;
  
  // Create a temporary request object for deletion
  const deleteReq = {
    getMethod: () => 'DELETE',
    getUrl: () => url,
    getBody: () => '',
    setHeader: (name, value) => {
      req.setHeader(name, value);
    }
  };
  
  // Generate auth headers
  auth.setAuthHeaders(deleteReq, bru);
  
  // Make DELETE request
  const response = await fetch(url, {
    method: 'DELETE',
    headers: {
      'Authorization': req.getHeader('Authorization'),
      'timestamp': req.getHeader('timestamp')
    }
  });
  
  if (!response.ok && response.status !== 404) {
    throw new Error(`Failed to delete resource ${resourceId}: ${response.status} ${response.statusText}`);
  }
}

/**
 * Delete a resource safely (ignore 404 errors)
 * @param {string} baseUrl - Base URL for the resource type
 * @param {string} resourceId - ID of resource to delete
 * @param {Object} req - Bruno request object
 * @param {Object} bru - Bruno utility object
 * @returns {Promise<boolean>} True if deleted, false if not found
 */
async function deleteResourceSafe(baseUrl, resourceId, req, bru) {
  try {
    await deleteResource(baseUrl, resourceId, req, bru);
    return true;
  } catch (error) {
    if (error.message.includes('404')) {
      console.log(`Resource ${resourceId} not found (already deleted)`);
      return false;
    }
    throw error;
  }
}

/**
 * Cleanup multiple test resources
 * @param {string} resourceType - Type of resource (environments, queues, etc.)
 * @param {Array<string>} ids - Array of resource IDs to delete
 * @param {Object} req - Bruno request object
 * @param {Object} bru - Bruno utility object
 * @returns {Promise<Object>} Cleanup results
 */
async function cleanupTestResources(resourceType, ids, req, bru) {
  const gatewayUrl = bru.getEnvVar('gatewayUrl');
  const baseUrl = `${gatewayUrl}/${resourceType}`;
  
  const results = {
    total: ids.length,
    deleted: 0,
    notFound: 0,
    failed: 0,
    errors: []
  };
  
  for (const id of ids) {
    try {
      const deleted = await deleteResourceSafe(baseUrl, id, req, bru);
      if (deleted) {
        results.deleted++;
      } else {
        results.notFound++;
      }
    } catch (error) {
      results.failed++;
      results.errors.push({
        id,
        error: error.message
      });
      console.error(`Failed to delete ${resourceType} ${id}:`, error.message);
    }
  }
  
  console.log(`Cleanup ${resourceType}: ${results.deleted} deleted, ${results.notFound} not found, ${results.failed} failed`);
  
  return results;
}

/**
 * Get test resources by prefix
 * @param {string} resourceType - Type of resource
 * @param {Object} req - Bruno request object
 * @param {Object} bru - Bruno utility object
 * @returns {Promise<Array>} Array of test resources
 */
async function getTestResources(resourceType, req, bru) {
  const gatewayUrl = bru.getEnvVar('gatewayUrl');
  const testPrefix = bru.getEnvVar('testDataPrefix') || 'bruno-test';
  const url = `${gatewayUrl}/${resourceType}`;
  
  // Create a temporary request object
  const getReq = {
    getMethod: () => 'GET',
    getUrl: () => url,
    getBody: () => '',
    setHeader: (name, value) => {
      req.setHeader(name, value);
    }
  };
  
  // Generate auth headers
  auth.setAuthHeaders(getReq, bru);
  
  // Make GET request
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      'Authorization': req.getHeader('Authorization'),
      'timestamp': req.getHeader('timestamp')
    }
  });
  
  if (!response.ok) {
    throw new Error(`Failed to get ${resourceType}: ${response.status}`);
  }
  
  const data = await response.json();
  
  // Filter resources by test prefix
  return data.filter(resource => {
    const name = resource.name || resource.applicationName || '';
    return name.startsWith(testPrefix);
  });
}

module.exports = {
  deleteResource,
  deleteResourceSafe,
  cleanupTestResources,
  getTestResources
};
