/**
 * Mock SIF Provider — echoes requests for Bruno certification tests.
 *
 * Listens on port 9090 (matching bruno-certification-tests/environments/local.bru).
 * Responds to any method on any path under /sif/provider/ with a JSON echo of:
 *   - receivedHeaders: all request headers (lowercased keys)
 *   - receivedQuery: query string parameters
 *   - receivedBody: parsed request body (if JSON)
 *   - method: HTTP method
 *   - path: request path
 *   - timestamp: ISO timestamp of when the request was received
 *
 * Usage:
 *   cd test-tools/mock-provider
 *   npm install
 *   npm start
 */

const express = require('express');
const app = express();
const PORT = process.env.PORT || 9091;

// Parse JSON and text bodies
app.use(express.json({ limit: '10mb' }));
app.use(express.text({ type: '*/*', limit: '10mb' }));

// Echo handler for all methods and paths
app.all('*', (req, res) => {
  const echo = {
    receivedHeaders: req.headers,
    receivedQuery: req.query,
    receivedBody: typeof req.body === 'object' ? req.body : null,
    method: req.method,
    path: req.path,
    url: req.originalUrl,
    timestamp: new Date().toISOString(),
  };

  // Log concisely
  console.log(`${req.method} ${req.originalUrl} → 200 (echo)`);

  res.status(200).json(echo);
});

app.listen(PORT, () => {
  console.log(`Mock SIF Provider running on http://localhost:${PORT}`);
  console.log('Echoes all requests as JSON. Ctrl+C to stop.');
});
